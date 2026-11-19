package com.imhere.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.imhere.app.AppGraph
import com.imhere.app.data.SettingsRepository
import com.imhere.app.data.model.AppState
import com.imhere.app.sms.SimManager
import com.imhere.app.sms.SimState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val sims: SimManager,
) : ViewModel() {

    val state: StateFlow<AppState> = repository.state
    val simState: StateFlow<SimState> = sims.state

    fun setSosTemplate(template: String?) {
        viewModelScope.launch { repository.setSosTemplate(template) }
    }

    fun setPlainTemplate(template: String?) {
        viewModelScope.launch { repository.setPlainTemplate(template) }
    }

    fun setSos(id: String, isSos: Boolean) {
        viewModelScope.launch { repository.setSos(id, isSos) }
    }

    fun refreshSims() = sims.refresh()

    fun selectSim(subscriptionId: Int) {
        viewModelScope.launch { repository.setSelectedSubscriptionId(subscriptionId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(AppGraph.settings, AppGraph.sims) }
        }
    }
}
