package com.imhere.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.imhere.app.AppGraph
import com.imhere.app.data.SettingsRepository
import com.imhere.app.data.model.Contact
import com.imhere.app.util.PhoneNumberValidator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ContactValidation {
    data class Valid(val phoneE164: String) : ContactValidation
    data object NameEmpty : ContactValidation
    data object PhoneInvalid : ContactValidation
    data object Duplicate : ContactValidation
}

class ContactsViewModel(
    private val repository: SettingsRepository,
    private val phoneNumbers: PhoneNumberValidator,
) : ViewModel() {

    val contacts: StateFlow<List<Contact>> = repository.state
        .map { it.contacts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun validate(existingId: String?, name: String, phone: String): ContactValidation {
        if (name.isBlank()) return ContactValidation.NameEmpty
        val normalized = phoneNumbers.normalize(phone) ?: return ContactValidation.PhoneInvalid
        val duplicate = repository.state.value.contacts.any { contact ->
            contact.id != existingId && contact.phoneE164 == normalized
        }
        if (duplicate) return ContactValidation.Duplicate
        return ContactValidation.Valid(normalized)
    }

    fun save(existingId: String?, name: String, phoneE164: String, isSos: Boolean) {
        viewModelScope.launch {
            repository.upsertContact(existingId, name.trim(), phoneE164, isSos)
        }
    }

    fun setSos(id: String, isSos: Boolean) {
        viewModelScope.launch { repository.setSos(id, isSos) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteContact(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ContactsViewModel(AppGraph.settings, AppGraph.phoneNumbers) }
        }
    }
}
