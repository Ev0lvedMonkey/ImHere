package com.imhere.app.sms

import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps send operations alive after the sending coroutine returns, so late
 * delivery reports can still update the result notification.
 *
 * This is deliberately in-memory only: the specification forbids storing a
 * history of sent SOS messages. An operation is dropped ten minutes after it
 * was created; if the process dies earlier, delivery updates are ignored and
 * the last shown status remains "sent to the operator".
 */
internal object SmsOperationRegistry {

    private const val MAX_AGE_MS = 10 * 60_000L
    private val operations = ConcurrentHashMap<String, SmsOperation>()

    fun register(operation: SmsOperation) {
        cleanup()
        operations[operation.token] = operation
    }

    fun reportSent(token: String, recipient: Int, part: Int, success: Boolean, error: SmsError?) {
        operations[token]?.reportSent(recipient, part, success, error)
    }

    fun reportDelivered(token: String, recipient: Int, part: Int, success: Boolean, error: SmsError?) {
        operations[token]?.reportDelivered(recipient, part, success, error)
    }

    fun snapshot(token: String): SmsSnapshot? = operations[token]?.snapshot()

    private fun cleanup() {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        operations.entries.removeIf { entry -> entry.value.createdAt < cutoff }
    }
}
