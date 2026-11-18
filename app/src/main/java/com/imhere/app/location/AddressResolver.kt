package com.imhere.app.location

import android.content.Context
import android.location.Geocoder
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional reverse geocoding (specification 8.4).
 *
 * The address is a convenience only: when the geocoder is missing, offline or
 * too slow, the caller gets `null` and continues without it. The call never
 * blocks the location pipeline for longer than [timeoutMillis].
 */
class AddressResolver(context: Context) {

    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun resolve(
        latitude: Double,
        longitude: Double,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    ): String? {
        if (!Geocoder.isPresent()) {
            Log.i(TAG, "geocoder is not available on this device")
            return null
        }
        return withTimeoutOrNull(timeoutMillis) {
            withContext(Dispatchers.IO) {
                runCatching { geocoder.getFromLocation(latitude, longitude, 1) }
                    .onFailure { throwable ->
                        Log.i(TAG, "reverse geocoding failed: ${throwable.javaClass.simpleName}")
                    }
                    .getOrNull()
                    ?.firstOrNull()
                    ?.let { address -> format(address) }
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
        private const val DEFAULT_TIMEOUT_MS = 3_000L
        private const val MAX_ADDRESS_LENGTH = 150
    }
}
