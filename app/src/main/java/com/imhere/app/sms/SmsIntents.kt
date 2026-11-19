package com.imhere.app.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import java.util.concurrent.atomic.AtomicInteger

/** PendingIntents shared by the real and the dry-run transports. */
internal object SmsIntents {

    const val ACTION_SENT = "com.imhere.app.action.SMS_SENT"
    const val ACTION_DELIVERED = "com.imhere.app.action.SMS_DELIVERED"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_RECIPIENT = "recipient"
    const val EXTRA_PART = "part"

    // PendingIntent identity ignores extras, so every intent needs its own
    // request code. The random base avoids collisions with intents left over
    // from a previous process.
    private val requestCode = AtomicInteger((1..100_000).random())

    fun sent(context: Context, token: String, recipient: Int, part: Int): PendingIntent =
        build(context, ACTION_SENT, token, recipient, part, mutable = false)

    fun delivered(context: Context, token: String, recipient: Int, part: Int): PendingIntent =
        build(context, ACTION_DELIVERED, token, recipient, part, mutable = true)

    private fun build(
        context: Context,
        action: String,
        token: String,
        recipient: Int,
        part: Int,
        mutable: Boolean,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TOKEN, token)
            .putExtra(EXTRA_RECIPIENT, recipient)
            .putExtra(EXTRA_PART, part)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        flags = flags or if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode.incrementAndGet(), intent, flags)
    }
}

/** Maps platform result codes to user-facing reasons. */
internal fun smsErrorForResultCode(code: Int): SmsError = when (code) {
    SmsManager.RESULT_ERROR_NO_SERVICE -> SmsError.NO_SERVICE
    SmsManager.RESULT_ERROR_RADIO_OFF -> SmsError.RADIO_OFF
    SmsManager.RESULT_ERROR_NULL_PDU -> SmsError.NULL_PDU
    SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> SmsError.LIMIT_EXCEEDED
    SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> SmsError.FDN_CHECK_FAILURE
    else -> SmsError.GENERIC_FAILURE
}
