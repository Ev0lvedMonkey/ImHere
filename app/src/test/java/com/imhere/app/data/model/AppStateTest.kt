package com.imhere.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateTest {

    @Test
    fun `a fresh install still needs onboarding`() {
        assertFalse(AppState().sanitized().onboardingCompleted)
    }

    @Test
    fun `a schema 1 file belongs to an existing user and skips onboarding`() {
        val legacy = AppState(schemaVersion = 1)

        val sanitized = legacy.sanitized()

        assertTrue(sanitized.onboardingCompleted)
        assertTrue(sanitized.schemaVersion >= 2)
    }

    @Test
    fun `an explicit completed flag survives sanitizing`() {
        val state = AppState(onboardingCompleted = true)

        assertTrue(state.sanitized().onboardingCompleted)
    }
}
