package com.imhere.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.imhere.app.R

object WidgetRenderer {

    enum class State { IDLE, WAITING, BUSY }

    fun currentState(context: Context): State = when {
        WidgetStateStore.isOperationActive(context) -> State.BUSY
        WidgetStateStore.hasPendingSecondTap(context) -> State.WAITING
        else -> State.IDLE
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, SosWidgetProvider::class.java))
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
    }

    fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_sos)
        val state = currentState(context)

        val subtitleRes: Int
        val backgroundRes: Int
        val contentDescriptionRes: Int
        when (state) {
            State.IDLE -> {
                subtitleRes = R.string.widget_subtitle_idle
                backgroundRes = R.drawable.bg_widget_idle
                contentDescriptionRes = R.string.widget_content_description_idle
            }

            State.WAITING -> {
                subtitleRes = R.string.widget_subtitle_waiting
                backgroundRes = R.drawable.bg_widget_waiting
                contentDescriptionRes = R.string.widget_content_description_waiting
            }

            State.BUSY -> {
                subtitleRes = R.string.widget_subtitle_busy
                backgroundRes = R.drawable.bg_widget_busy
                contentDescriptionRes = R.string.widget_content_description_busy
            }
        }

        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        views.setTextViewText(R.id.widget_subtitle, context.getString(subtitleRes))
        views.setInt(R.id.widget_root, "setBackgroundResource", backgroundRes)
        views.setContentDescription(R.id.widget_root, context.getString(contentDescriptionRes))
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, appWidgetId))
        return views
    }

    private fun tapIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WidgetTapReceiver::class.java)
            .setAction(WidgetTapReceiver.ACTION_TAP)
            .setData(Uri.parse("imhere://widget/$appWidgetId/tap"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return PendingIntent.getBroadcast(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
