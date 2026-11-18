package com.imhere.app.widget

import android.content.Context
import android.os.SystemClock

/**
 * Persists the small piece of technical state needed by the widget double tap.
 * This is not user history: only the last tap timestamp and the busy flag.
 */
object WidgetStateStore {

    private const val PREFS_NAME = "widget_state"
    private const val KEY_LAST_TAP = "last_tap_elapsed"
    private const val KEY_OPERATION_ACTIVE = "operation_active"
    private const val KEY_OPERATION_STARTED = "operation_started_elapsed"

    const val DOUBLE_TAP_WINDOW_MS = 2_000L
    private const val STALE_OPERATION_MS = 3 * 60_000L

    enum class TapResult { FIRST_TAP, START_SOS, BUSY }

    @Synchronized
    fun onTap(context: Context): TapResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()

        if (prefs.getBoolean(KEY_OPERATION_ACTIVE, false)) {
            val started = prefs.getLong(KEY_OPERATION_STARTED, 0L)
            val age = now - started
            if (started > 0L && age in 0..STALE_OPERATION_MS) {
                return TapResult.BUSY
            }
            // A previous operation died without clearing the flag.
            prefs.edit()
                .putBoolean(KEY_OPERATION_ACTIVE, false)
                .putLong(KEY_LAST_TAP, 0L)
                .commit()
        }

        val lastTap = prefs.getLong(KEY_LAST_TAP, 0L)
        if (lastTap > 0L && now - lastTap in 0..DOUBLE_TAP_WINDOW_MS) {
            prefs.edit()
                .putLong(KEY_LAST_TAP, 0L)
                .putBoolean(KEY_OPERATION_ACTIVE, true)
                .putLong(KEY_OPERATION_STARTED, now)
                .commit()
            return TapResult.START_SOS
        }

        prefs.edit().putLong(KEY_LAST_TAP, now).commit()
        return TapResult.FIRST_TAP
    }

    @Synchronized
    fun hasPendingSecondTap(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OPERATION_ACTIVE, false)) return false
        val lastTap = prefs.getLong(KEY_LAST_TAP, 0L)
        if (lastTap <= 0L) return false
        return SystemClock.elapsedRealtime() - lastTap in 0..DOUBLE_TAP_WINDOW_MS
    }

    @Synchronized
    fun setOperationActive(context: Context, active: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_OPERATION_ACTIVE, active)
            .putLong(KEY_OPERATION_STARTED, if (active) SystemClock.elapsedRealtime() else 0L)
            .apply()
    }

    @Synchronized
    fun isOperationActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OPERATION_ACTIVE, false)
}
