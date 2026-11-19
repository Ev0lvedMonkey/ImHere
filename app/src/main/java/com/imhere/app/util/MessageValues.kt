package com.imhere.app.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Builds the variable values used by [MessageTemplate] for a location message. */
object MessageValues {

    /**
     * [address] is `null` when reverse geocoding failed; the key is still
     * present so [MessageTemplate] removes the line instead of leaving the
     * literal `{address}` in the message.
     */
    fun forLocation(
        latitude: Double,
        longitude: Double,
        address: String?,
        acquiredAtMillis: Long,
        locale: Locale = Locale.getDefault(),
    ): Map<String, String?> = mapOf(
        MessageTemplate.ADDRESS to address,
        MessageTemplate.LATITUDE to String.format(Locale.US, "%.6f", latitude),
        MessageTemplate.LONGITUDE to String.format(Locale.US, "%.6f", longitude),
        MessageTemplate.MAP_LINK to MapLinks.forCoordinates(latitude, longitude),
        MessageTemplate.TIME to DateFormat.getTimeInstance(DateFormat.MEDIUM, locale)
            .format(Date(acquiredAtMillis)),
    )
}
