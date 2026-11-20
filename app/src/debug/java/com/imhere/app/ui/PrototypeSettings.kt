package com.imhere.app.ui

import android.content.Context

/** Debug-only state used by the diagnostics screen. */
object PrototypeSettings {

    private const val PREFS_NAME = "prototype_settings"
    private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"

    fun permissionsRequested(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSIONS_REQUESTED, false)

    fun setPermissionsRequested(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSIONS_REQUESTED, value)
            .apply()
    }
}
