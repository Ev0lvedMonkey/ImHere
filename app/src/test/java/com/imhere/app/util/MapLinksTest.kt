package com.imhere.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MapLinksTest {

    @Test
    fun `uses the format required by the specification`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=55.755800,37.617300",
            MapLinks.forCoordinates(55.7558, 37.6173),
        )
    }

    @Test
    fun `formats negative coordinates with a dot separator`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=-33.868800,151.209300",
            MapLinks.forCoordinates(-33.8688, 151.2093),
        )
    }

    @Test
    fun `rounds to six decimal places`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=1.234568,2.345679",
            MapLinks.forCoordinates(1.2345678, 2.3456789),
        )
    }
}
