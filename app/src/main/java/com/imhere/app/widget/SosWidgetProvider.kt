package com.imhere.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Exported widget provider. It only handles the system widget lifecycle
 * (`APPWIDGET_UPDATE`); user taps are delivered to the non-exported
 * [WidgetTapReceiver] through a PendingIntent, so other apps cannot trigger
 * the SOS pipeline.
 */
class SosWidgetProvider : AppWidgetProvider() {

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
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetRenderer.buildViews(context, appWidgetId))
    }
}
