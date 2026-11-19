package com.imhere.app.sms

/** One logical recipient of one SMS message. */
data class SmsRecipient(
    val contactId: String?,
    val name: String,
    val phoneE164: String,
)

/** Technical reason why a message or a part of it was not sent. */
enum class SmsError {
    GENERIC_FAILURE,
    NO_SERVICE,
    RADIO_OFF,
    NULL_PDU,
    LIMIT_EXCEEDED,
    FDN_CHECK_FAILURE,
    INVALID_NUMBER,
    NO_PERMISSION,
    TOO_MANY_PARTS,
    NOT_SENT,
}

/**
 * State of one recipient.
 *
 * [SENT] means every part was accepted by the mobile operator. It is not proof
 * of delivery: [DELIVERED] is only reported when the carrier sends delivery
 * reports for every part.
 */
enum class SmsRecipientState { PENDING, SENT, FAILED, DELIVERED, NOT_DELIVERED }

data class SmsRecipientResult(
    val contactId: String?,
    val name: String,
    val phoneE164: String,
    val state: SmsRecipientState,
    val partsTotal: Int,
    val partsSent: Int,
    val sentError: SmsError?,
    val deliveryReportReceived: Boolean,
    val deliveryError: SmsError?,
)

data class SmsSnapshot(
    val token: String,
    val recipients: List<SmsRecipientResult>,
    val sendingComplete: Boolean,
) {
    val hasFailures: Boolean
        get() = recipients.any {
            it.state == SmsRecipientState.FAILED || it.state == SmsRecipientState.NOT_DELIVERED
        }

    val allDelivered: Boolean
        get() = recipients.isNotEmpty() && recipients.all { it.state == SmsRecipientState.DELIVERED }
}

/** Global rejection before any message was handed to the SMS service. */
enum class SmsRejection {
    NO_PERMISSION,
    SIM_PERMISSION,
    NO_SIM,
    SIM_NOT_SELECTED,
    SIM_UNAVAILABLE,
    NO_RECIPIENTS,
    EMPTY_MESSAGE,
    MESSAGE_TOO_LONG,
    MESSAGE_SPLIT_FAILED,
}

sealed interface SmsSendResult {
    data class Completed(val snapshot: SmsSnapshot) : SmsSendResult
    data class Rejected(val reason: SmsRejection) : SmsSendResult
}
