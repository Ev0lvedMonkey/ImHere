package com.imhere.app.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives sent and delivery reports for every SMS part.
 *
 * A successful send result only means "accepted by the network"; it is shown as
 * "sent to the operator". Delivery is reported separately and only when the
 * carrier provides a report.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(SmsIntents.EXTRA_TOKEN) ?: return
        val recipient = intent.getIntExtra(SmsIntents.EXTRA_RECIPIENT, -1)
        val part = intent.getIntExtra(SmsIntents.EXTRA_PART, -1)
        if (recipient < 0 || part < 0) return

        val code = resultCode
        val success = code == Activity.RESULT_OK
        when (intent.action) {
            SmsIntents.ACTION_SENT -> SmsOperationRegistry.reportSent(
                token = token,
                recipient = recipient,
                part = part,
                success = success,
                error = if (success) null else smsErrorForResultCode(code),
            )

            SmsIntents.ACTION_DELIVERED -> SmsOperationRegistry.reportDelivered(
                token = token,
                recipient = recipient,
                part = part,
                success = success,
                error = if (success) null else smsErrorForResultCode(code),
            )

            else -> return
        }
        Log.i(
            TAG,
            "report action=${intent.action} recipient=$recipient part=$part " +
                "code=$code success=$success",
        )
    }

    private companion object {
        private const val TAG = "ImHereSms"
    }
}
