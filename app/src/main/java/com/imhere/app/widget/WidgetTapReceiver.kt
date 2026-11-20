package com.imhere.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.imhere.app.R
import com.imhere.app.location.LocationResolver
import com.imhere.app.notifications.NotificationHelper
import com.imhere.app.service.SosService

/**
 * Handles taps on the home-screen widget.
 *
 * This receiver is **not exported**: only the PendingIntent created by
 * [WidgetRenderer] can start it. It is deliberately kept separate from
 * [SosWidgetProvider], which must be exported for `APPWIDGET_UPDATE`. Otherwise
 * any installed app could send two broadcasts and trigger an SOS.
 */
class WidgetTapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) return
        handleTap(context, intent)
    }

    private fun handleTap(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        Log.i(TAG, "widget tap, id=$appWidgetId")

        when (WidgetStateStore.onTap(context)) {
            WidgetStateStore.TapResult.FIRST_TAP -> {
                Log.i(TAG, "first tap: waiting for the second tap within 2 s")
                WidgetRenderer.updateAll(context)
                // Restore the idle visual state if the process survives long enough.
                // The persisted timestamp stays the source of truth either way.
                Handler(Looper.getMainLooper()).postDelayed(
                    { WidgetRenderer.updateAll(context) },
                    WidgetStateStore.DOUBLE_TAP_WINDOW_MS + 200L,
                )
            }

            WidgetStateStore.TapResult.START_SOS -> {
                Log.i(TAG, "second tap confirmed: starting SOS")
                if (!LocationResolver(context).hasLocationPermission()) {
                    Log.w(TAG, "cannot start SOS: location permission is missing")
                    abortStart(context, context.getString(R.string.notif_failure_permission))
                    return
                }
                WidgetRenderer.updateAll(context)
                if (!SosService.start(context, source = "widget")) {
                    abortStart(context, context.getString(R.string.notif_start_failed))
                }
            }

            WidgetStateStore.TapResult.BUSY -> {
                Log.i(TAG, "SOS is already running, ignoring tap")
                WidgetRenderer.updateAll(context)
            }

            WidgetStateStore.TapResult.COOLDOWN -> {
                Log.i(TAG, "cooldown after a successful SOS, ignoring tap")
                WidgetRenderer.updateAll(context)
            }
        }
    }

    private fun abortStart(context: Context, message: String) {
        WidgetStateStore.setOperationActive(context, false)
        WidgetRenderer.updateAll(context)
        NotificationHelper.showResult(
            context,
            context.getString(R.string.notif_failure_title),
            message,
        )
    }

    companion object {
        private const val TAG = "ImHereWidget"
        const val ACTION_TAP = "com.imhere.app.action.WIDGET_TAP"
    }
}
