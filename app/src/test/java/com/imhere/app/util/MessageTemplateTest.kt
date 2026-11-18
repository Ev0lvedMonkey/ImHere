package com.imhere.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTemplateTest {

    private val coordinates = mapOf(
        MessageTemplate.LATITUDE to "55.755800",
        MessageTemplate.LONGITUDE to "37.617300",
        MessageTemplate.TIME to "18.11.2026 17:45",
        MessageTemplate.MAP_LINK to "https://www.google.com/maps/search/?api=1&query=55.755800,37.617300",
        MessageTemplate.ADDRESS to null,
    )

    @Test
    fun `substitutes every known variable`() {
        val template = "SOS\n{latitude}, {longitude}\n{map_link}\nTime: {time}"
        assertEquals(
            "SOS\n55.755800, 37.617300\n" +
                "https://www.google.com/maps/search/?api=1&query=55.755800,37.617300\n" +
                "Time: 18.11.2026 17:45",
            MessageTemplate.render(template, coordinates),
        )
    }

    @Test
    fun `drops a line that consists of a missing address label only`() {
        val template = "SOS\nAddress: {address}\n{map_link}"
        assertEquals(
            "SOS\nhttps://www.google.com/maps/search/?api=1&query=55.755800,37.617300",
            MessageTemplate.render(template, coordinates),
        )
    }

    @Test
    fun `drops empty lines and dangling separators`() {
        val template = "SOS\n\n, ;\n{map_link}"
        assertEquals(
            "SOS\nhttps://www.google.com/maps/search/?api=1&query=55.755800,37.617300",
            MessageTemplate.render(template, coordinates),
        )
    }

    @Test
    fun `keeps the coordinates even when the address is unavailable`() {
        val template = "I am here: {latitude}, {longitude}"
        assertEquals(
            "I am here: 55.755800, 37.617300",
            MessageTemplate.render(template, mapOf(
                MessageTemplate.LATITUDE to "55.755800",
                MessageTemplate.LONGITUDE to "37.617300",
                MessageTemplate.ADDRESS to null,
            )),
        )
    }
}
