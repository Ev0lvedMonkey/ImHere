package com.imhere.app.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageValuesTest {

    private val locale = Locale.US
    private val acquiredAt = 1_700_000_000_000L

    private fun values(address: String?) = MessageValues.forLocation(
        latitude = 55.755826,
        longitude = 37.617300,
        address = address,
        acquiredAtMillis = acquiredAt,
        locale = locale,
    )

    @Test
    fun `coordinates use six decimal places`() {
        val map = values(address = null)

        assertEquals("55.755826", map[MessageTemplate.LATITUDE])
        assertEquals("37.617300", map[MessageTemplate.LONGITUDE])
    }

    @Test
    fun `map link matches the required format`() {
        val map = values(address = null)

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=55.755826,37.617300",
            map[MessageTemplate.MAP_LINK],
        )
    }

    @Test
    fun `missing address keeps the key so the line is removed`() {
        val map = values(address = null)

        assertTrue(map.containsKey(MessageTemplate.ADDRESS))
        assertNull(map[MessageTemplate.ADDRESS])

        val template = "SOS\nAddress: {address}\n{latitude}, {longitude}"
        assertEquals(
            "SOS\n55.755826, 37.617300",
            MessageTemplate.render(template, map),
        )
    }

    @Test
    fun `address is substituted when available`() {
        val map = values(address = "Moscow, Red Square")

        val template = "SOS\nAddress: {address}\n{latitude}, {longitude}"
        assertEquals(
            "SOS\nAddress: Moscow, Red Square\n55.755826, 37.617300",
            MessageTemplate.render(template, map),
        )
    }

    @Test
    fun `time variable is always present`() {
        val map = values(address = null)

        assertTrue(map[MessageTemplate.TIME]?.isNotBlank() == true)
    }
}
