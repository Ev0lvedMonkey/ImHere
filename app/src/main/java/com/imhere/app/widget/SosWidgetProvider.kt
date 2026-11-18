package com.imhere.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.imhere.app.R
import com.imhere.app.location.LocationResolver
import com.imhere.app.notifications.NotificationHelper
import com.imhere.app.service.SosService

class SosWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TAP -> handleTap(context, intent)
            ACTION_RESET_VISUAL -> WidgetRenderer.updateAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, WidgetRenderer.buildViews(context, id))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetRenderer.buildViews(context, appWidgetId))
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
        private const val TAG = "ImHerePrototype"
        const val ACTION_TAP = "com.imhere.app.action.WIDGET_TAP"
        const val ACTION_RESET_VISUAL = "com.imhere.app.action.WIDGET_RESET_VISUAL"
    }
}
