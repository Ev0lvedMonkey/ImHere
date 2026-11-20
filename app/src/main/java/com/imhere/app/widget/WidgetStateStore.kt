package com.imhere.app.widget

import android.content.Context
import android.os.SystemClock

/**
 * Persists the small piece of technical state needed by the widget double tap.
 * This is not user history: only the last tap timestamp, the busy flag and the
 * cooldown deadline are stored, and only to guard the launch itself.
 */
object WidgetStateStore {

    private const val PREFS_NAME = "widget_state"
    private const val KEY_LAST_TAP = "last_tap_elapsed"
    private const val KEY_OPERATION_ACTIVE = "operation_active"
    private const val KEY_OPERATION_STARTED = "operation_started_elapsed"

    // Wall-clock time: elapsedRealtime() is reset by a reboot, which used to
    // leave the widget blocked for the previous uptime after a restart.
    private const val KEY_COOLDOWN_UNTIL = "cooldown_until_wall"

    const val DOUBLE_TAP_WINDOW_MS = 2_000L
    private const val STALE_OPERATION_MS = 3 * 60_000L

    /**
     * A persisted cooldown is trusted only while it is at most this far in the
     * future. Anything larger is a corrupted or pre-upgrade value and is
     * discarded instead of blocking the widget indefinitely.
     */
    private const val MAX_COOLDOWN_MS = 60_000L

    enum class TapResult { FIRST_TAP, START_SOS, BUSY, COOLDOWN }

    @Synchronized
    fun onTap(context: Context): TapResult {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()

        if (isActiveLocked(prefs, now)) {
            return TapResult.BUSY
        }
        clearStaleOperationLocked(prefs)

        // Right after a successful operation the widget ignores taps for a few
        // seconds so an accidental extra tap cannot send a second message.
        if (isCoolingDownLocked(prefs, System.currentTimeMillis())) {
            prefs.edit().putLong(KEY_LAST_TAP, 0L).apply()
            return TapResult.COOLDOWN
        }

        val lastTap = prefs.getLong(KEY_LAST_TAP, 0L)
        if (lastTap > 0L && now - lastTap in 0..DOUBLE_TAP_WINDOW_MS) {
            prefs.edit()
                .putLong(KEY_LAST_TAP, 0L)
                .putBoolean(KEY_OPERATION_ACTIVE, true)
                .putLong(KEY_OPERATION_STARTED, now)
                .apply()
            return TapResult.START_SOS
        }

        prefs.edit().putLong(KEY_LAST_TAP, now).apply()
        return TapResult.FIRST_TAP
    }

    @Synchronized
    fun hasPendingSecondTap(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (isActiveLocked(prefs, SystemClock.elapsedRealtime())) return false
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

    /** Returns `false` for an operation that died more than three minutes ago. */
    @Synchronized
    fun isOperationActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val active = isActiveLocked(prefs, SystemClock.elapsedRealtime())
        if (!active) clearStaleOperationLocked(prefs)
        return active
    }

    /** Blocks a new launch for [durationMs] after a successful operation. */
    @Synchronized
    fun startCooldown(context: Context, durationMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_COOLDOWN_UNTIL, System.currentTimeMillis() + durationMs)
            .apply()
    }

    @Synchronized
    fun isCoolingDown(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return isCoolingDownLocked(prefs, System.currentTimeMillis())
    }

    private fun isActiveLocked(
        prefs: android.content.SharedPreferences,
        now: Long,
    ): Boolean {
        if (!prefs.getBoolean(KEY_OPERATION_ACTIVE, false)) return false
        val started = prefs.getLong(KEY_OPERATION_STARTED, 0L)
        val age = now - started
        return started > 0L && age in 0..STALE_OPERATION_MS
    }

    private fun clearStaleOperationLocked(prefs: android.content.SharedPreferences) {
        if (!prefs.getBoolean(KEY_OPERATION_ACTIVE, false)) return
        if (isActiveLocked(prefs, SystemClock.elapsedRealtime())) return
        // A previous operation died without clearing the flag.
        prefs.edit()
            .putBoolean(KEY_OPERATION_ACTIVE, false)
            .putLong(KEY_LAST_TAP, 0L)
            .apply()
    }

    private fun isCoolingDownLocked(
        prefs: android.content.SharedPreferences,
        now: Long,
    ): Boolean {
        val until = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
        val remaining = until - now
        if (remaining in 1..MAX_COOLDOWN_MS) return true
        if (until != 0L && remaining !in 1..MAX_COOLDOWN_MS) {
            prefs.edit().putLong(KEY_COOLDOWN_UNTIL, 0L).apply()
        }
        return false
    }
}
