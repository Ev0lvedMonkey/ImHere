package com.imhere.app.sms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import java.util.ArrayList

/** The production transport: sends through the platform SMS service. */
class AndroidSmsTransport(private val context: Context) : SmsTransport {

    override fun divide(subscriptionId: Int, message: String): List<String> =
        smsManager(subscriptionId).divideMessage(message).orEmpty()

    @SuppressLint("MissingPermission") // SEND_SMS is checked by SmsDispatcher.
    override fun send(
        subscriptionId: Int,
        recipient: String,
        parts: List<String>,
        token: String,
        recipientIndex: Int,
    ) {
        val manager = smsManager(subscriptionId)
        val sentIntents = parts.indices.map { part ->
            SmsIntents.sent(context, token, recipientIndex, part)
        }
        val deliveryIntents = parts.indices.map { part ->
            SmsIntents.delivered(context, token, recipientIndex, part)
        }

        if (parts.size == 1) {
            manager.sendTextMessage(
                recipient,
                null,
                parts.single(),
                sentIntents.single(),
                deliveryIntents.single(),
            )
        } else {
            manager.sendMultipartTextMessage(
                recipient,
                null,
                ArrayList(parts),
                ArrayList(sentIntents),
                ArrayList(deliveryIntents),
            )
        }
        Log.i(
            TAG,
            "queued recipient=$recipientIndex subscription=$subscriptionId parts=${parts.size}",
        )
    }

    @Suppress("DEPRECATION") // The API 26-30 path is required for minSdk 26.
    private fun smsManager(subscriptionId: Int): SmsManager {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
                ?: throw IllegalStateException("SmsManager is unavailable")
            return base.createForSubscriptionId(subscriptionId)
        }
        return SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            ?: throw IllegalStateException("SmsManager is unavailable")
    }

    private companion object {
        private const val TAG = "ImHereSms"
    }
}
