package com.imhere.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * Reads a name and a phone number for a contact URI returned by the system
 * contact picker.
 *
 * The picker itself never needs a permission. On most devices the provider also
 * lets the caller read the single picked contact, so no permission prompt is
 * shown at all. If the platform denies the read, [ReadResult.PermissionDenied]
 * tells the caller that READ_CONTACTS is the only way to get the number.
 */
object ContactReader {

    data class Picked(val name: String, val phone: String)

    sealed interface ReadResult {
        data class Success(val picked: Picked) : ReadResult
        data object PermissionDenied : ReadResult
        data object Unavailable : ReadResult
    }

    fun read(context: Context, uri: Uri): ReadResult {
        return try {
            val contactId = uri.lastPathSegment ?: return ReadResult.Unavailable
            readByContactId(context.contentResolver, contactId)
                ?.let { ReadResult.Success(it) }
                ?: ReadResult.Unavailable
        } catch (exception: SecurityException) {
            ReadResult.PermissionDenied
        } catch (exception: Exception) {
            ReadResult.Unavailable
        }
    }

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
