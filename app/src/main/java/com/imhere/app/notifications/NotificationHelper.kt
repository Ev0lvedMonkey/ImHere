package com.imhere.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.imhere.app.R
import com.imhere.app.ui.MainActivity

object NotificationHelper {

    const val CHANNEL_PROGRESS = "sos_progress"
    const val CHANNEL_RESULT = "sos_result"
    const val PROGRESS_NOTIFICATION_ID = 1001
    const val RESULT_NOTIFICATION_ID = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notif_channel_progress_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_progress_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val result = NotificationChannel(
            CHANNEL_RESULT,
            context.getString(R.string.notif_channel_result_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_result_description)
        }

        manager.createNotificationChannels(listOf(progress, result))
    }

    fun progress(context: Context, text: String): Notification =
        Notification.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_progress_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(context))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()

    @SuppressLint("MissingPermission") // Guarded by canPost() below.
    fun showResult(context: Context, title: String, text: String, onlyAlertOnce: Boolean = false) {
        // On Android 13+ posting is silently dropped without the runtime permission.
        if (!canPost(context)) return
        val notification = Notification.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openAppIntent(context))
            .build()
        NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
    }

    /** Replaces the foreground progress notification while the service runs. */
    @SuppressLint("MissingPermission") // Guarded by canPost() below.
    fun updateProgress(context: Context, text: String) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context)
            .notify(PROGRESS_NOTIFICATION_ID, progress(context, text))
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
