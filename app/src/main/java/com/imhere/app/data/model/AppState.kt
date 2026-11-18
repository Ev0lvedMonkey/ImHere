package com.imhere.app.data.model

import kotlinx.serialization.Serializable

/**
 * The complete user data set. It is stored as one JSON document in DataStore,
 * so a change is always written atomically.
 *
 * [sosTemplate] and [plainTemplate] are nullable on purpose: `null` means
 * "use the built-in default for the current language", so a user who never
 * edited a template gets it in the language they switched to.
 *
 * [selectedSubscriptionId] references a SIM subscription id, see Settings 6.3.
 */
@Serializable
data class AppState(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val contacts: List<Contact> = emptyList(),
    val sosTemplate: String? = null,
    val plainTemplate: String? = null,
    val selectedSubscriptionId: Int? = null,
) {

    val sosContacts: List<Contact> get() = contacts.filter { it.isSos }

    /**
     * Repairs data that could arrive from a hand-edited file or an older build:
     * drops blank entries, duplicate numbers and keeps at most one record per number.
     */
    fun sanitized(): AppState {
        val seen = HashSet<String>()
        val cleaned = contacts
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.phoneE164.isNotBlank() }
            .filter { seen.add(it.phoneE164) }
        return copy(
            contacts = cleaned,
            sosTemplate = sosTemplate?.takeIf { it.isNotBlank() },
            plainTemplate = plainTemplate?.takeIf { it.isNotBlank() },
            schemaVersion = CURRENT_SCHEMA_VERSION,
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
