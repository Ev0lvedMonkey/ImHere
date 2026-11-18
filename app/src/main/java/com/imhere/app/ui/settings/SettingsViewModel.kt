package com.imhere.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.imhere.app.AppGraph
import com.imhere.app.data.SettingsRepository
import com.imhere.app.data.model.AppState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<AppState> = repository.state

    fun setSosTemplate(template: String?) {
        viewModelScope.launch { repository.setSosTemplate(template) }
    }

    fun setPlainTemplate(template: String?) {
        viewModelScope.launch { repository.setPlainTemplate(template) }
    }

    fun setSos(id: String, isSos: Boolean) {
        viewModelScope.launch { repository.setSos(id, isSos) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(AppGraph.settings) }
        }
    }
}
