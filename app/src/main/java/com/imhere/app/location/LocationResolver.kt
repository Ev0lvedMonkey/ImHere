package com.imhere.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves a usable location fix for the SOS pipeline.
 *
 * Rules (from the specification):
 *  - a fresh fix is preferred;
 *  - a last known fix is allowed only if it is at most 30 seconds old;
 *  - accuracy must be known and at most 100 meters;
 *  - the total wait for a new fix is limited to 20 seconds.
 *
 * Prototype note: [acceptInexact] disables only the accuracy check and is used for
 * indoor verification of the widget pipeline. It is never enabled in production flows.
 */
class LocationResolver(private val context: Context) {

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean {
        val manager = locationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun isPlayServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    suspend fun resolve(acceptInexact: Boolean): LocationOutcome {
        if (!hasLocationPermission()) {
            Log.w(TAG, "resolve: location permission is missing")
            return LocationOutcome.Failure(LocationFailure.PermissionDenied)
        }
        if (!isLocationEnabled()) {
            Log.w(TAG, "resolve: location services are disabled")
            return LocationOutcome.Failure(LocationFailure.LocationDisabled)
        }

        val lastKnown = lastKnownFix()
        logLocation("lastKnown", lastKnown)

        val newFix = try {
            if (isPlayServicesAvailable()) {
                Log.i(TAG, "resolve: using FusedLocationProviderClient")
                requestFusedFix(acceptInexact)
            } else {
                Log.i(TAG, "resolve: Google Play services unavailable, using LocationManager")
                requestPlatformFix(acceptInexact)
            }
        } catch (securityException: SecurityException) {
            Log.e(TAG, "resolve: SecurityException while requesting location", securityException)
            null
        } catch (throwable: Throwable) {
            Log.e(TAG, "resolve: location request failed", throwable)
            null
        }
        logLocation("newFix", newFix)

        val candidates = listOfNotNull(newFix, lastKnown).filter { isValidNow(it, acceptInexact) }
        if (candidates.isNotEmpty()) {
            // Prefer the most accurate candidate; a new fix normally wins.
            val best = candidates.minBy { it.accuracy }
            val fix = toFix(best)
            Log.i(TAG, "resolve: accepted fix $fix")
            return LocationOutcome.Success(fix)
        }

        val bestAny = listOfNotNull(newFix, lastKnown)
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
        val failure = when {
            bestAny != null && bestAny.hasAccuracy() && bestAny.accuracy > MAX_ACCURACY_METERS &&
                !acceptInexact -> LocationFailure.LowAccuracy(bestAny.accuracy)

            bestAny != null && ageMillis(bestAny) > MAX_AGE_MILLIS -> LocationFailure.Stale
            bestAny == null -> LocationFailure.NoFix
            else -> LocationFailure.Timeout
        }
        Log.w(TAG, "resolve: rejected, reason=$failure, bestAny=$bestAny")
        return LocationOutcome.Failure(failure)
    }

    /**
     * [resolve] verifies the location permission before calling this method; every
     * protected call below is additionally wrapped by the caller's try/catch.
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestFusedFix(acceptInexact: Boolean): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cached = try {
            client.lastLocation.await()
        } catch (throwable: Throwable) {
            Log.w(TAG, "fused lastLocation failed", throwable)
            null
        }
        if (cached != null && isValidNow(cached, acceptInexact)) {
            Log.i(TAG, "fused: cached fix is still valid")
            return cached
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateAgeMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val bestRef = AtomicReference<Location?>(null)
        val fixes = callbackFlow {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    if (!location.hasAccuracy()) return
                    bestRef.updateAndGet { current ->
                        if (current == null || location.accuracy < current.accuracy) location else current
                    }
                    trySend(location)
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            awaitClose { client.removeLocationUpdates(callback) }
        }

        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            fixes.first { location ->
                location.hasAccuracy() &&
                    ageMillis(location) <= MAX_AGE_MILLIS &&
                    (acceptInexact || location.accuracy <= MAX_ACCURACY_METERS)
            }
        }
        return fresh ?: bestRef.get()
    }

    /** Permission is verified by [resolve] before any platform request. */
    @SuppressLint("MissingPermission")
    private suspend fun requestPlatformFix(acceptInexact: Boolean): Location? {
        val manager = locationManager ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            Log.w(TAG, "platform: no enabled providers")
            return null
        }

        val bestRef = AtomicReference<Location?>(null)
        val fixes = callbackFlow {
            val listener = LocationListener { location ->
                if (!location.hasAccuracy()) return@LocationListener
                bestRef.updateAndGet { current ->
                    if (current == null || location.accuracy < current.accuracy) location else current
                }
                trySend(location)
            }
            providers.forEach { provider ->
                try {
                    manager.requestLocationUpdates(
                        provider,
                        UPDATE_INTERVAL_MS,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                } catch (throwable: Throwable) {
                    Log.w(TAG, "platform: cannot request updates from $provider", throwable)
                }
            }
            awaitClose { manager.removeUpdates(listener) }
        }

        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            fixes.first { location ->
                location.hasAccuracy() &&
                    ageMillis(location) <= MAX_AGE_MILLIS &&
                    (acceptInexact || location.accuracy <= MAX_ACCURACY_METERS)
            }
        }
        return fresh ?: bestRef.get()
    }

    /** Permission is verified by [resolve] before the last known fix is read. */
    @SuppressLint("MissingPermission")
    private fun lastKnownFix(): Location? {
        val manager = locationManager ?: return null
        val providers = buildList {
            add(LocationManager.PASSIVE_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { it.hasAccuracy() }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    private fun isValidNow(location: Location, acceptInexact: Boolean): Boolean {
        if (!location.hasAccuracy()) return false
        if (!acceptInexact && location.accuracy > MAX_ACCURACY_METERS) return false
        val age = ageMillis(location)
        return age >= 0 && age <= MAX_AGE_MILLIS
    }

    private fun ageMillis(location: Location): Long {
        if (location.elapsedRealtimeNanos == 0L) return Long.MAX_VALUE
        return (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
    }

    private fun toFix(location: Location): LocationFix = LocationFix(
        latitude = location.latitude,
        longitude = location.longitude,
        accuracyMeters = location.accuracy,
        ageMillis = ageMillis(location),
        provider = location.provider ?: "unknown",
    )

    private fun logLocation(label: String, location: Location?) {
        if (location == null) {
            Log.i(TAG, "$label: none")
            return
        }
        Log.i(
            TAG,
            "$label: provider=${location.provider} accuracy=${location.accuracy} " +
                "ageMs=${ageMillis(location)} lat=${location.latitude} lon=${location.longitude}",
        )
    }

    companion object {
        private const val TAG = "ImHerePrototype"
        const val TIMEOUT_MS = 20_000L
        const val MAX_AGE_MILLIS = 30_000L
        const val MAX_ACCURACY_METERS = 100f
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
