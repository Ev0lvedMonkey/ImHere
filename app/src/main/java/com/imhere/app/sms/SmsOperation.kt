package com.imhere.app.sms

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Collects per-part SMS sent and delivery reports for one send operation.
 *
 * Pure Kotlin: no Android types, so the status aggregation is unit-tested
 * without a device. Reports can arrive on any thread; all state is guarded by
 * [lock]. [updateListener] is invoked only after [awaitSending] has returned,
 * i.e. only for late reports that change the final result.
 */
internal class SmsOperation(
    val token: String,
    recipients: List<SmsRecipient>,
    partsPerRecipient: List<Int>,
    private val updateListener: ((SmsSnapshot) -> Unit)? = null,
) {

    private val recipients = recipients.toList()
    private val parts = partsPerRecipient.toList()
    private val lock = Any()

    private val sentOk = IntArray(recipients.size)
    private val sentFailed = IntArray(recipients.size)
    private val sentErrors = arrayOfNulls<SmsError>(recipients.size)
    private val sentSeen = Array(recipients.size) { BooleanArray(parts[it]) }

    private val deliveredOk = IntArray(recipients.size)
    private val deliveryFailed = IntArray(recipients.size)
    private val deliveryErrors = arrayOfNulls<SmsError>(recipients.size)
    private val deliveryReported = BooleanArray(recipients.size)
    private val deliveredSeen = Array(recipients.size) { BooleanArray(parts[it]) }

    private val allSent = CompletableDeferred<Unit>()
    private var finalized = false

    /** Used by [SmsOperationRegistry] to drop abandoned operations. */
    val createdAt: Long = System.currentTimeMillis()

    init {
        require(recipients.size == parts.size) { "Each recipient needs a part count" }
        require(parts.all { it > 0 }) { "Every message has at least one part" }
    }

    fun reportSent(index: Int, part: Int, success: Boolean, error: SmsError?) {
        val snapshot = synchronized(lock) {
            if (index !in recipients.indices || part !in 0 until parts[index]) return
            if (sentSeen[index][part]) return
            sentSeen[index][part] = true
            if (success) {
                sentOk[index]++
            } else {
                sentFailed[index]++
                if (sentErrors[index] == null) {
                    sentErrors[index] = error ?: SmsError.GENERIC_FAILURE
                }
            }
            completeIfDoneLocked()
            if (finalized) snapshotLocked() else null
        }
        if (snapshot != null) updateListener?.invoke(snapshot)
    }

    /** Marks every part of [index] as failed before any PendingIntent was sent. */
    fun reportUnsendable(index: Int, error: SmsError) {
        val snapshot = synchronized(lock) {
            if (index !in recipients.indices) return
            for (part in 0 until parts[index]) {
                if (sentSeen[index][part]) continue
                sentSeen[index][part] = true
                sentFailed[index]++
                if (sentErrors[index] == null) sentErrors[index] = error
            }
            completeIfDoneLocked()
            if (finalized) snapshotLocked() else null
        }
        if (snapshot != null) updateListener?.invoke(snapshot)
    }

    fun reportDelivered(index: Int, part: Int, success: Boolean, error: SmsError?) {
        val snapshot = synchronized(lock) {
            if (index !in recipients.indices || part !in 0 until parts[index]) return
            if (deliveredSeen[index][part]) return
            deliveredSeen[index][part] = true
            deliveryReported[index] = true
            if (success) {
                deliveredOk[index]++
            } else {
                deliveryFailed[index]++
                if (deliveryErrors[index] == null) {
                    deliveryErrors[index] = error ?: SmsError.GENERIC_FAILURE
                }
            }
            if (finalized) snapshotLocked() else null
        }
        if (snapshot != null) updateListener?.invoke(snapshot)
    }

    /**
     * Waits until every part has a sent report, or [timeoutMillis] expires, then
     * freezes the result. Late sent/delivery reports still update the snapshot
     * and are forwarded to [updateListener].
     */
    suspend fun awaitSending(timeoutMillis: Long): SmsSnapshot {
        withTimeoutOrNull(timeoutMillis) { allSent.await() }
        return synchronized(lock) {
            finalized = true
            snapshotLocked()
        }
    }

    fun snapshot(): SmsSnapshot = synchronized(lock) { snapshotLocked() }

    private fun completeIfDoneLocked() {
        val done = recipients.indices.all { sentOk[it] + sentFailed[it] >= parts[it] }
        if (done && !allSent.isCompleted) allSent.complete(Unit)
    }

    private fun snapshotLocked(): SmsSnapshot {
        val results = recipients.mapIndexed { index, recipient ->
            val total = parts[index]
            val sent = sentOk[index]
            val failed = sentFailed[index]
            val delivered = deliveredOk[index]
            val deliveryFailures = deliveryFailed[index]
            val state = when {
                failed > 0 -> SmsRecipientState.FAILED
                deliveryFailures > 0 -> SmsRecipientState.NOT_DELIVERED
                delivered == total -> SmsRecipientState.DELIVERED
                sent == total -> SmsRecipientState.SENT
                else -> SmsRecipientState.PENDING
            }
            SmsRecipientResult(
                contactId = recipient.contactId,
                name = recipient.name,
                phoneE164 = recipient.phoneE164,
                state = state,
                partsTotal = total,
                partsSent = sent,
                sentError = sentErrors[index],
                deliveryReportReceived = deliveryReported[index],
                deliveryError = deliveryErrors[index],
            )
        }
        return SmsSnapshot(
            token = token,
            recipients = results,
            sendingComplete = allSent.isCompleted,
        )
    }
}
