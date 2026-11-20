package com.imhere.app.data

import androidx.datastore.core.DataStore
import com.imhere.app.data.model.AppState
import com.imhere.app.data.model.Contact
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for the user data. All writes go through [update] so
 * the whole document is replaced atomically and never observed half-written.
 */
class SettingsRepository(
    private val dataStore: DataStore<AppState>,
    scope: CoroutineScope,
) {

    val state: StateFlow<AppState> = dataStore.data
        .catch { throwable ->
            // A read error must not kill the app: fall back to defaults and let
            // the next write repair the file.
            if (throwable is IOException) emit(AppState()) else throw throwable
        }
        .stateIn(scope, SharingStarted.Eagerly, AppState())

    suspend fun update(transform: (AppState) -> AppState) {
        dataStore.updateData { current -> transform(current).sanitized() }
    }

    /**
     * Reads the latest saved state directly from the store. Used by the SOS
     * service, where the cached [state] may still hold the initial default
     * value right after a cold start from the widget.
     */
    suspend fun current(): AppState = try {
        dataStore.data.first().sanitized()
    } catch (io: IOException) {
        AppState()
    }

    /** Adds a contact or replaces an existing one, keeping list order stable. */
    suspend fun upsertContact(
        existingId: String?,
        name: String,
        phoneE164: String,
        isSos: Boolean,
    ) {
        update { current ->
            // The uniqueness check in the UI reads a cached state. Enforce it
            // again inside the atomic update so a concurrent save cannot leave
            // two contacts with the same number (and thus duplicate SOS SMS).
            val withoutDuplicate = current.contacts.filterNot { contact ->
                contact.id != existingId && contact.phoneE164 == phoneE164
            }
            val contacts = if (existingId == null) {
                withoutDuplicate + Contact(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    phoneE164 = phoneE164,
                    isSos = isSos,
                )
            } else {
                withoutDuplicate.map { contact ->
                    if (contact.id == existingId) {
                        contact.copy(name = name, phoneE164 = phoneE164, isSos = isSos)
                    } else {
                        contact
                    }
                }
            }
            current.copy(contacts = contacts)
        }
    }

    suspend fun deleteContact(id: String) {
        update { current -> current.copy(contacts = current.contacts.filterNot { it.id == id }) }
    }

    suspend fun setSos(id: String, isSos: Boolean) {
        update { current ->
            current.copy(
                contacts = current.contacts.map { contact ->
                    if (contact.id == id) contact.copy(isSos = isSos) else contact
                },
            )
        }
    }

    suspend fun setSosTemplate(template: String?) {
        update { current -> current.copy(sosTemplate = template) }
    }

    suspend fun setPlainTemplate(template: String?) {
        update { current -> current.copy(plainTemplate = template) }
    }

    suspend fun setSelectedSubscriptionId(subscriptionId: Int?) {
        update { current -> current.copy(selectedSubscriptionId = subscriptionId) }
    }
}
