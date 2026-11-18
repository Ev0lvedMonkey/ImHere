package com.imhere.app.ui

import android.content.Context

/** Prototype-only switches. Removed once the real application ships. */
object PrototypeSettings {

    private const val PREFS_NAME = "prototype_settings"
    private const val KEY_ACCEPT_INEXACT = "accept_inexact_location"

    fun acceptInexactLocation(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACCEPT_INEXACT, false)

    fun setAcceptInexactLocation(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCEPT_INEXACT, value)
            .apply()
    }
}
