package com.imhere.app.sms

/** Low-level SMS transport, replaceable in the debug diagnostics screen. */
interface SmsTransport {

    /** Splits [message] into the parts the platform will actually send. */
    fun divide(subscriptionId: Int, message: String): List<String>

    /**
     * Queues [parts] to [recipient] using the SIM [subscriptionId].
     *
     * The transport only starts the send; results must come through the
     * sent/delivery reports. A dry-run transport may invoke those reports
     * itself.
     */
    fun send(
        subscriptionId: Int,
        recipient: String,
        parts: List<String>,
        token: String,
        recipientIndex: Int,
    )
}
