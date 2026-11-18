package com.imhere.app.util

import java.util.Locale

/** Builds the map link required by the specification (8.5). */
object MapLinks {

    /**
     * Returns `https://www.google.com/maps/search/?api=1&query={latitude},{longitude}`.
     * The link opens in a browser or any compatible map application and does not
     * require Google Maps to be installed.
     */
    fun forCoordinates(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            String.format(Locale.US, "%.6f,%.6f", latitude, longitude)
}
