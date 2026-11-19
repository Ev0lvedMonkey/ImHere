package com.imhere.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimAvailabilityTest {

    private val simA = SimInfo(subscriptionId = 1, slotIndex = 0, label = "Carrier A")
    private val simB = SimInfo(subscriptionId = 2, slotIndex = 1, label = "Carrier B")

    @Test
    fun `missing permission blocks sending`() {
        val availability = resolveSimAvailability(
            selectedId = null,
            state = SimState(permissionGranted = false, sims = listOf(simA)),
        )

        assertEquals(SimAvailability.PermissionRequired, availability)
    }

    @Test
    fun `no active sim is reported explicitly`() {
        val availability = resolveSimAvailability(
            selectedId = null,
            state = SimState(permissionGranted = true, sims = emptyList()),
        )

        assertEquals(SimAvailability.NoSims, availability)
    }

    @Test
    fun `single sim is used automatically when nothing is selected`() {
        val availability = resolveSimAvailability(
            selectedId = null,
            state = SimState(permissionGranted = true, sims = listOf(simA)),
        )

        assertEquals(SimAvailability.Ready(simA), availability)
    }

    @Test
    fun `several sims require an explicit choice`() {
        val availability = resolveSimAvailability(
            selectedId = null,
            state = SimState(permissionGranted = true, sims = listOf(simA, simB)),
        )

        assertTrue(availability is SimAvailability.NotSelected)
        assertEquals(listOf(simA, simB), (availability as SimAvailability.NotSelected).sims)
    }

    @Test
    fun `selected sim is used`() {
        val availability = resolveSimAvailability(
            selectedId = simB.subscriptionId,
            state = SimState(permissionGranted = true, sims = listOf(simA, simB)),
        )

        assertEquals(SimAvailability.Ready(simB), availability)
    }

    @Test
    fun `stale selection is never replaced automatically`() {
        val availability = resolveSimAvailability(
            selectedId = 99,
            state = SimState(permissionGranted = true, sims = listOf(simA, simB)),
        )

        assertTrue(availability is SimAvailability.Unavailable)
        val unavailable = availability as SimAvailability.Unavailable
        assertEquals(99, unavailable.selectedId)
        assertEquals(listOf(simA, simB), unavailable.sims)
    }
}
