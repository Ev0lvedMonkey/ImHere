package com.imhere.app.ui

import android.content.Context
import com.imhere.app.R
import com.imhere.app.sms.SmsError
import com.imhere.app.sms.SmsRecipientResult
import com.imhere.app.sms.SmsRecipientState
import com.imhere.app.sms.SmsRejection
import com.imhere.app.sms.SmsSnapshot

/** Human-readable status of one recipient. */
fun SmsRecipientResult.statusText(context: Context): String = when (state) {
    SmsRecipientState.PENDING -> context.getString(R.string.sms_status_pending)
    SmsRecipientState.SENT -> context.getString(R.string.sms_status_sent)
    SmsRecipientState.FAILED -> context.getString(
        R.string.sms_status_failed,
        (sentError ?: SmsError.GENERIC_FAILURE).toUserMessage(context),
    )

    SmsRecipientState.DELIVERED -> context.getString(R.string.sms_status_delivered)
    SmsRecipientState.NOT_DELIVERED -> context.getString(
        R.string.sms_status_not_delivered,
        (deliveryError ?: SmsError.GENERIC_FAILURE).toUserMessage(context),
    )
}

fun SmsError.toUserMessage(context: Context): String = when (this) {
    SmsError.GENERIC_FAILURE -> context.getString(R.string.sms_error_generic)
    SmsError.NO_SERVICE -> context.getString(R.string.sms_error_no_service)
    SmsError.RADIO_OFF -> context.getString(R.string.sms_error_radio_off)
    SmsError.NULL_PDU -> context.getString(R.string.sms_error_null_pdu)
    SmsError.LIMIT_EXCEEDED -> context.getString(R.string.sms_error_limit)
    SmsError.FDN_CHECK_FAILURE -> context.getString(R.string.sms_error_fdn)
    SmsError.INVALID_NUMBER -> context.getString(R.string.sms_error_invalid_number)
    SmsError.NO_PERMISSION -> context.getString(R.string.sms_error_no_permission)
    SmsError.TOO_MANY_PARTS -> context.getString(R.string.sms_error_too_long)
    SmsError.NOT_SENT -> context.getString(R.string.sms_error_not_sent)
}

fun SmsRejection.toUserMessage(context: Context): String = when (this) {
    SmsRejection.NO_PERMISSION -> context.getString(R.string.sms_rejection_no_permission)
    SmsRejection.SIM_PERMISSION -> context.getString(R.string.sms_rejection_sim_permission)
    SmsRejection.NO_SIM -> context.getString(R.string.sms_rejection_no_sim)
    SmsRejection.SIM_NOT_SELECTED -> context.getString(R.string.sms_rejection_sim_not_selected)
    SmsRejection.SIM_UNAVAILABLE -> context.getString(R.string.sms_rejection_sim_unavailable)
    SmsRejection.NO_RECIPIENTS -> context.getString(R.string.sms_rejection_no_recipients)
    SmsRejection.EMPTY_MESSAGE -> context.getString(R.string.sms_rejection_empty_message)
    SmsRejection.MESSAGE_TOO_LONG -> context.getString(R.string.sms_rejection_too_long)
    SmsRejection.MESSAGE_SPLIT_FAILED -> context.getString(R.string.sms_rejection_split_failed)
}

/** One line per recipient, used by the result notification and the debug screen. */
fun SmsSnapshot.toResultText(context: Context): String =
    recipients.joinToString(separator = "\n") { recipient ->
        context.getString(
            R.string.sms_result_line,
            recipient.name,
            recipient.statusText(context),
        )
    }
