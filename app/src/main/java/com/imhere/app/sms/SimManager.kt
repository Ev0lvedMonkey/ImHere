package com.imhere.app.sms

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads the list of active SIM cards.
 *
 * `READ_PHONE_STATE` is the minimum permission the platform requires to see
 * subscription ids and slots. Phone numbers are deliberately not read: the app
 * only needs to know which SIM to use, so `READ_PHONE_NUMBERS`/`READ_SMS` stay
 * out of the manifest.
 */
class SimManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(SimState(permissionGranted = hasPermission()))
    val state: StateFlow<SimState> = _state.asStateFlow()

    fun hasPermission(): Boolean = isGranted(Manifest.permission.READ_PHONE_STATE)

    /** Re-reads the SIM list on a background thread and publishes the result. */
    fun refresh() {
        scope.launch {
            val next = withContext(Dispatchers.IO) { load() }
            _state.value = next
            Log.i(
                TAG,
                "SIM state: permission=${next.permissionGranted}, " +
                    "sims=${next.sims.map { "${it.subscriptionId}:${it.slotIndex}:${it.label}" }}",
            )
        }
    }

    /** Suspending variant used right before sending SMS, so the check is fresh. */
    suspend fun loadFresh(): SimState = withContext(Dispatchers.IO) {
        load().also { next ->
            _state.value = next
            Log.i(
                TAG,
                "SIM state (fresh): permission=${next.permissionGranted}, " +
                    "sims=${next.sims.map { it.subscriptionId }}",
            )
        }
    }

    @SuppressLint("MissingPermission") // Guarded by hasPermission() below.
    private fun load(): SimState {
        if (!hasPermission()) return SimState(permissionGranted = false)
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
                ?: return SimState(permissionGranted = true)
            val active = manager.activeSubscriptionInfoList.orEmpty()
            SimState(
                permissionGranted = true,
                sims = active
                    .map { it.toSimInfo() }
                    .sortedWith(compareBy({ it.slotIndex }, { it.subscriptionId })),
            )
        } catch (security: SecurityException) {
            Log.w(TAG, "SIM list is not accessible", security)
            SimState(permissionGranted = false)
        } catch (throwable: Throwable) {
            Log.w(TAG, "SIM list query failed", throwable)
            SimState(permissionGranted = hasPermission())
        }
    }

    private fun SubscriptionInfo.toSimInfo(): SimInfo {
        val name = listOfNotNull(displayName?.toString(), carrierName?.toString())
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
        return SimInfo(
            subscriptionId = subscriptionId,
            slotIndex = simSlotIndex.coerceAtLeast(0),
            label = name,
        )
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val TAG = "ImHereSms"
    }
}
