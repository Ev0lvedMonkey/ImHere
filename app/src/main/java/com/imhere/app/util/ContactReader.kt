package com.imhere.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * Reads a name and a phone number for a contact URI returned by the system
 * contact picker. The caller must hold READ_CONTACTS: the picker returns a
 * content URI without a read grant.
 */
object ContactReader {

    data class Picked(val name: String, val phone: String)

    fun read(context: Context, uri: Uri): Picked? = runCatching {
        val contactId = uri.lastPathSegment ?: return null
        readByContactId(context.contentResolver, contactId)
    }.getOrNull()

    private fun readByContactId(resolver: ContentResolver, contactId: String): Picked? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            arrayOf(contactId),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0)
                if (!number.isNullOrBlank()) {
                    return Picked(name = cursor.getString(1).orEmpty(), phone = number)
                }
            }
        }
        return null
    }
}
