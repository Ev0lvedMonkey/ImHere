package com.imhere.app.sms

/** One active SIM card as reported by [android.telephony.SubscriptionManager]. */
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    /** Carrier or user-defined name; `null` when the system does not expose one. */
    val label: String?,
)

/** Result of querying the SIM list. */
data class SimState(
    val permissionGranted: Boolean = false,
    val sims: List<SimInfo> = emptyList(),
)

/**
 * What the app can do with the current selection.
 *
 * The selected subscription id is stored in DataStore. `null` means the user
 * has not chosen a SIM yet: with exactly one active SIM the app uses it
 * automatically (specification 6.3), with several SIMs the user has to choose.
 */
sealed interface SimAvailability {
    data class Ready(val sim: SimInfo) : SimAvailability
    data object PermissionRequired : SimAvailability
    data object NoSims : SimAvailability
    data class NotSelected(val sims: List<SimInfo>) : SimAvailability
    data class Unavailable(val selectedId: Int, val sims: List<SimInfo>) : SimAvailability
}

fun resolveSimAvailability(selectedId: Int?, state: SimState): SimAvailability {
    if (!state.permissionGranted) return SimAvailability.PermissionRequired
    if (selectedId != null) {
        val selected = state.sims.firstOrNull { it.subscriptionId == selectedId }
        return if (selected != null) {
            SimAvailability.Ready(selected)
        } else {
            SimAvailability.Unavailable(selectedId, state.sims)
        }
    }
    return when (state.sims.size) {
        0 -> SimAvailability.NoSims
        1 -> SimAvailability.Ready(state.sims.single())
        else -> SimAvailability.NotSelected(state.sims)
    }
}
