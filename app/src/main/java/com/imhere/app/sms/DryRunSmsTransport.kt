package com.imhere.app.sms

import android.app.Activity
import android.content.Context
import android.telephony.SmsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Diagnostic transport used only by the debug screen. It behaves like the real
 * SMS service without sending anything: sent reports arrive shortly after the
 * call, delivery reports arrive a bit later.
 *
 * A recipient ending in `0000` simulates a send failure; everything else
 * simulates successful sending and delivery. The real SOS flow never uses this
 * class.
 */
class DryRunSmsTransport(
    private val context: Context,
    private val scope: CoroutineScope,
) : SmsTransport {

    private val splitter = AndroidSmsTransport(context)

    override fun divide(subscriptionId: Int, message: String): List<String> =
        splitter.divide(subscriptionId, message)

    override fun send(
        subscriptionId: Int,
        recipient: String,
        parts: List<String>,
        token: String,
        recipientIndex: Int,
    ) {
        val sentIntents = parts.indices.map { part ->
            SmsIntents.sent(context, token, recipientIndex, part)
        }
        val deliveryIntents = parts.indices.map { part ->
            SmsIntents.delivered(context, token, recipientIndex, part)
        }
        val failingPart = if (recipient.endsWith(FAILURE_SUFFIX)) parts.lastIndex else -1

        scope.launch {
            delay(SENT_DELAY_MS)
            parts.indices.forEach { part ->
                val code = if (part == failingPart) {
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE
                } else {
                    Activity.RESULT_OK
                }
                runCatching { sentIntents[part].send(code) }
            }
            if (failingPart >= 0) return@launch

            delay(DELIVERY_DELAY_MS)
            deliveryIntents.forEach { intent ->
                runCatching { intent.send(Activity.RESULT_OK) }
            }
        }
    }

    private companion object {
        private const val FAILURE_SUFFIX = "0000"
        private const val SENT_DELAY_MS = 800L
        private const val DELIVERY_DELAY_MS = 1_200L
    }
}
