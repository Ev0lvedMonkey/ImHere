package com.imhere.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.imhere.app.util.PhoneNumberValidator
import java.util.UUID
import kotlinx.coroutines.CancellationException

/**
 * Validates the SIM and recipients, hands every recipient to the transport and
 * waits for the sent reports.
 *
 * One recipient is one SMS: a failure while queueing it never stops the other
 * recipients. [onUpdate] is called for reports that arrive after [send] has
 * returned, so a caller can keep a result notification up to date.
 */
class SmsDispatcher(
    private val context: Context,
    private val sims: SimManager,
    private val phoneNumbers: PhoneNumberValidator,
    private val transport: SmsTransport,
) {

    suspend fun send(
        selectedSubscriptionId: Int?,
        recipients: List<SmsRecipient>,
        message: String,
        onUpdate: ((SmsSnapshot) -> Unit)? = null,
    ): SmsSendResult {
        if (recipients.isEmpty()) return SmsSendResult.Rejected(SmsRejection.NO_RECIPIENTS)
        if (message.isBlank()) return SmsSendResult.Rejected(SmsRejection.EMPTY_MESSAGE)
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            return SmsSendResult.Rejected(SmsRejection.NO_PERMISSION)
        }

        val simState = sims.loadFresh()
        if (!simState.permissionGranted) {
            return SmsSendResult.Rejected(SmsRejection.SIM_PERMISSION)
        }
        val sim = when (val availability = resolveSimAvailability(selectedSubscriptionId, simState)) {
            is SimAvailability.Ready -> availability.sim
            SimAvailability.PermissionRequired ->
                return SmsSendResult.Rejected(SmsRejection.SIM_PERMISSION)

            SimAvailability.NoSims -> return SmsSendResult.Rejected(SmsRejection.NO_SIM)
            is SimAvailability.NotSelected ->
                return SmsSendResult.Rejected(SmsRejection.SIM_NOT_SELECTED)

            is SimAvailability.Unavailable ->
                return SmsSendResult.Rejected(SmsRejection.SIM_UNAVAILABLE)
        }

        val parts = try {
            transport.divide(sim.subscriptionId, message)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "cannot split the message", throwable)
            return SmsSendResult.Rejected(SmsRejection.MESSAGE_SPLIT_FAILED)
        }
        if (parts.isEmpty()) {
            return SmsSendResult.Rejected(SmsRejection.MESSAGE_SPLIT_FAILED)
        }
        if (parts.size > MAX_PARTS) {
            Log.w(TAG, "message needs ${parts.size} parts, limit is $MAX_PARTS")
            return SmsSendResult.Rejected(SmsRejection.MESSAGE_TOO_LONG)
        }

        val token = UUID.randomUUID().toString()
        val operation = SmsOperation(
            token = token,
            recipients = recipients,
            partsPerRecipient = List(recipients.size) { parts.size },
            updateListener = onUpdate,
        )
        SmsOperationRegistry.register(operation)
        Log.i(
            TAG,
            "send: token=$token sim=${sim.subscriptionId} slot=${sim.slotIndex} " +
                "parts=${parts.size} recipients=${recipients.size}",
        )

        recipients.forEachIndexed { index, recipient ->
            if (phoneNumbers.normalize(recipient.phoneE164) == null) {
                Log.w(TAG, "recipient $index has an invalid number")
                operation.reportUnsendable(index, SmsError.INVALID_NUMBER)
                return@forEachIndexed
            }
            try {
                transport.send(
                    subscriptionId = sim.subscriptionId,
                    recipient = recipient.phoneE164,
                    parts = parts,
                    token = token,
                    recipientIndex = index,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (security: SecurityException) {
                Log.w(TAG, "recipient $index: SMS permission was revoked", security)
                operation.reportUnsendable(index, SmsError.NO_PERMISSION)
            } catch (illegal: IllegalArgumentException) {
                Log.w(TAG, "recipient $index: platform rejected the message", illegal)
                operation.reportUnsendable(index, SmsError.INVALID_NUMBER)
            } catch (throwable: Throwable) {
                Log.e(TAG, "recipient $index: queueing failed", throwable)
                operation.reportUnsendable(index, SmsError.GENERIC_FAILURE)
            }
        }

        val snapshot = operation.awaitSending(SENT_TIMEOUT_MS)
        Log.i(
            TAG,
            "send finished: token=$token " +
                snapshot.recipients.joinToString { "${it.state}:${it.partsSent}/${it.partsTotal}" },
        )
        return SmsSendResult.Completed(snapshot)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "ImHereSms"

        /** Long enough for slow networks, short enough not to block the SOS flow. */
        private const val SENT_TIMEOUT_MS = 30_000L

        /** Ten parts is about ten concatenated SMS; longer templates are rejected. */
        private const val MAX_PARTS = 10
    }
}
