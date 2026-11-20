package com.imhere.app.ui

import android.content.Context

/** Prototype-only switches. Removed once the real application ships. */
object PrototypeSettings {

    private const val PREFS_NAME = "prototype_settings"
    private const val KEY_ACCEPT_INEXACT = "accept_inexact_location"
    private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
    private const val KEY_ARTIFICIAL_DELAY = "artificial_delay"

    fun acceptInexactLocation(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPT_INEXACT, false)

    fun setAcceptInexactLocation(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPT_INEXACT, value)
            .apply()
    }

    fun permissionsRequested(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSIONS_REQUESTED, false)

    fun setPermissionsRequested(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSIONS_REQUESTED, value)
            .apply()
    }

    /**
     * Prototype-only instrumentation: keeps the SOS operation busy for a while so the
     * repeated-tap lock can be verified on a device where a fix arrives in seconds.
     */
    fun artificialDelayMillis(context: Context): Long =
        if (context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ARTIFICIAL_DELAY, false)
        ) {
            ARTIFICIAL_DELAY_MS
        } else {
            0L
        }

    fun setArtificialDelay(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ARTIFICIAL_DELAY, value)
            .apply()
    }

    private const val ARTIFICIAL_DELAY_MS = 10_000L
}
