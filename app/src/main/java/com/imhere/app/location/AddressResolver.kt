package com.imhere.app.location

import android.content.Context
import android.location.Geocoder
import android.util.Log
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional reverse geocoding (specification 8.4).
 *
 * The address is a convenience only: when the geocoder is missing, offline or
 * too slow, the caller gets `null` and continues without it.
 *
 * `Geocoder.getFromLocation` is a blocking call that cannot be cancelled. It is
 * therefore executed on a dedicated daemon thread and awaited with a hard
 * wall-clock timeout, so a stalled geocoder can never delay the SMS.
 */
class AddressResolver(context: Context) {

    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

    @Suppress("DEPRECATION") // The synchronous API is the only one below API 33.
    private fun lookup(latitude: Double, longitude: Double): String? =
        geocoder.getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { address -> format(address) }

    suspend fun resolve(
        latitude: Double,
        longitude: Double,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    ): String? {
        if (!Geocoder.isPresent()) {
            Log.i(TAG, "geocoder is not available on this device")
            return null
        }
        return withContext(Dispatchers.IO) {
            val task = geocoderExecutor.submit(Callable { lookup(latitude, longitude) })
            try {
                task.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (timeout: TimeoutException) {
                task.cancel(true)
                Log.i(TAG, "reverse geocoding timed out after ${timeoutMillis}ms")
                null
            } catch (interrupted: InterruptedException) {
                task.cancel(true)
                Thread.currentThread().interrupt()
                null
            } catch (execution: ExecutionException) {
                Log.i(TAG, "reverse geocoding failed: ${execution.cause?.javaClass?.simpleName}")
                null
            }
        }
    }

    private fun format(address: android.location.Address): String? {
        val full = address.getAddressLine(0)?.trim()
        if (!full.isNullOrEmpty()) {
            return full.take(MAX_ADDRESS_LENGTH)
        }
        val parts = listOfNotNull(
            address.locality ?: address.subAdminArea ?: address.adminArea,
            address.thoroughfare,
            address.subThoroughfare,
        ).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return parts.joinToString(", ").take(MAX_ADDRESS_LENGTH)
    }

    private companion object {
        private const val TAG = "ImHerePrototype"
        private const val DEFAULT_TIMEOUT_MS = 2_000L
        private const val MAX_ADDRESS_LENGTH = 150

        /**
         * One daemon thread: a stuck platform call can block at most itself and
         * never delays the SOS operation. Later requests still time out normally.
         */
        private val geocoderExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "imhere-geocoder").apply { isDaemon = true }
        }
    }
}
