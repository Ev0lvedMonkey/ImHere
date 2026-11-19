package com.imhere.app.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.imhere.app.AppGraph
import com.imhere.app.data.SettingsRepository
import com.imhere.app.data.model.AppState
import com.imhere.app.location.LocationResolver
import com.imhere.app.sms.SimManager
import com.imhere.app.sms.SimState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LocationAvailability { AVAILABLE, APPROXIMATE_ONLY, DISABLED, NO_PERMISSION }

class MainViewModel(
    private val context: Context,
    private val repository: SettingsRepository,
    private val sims: SimManager,
) : ViewModel() {

    val state: StateFlow<AppState> = repository.state
    val simState: StateFlow<SimState> = sims.state

    private val _location = MutableStateFlow(LocationAvailability.AVAILABLE)
    val location: StateFlow<LocationAvailability> = _location.asStateFlow()

    fun refresh() {
        val resolver = LocationResolver(context)
        _location.value = when {
            !resolver.hasLocationPermission() -> LocationAvailability.NO_PERMISSION
            !resolver.isLocationEnabled() -> LocationAvailability.DISABLED
            !resolver.hasFinePermission() -> LocationAvailability.APPROXIMATE_ONLY
            else -> LocationAvailability.AVAILABLE
        }
        sims.refresh()
    }

    fun refreshSims() = sims.refresh()

    fun selectSim(subscriptionId: Int) {
        viewModelScope.launch { repository.setSelectedSubscriptionId(subscriptionId) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MainViewModel(AppGraph.context, AppGraph.settings, AppGraph.sims) }
        }
    }
}
