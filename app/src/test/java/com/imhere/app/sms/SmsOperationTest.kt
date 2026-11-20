package com.imhere.app.sms

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsOperationTest {

    private fun recipient(name: String, phone: String = "+79160000000") =
        SmsRecipient(contactId = null, name = name, phoneE164 = phone)

    private fun operation(
        recipientCount: Int,
        parts: Int = 1,
        onUpdate: ((SmsSnapshot) -> Unit)? = null,
    ) = SmsOperation(
        token = "token",
        recipients = List(recipientCount) { recipient("Recipient $it") },
        partsPerRecipient = List(recipientCount) { parts },
        updateListener = onUpdate,
    )

    @Test
    fun `all parts accepted means sent to operator`() = runBlocking {
        val operation = operation(recipientCount = 1, parts = 2)
        operation.reportSent(index = 0, part = 0, success = true, error = null)
        operation.reportSent(index = 0, part = 1, success = true, error = null)

        val snapshot = operation.awaitSending(0)

        assertEquals(SmsRecipientState.SENT, snapshot.recipients.single().state)
        assertTrue(snapshot.sendingComplete)
        assertFalse(snapshot.hasFailures)
        assertFalse(snapshot.hasPending)
        assertFalse(snapshot.allDelivered)
    }

    @Test
    fun `one failed part fails the whole recipient but not the others`() = runBlocking {
        val operation = operation(recipientCount = 2)
        operation.reportSent(index = 0, part = 0, success = false, error = SmsError.NO_SERVICE)
        operation.reportSent(index = 1, part = 0, success = true, error = null)

        val snapshot = operation.awaitSending(0)

        assertEquals(SmsRecipientState.FAILED, snapshot.recipients[0].state)
        assertEquals(SmsError.NO_SERVICE, snapshot.recipients[0].sentError)
        assertEquals(SmsRecipientState.SENT, snapshot.recipients[1].state)
        assertTrue(snapshot.hasFailures)
    }

    @Test
    fun `unsendable recipient fails without any report`() = runBlocking {
        val operation = operation(recipientCount = 1)
        operation.reportUnsendable(index = 0, error = SmsError.INVALID_NUMBER)

        val snapshot = operation.awaitSending(0)

        assertEquals(SmsRecipientState.FAILED, snapshot.recipients.single().state)
        assertEquals(SmsError.INVALID_NUMBER, snapshot.recipients.single().sentError)
    }

    @Test
    fun `delivery reports move a sent recipient to delivered`() = runBlocking {
        val operation = operation(recipientCount = 1, parts = 2)
        operation.reportSent(index = 0, part = 0, success = true, error = null)
        operation.reportSent(index = 0, part = 1, success = true, error = null)
        operation.reportDelivered(index = 0, part = 0, success = true, error = null)
        operation.reportDelivered(index = 0, part = 1, success = true, error = null)

        val snapshot = operation.snapshot()

        assertEquals(SmsRecipientState.DELIVERED, snapshot.recipients.single().state)
        assertTrue(snapshot.allDelivered)
        assertTrue(snapshot.recipients.single().deliveryReportReceived)
    }

    @Test
    fun `failed delivery is reported separately from sending`() = runBlocking {
        val operation = operation(recipientCount = 1)
        operation.reportSent(index = 0, part = 0, success = true, error = null)
        operation.reportDelivered(index = 0, part = 0, success = false, error = SmsError.GENERIC_FAILURE)

        val snapshot = operation.snapshot()

        assertEquals(SmsRecipientState.NOT_DELIVERED, snapshot.recipients.single().state)
        assertNull(snapshot.recipients.single().sentError)
    }

    @Test
    fun `timeout freezes a pending recipient`() = runBlocking {
        val operation = operation(recipientCount = 1, parts = 2)
        operation.reportSent(index = 0, part = 0, success = true, error = null)

        val snapshot = operation.awaitSending(0)

        assertEquals(SmsRecipientState.PENDING, snapshot.recipients.single().state)
        assertEquals(1, snapshot.recipients.single().partsSent)
        assertEquals(2, snapshot.recipients.single().partsTotal)
        assertFalse(snapshot.sendingComplete)
        assertTrue(snapshot.hasPending)
        assertFalse(snapshot.hasFailures)
    }

    @Test
    fun `late reports are forwarded to the update listener`() = runBlocking {
        val updates = mutableListOf<SmsSnapshot>()
        val operation = operation(recipientCount = 1) { updates += it }
        operation.reportSent(index = 0, part = 0, success = true, error = null)
        operation.awaitSending(0)

        operation.reportDelivered(index = 0, part = 0, success = true, error = null)

        assertEquals(1, updates.size)
        assertEquals(SmsRecipientState.DELIVERED, updates.single().recipients.single().state)
    }

    @Test
    fun `duplicate reports are ignored`() = runBlocking {
        val operation = operation(recipientCount = 1)
        operation.reportSent(index = 0, part = 0, success = false, error = SmsError.GENERIC_FAILURE)
        operation.reportSent(index = 0, part = 0, success = true, error = null)

        val snapshot = operation.awaitSending(0)

        assertEquals(SmsRecipientState.FAILED, snapshot.recipients.single().state)
        assertEquals(0, snapshot.recipients.single().partsSent)
    }
}
