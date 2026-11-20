package com.imhere.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.imhere.app.AppGraph
import com.imhere.app.R
import com.imhere.app.location.AddressResolver
import com.imhere.app.location.LocationOutcome
import com.imhere.app.location.LocationResolver
import com.imhere.app.service.SosService
import com.imhere.app.sms.AndroidSmsTransport
import com.imhere.app.sms.DryRunSmsTransport
import com.imhere.app.sms.SmsDispatcher
import com.imhere.app.sms.SmsRecipient
import com.imhere.app.sms.SmsSendResult
import com.imhere.app.util.MapLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class PrototypeActivity : ComponentActivity() {

    private lateinit var statusView: TextView
    private lateinit var openSettingsButton: Button
    private lateinit var deniedHint: TextView
    private lateinit var locationResultView: TextView
    private lateinit var smsRecipientView: EditText
    private lateinit var smsDryRunView: CheckBox
    private lateinit var smsResultView: TextView
    private var locationCheckRunning = false
    private var smsTestRunning = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
        }

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            updateStatus()
            if (results.values.all { it }) sendTestSms()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prototype)

        statusView = findViewById(R.id.tv_status)
        openSettingsButton = findViewById(R.id.btn_open_settings)
        deniedHint = findViewById(R.id.tv_denied_hint)
        locationResultView = findViewById(R.id.tv_location_result)
        smsRecipientView = findViewById(R.id.et_sms_recipient)
        smsDryRunView = findViewById(R.id.cb_sms_dry_run)
        smsResultView = findViewById(R.id.tv_sms_result)
        val grantButton = findViewById<Button>(R.id.btn_grant)
        val testButton = findViewById<Button>(R.id.btn_test)
        val checkLocationButton = findViewById<Button>(R.id.btn_check_location)
        val sendSmsButton = findViewById<Button>(R.id.btn_send_test_sms)

        grantButton.setOnClickListener { requestPermissions() }

        testButton.setOnClickListener {
            val resolver = LocationResolver(this)
            if (!resolver.hasLocationPermission()) {
                Toast.makeText(this, R.string.notif_failure_permission, Toast.LENGTH_LONG).show()
                requestPermissions()
                return@setOnClickListener
            }
            if (!isGranted(Manifest.permission.SEND_SMS) ||
                !isGranted(Manifest.permission.READ_PHONE_STATE)
            ) {
                Toast.makeText(this, R.string.prototype_sms_no_permission, Toast.LENGTH_LONG).show()
                requestPermissions()
                return@setOnClickListener
            }
            if (SosService.isRunning()) {
                Toast.makeText(this, R.string.notif_already_running, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!SosService.start(this, source = "activity")) {
                Toast.makeText(this, R.string.notif_start_failed, Toast.LENGTH_LONG).show()
            }
        }

        checkLocationButton.setOnClickListener { runLocationCheck() }
        sendSmsButton.setOnClickListener { sendTestSms() }

        openSettingsButton.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun requestPermissions() {
        PrototypeSettings.setPermissionsRequested(this, true)
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun runLocationCheck() {
        val resolver = LocationResolver(this)
        if (!resolver.hasLocationPermission()) {
            Toast.makeText(this, R.string.notif_failure_permission, Toast.LENGTH_LONG).show()
            requestPermissions()
            return
        }
        if (locationCheckRunning) {
            Toast.makeText(this, R.string.notif_already_running, Toast.LENGTH_SHORT).show()
            return
        }
        locationCheckRunning = true
        locationResultView.isVisible = true
        locationResultView.text = getString(R.string.prototype_location_running)

        lifecycleScope.launch {
            try {
                val outcome = resolver.resolve()
                val text = when (outcome) {
                    is LocationOutcome.Success -> {
                        val fix = outcome.fix
                        val address = withTimeoutOrNull(ADDRESS_TIMEOUT_MS) {
                            AddressResolver(this@PrototypeActivity)
                                .resolve(fix.latitude, fix.longitude)
                        }
                        getString(
                            R.string.prototype_location_success,
                            fix.latitude,
                            fix.longitude,
                            fix.accuracyMeters,
                            fix.ageMillis / 1_000L,
                            fix.provider,
                            address ?: getString(R.string.prototype_location_no_address),
                            MapLinks.forCoordinates(fix.latitude, fix.longitude),
                        )
                    }

                    is LocationOutcome.Failure -> getString(
                        R.string.prototype_location_failure,
                        outcome.reason.toUserMessage(this@PrototypeActivity),
                    )
                }
                Log.i(LOG_TAG, "location check finished: ${outcome::class.simpleName}")
                locationResultView.text = text
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(LOG_TAG, "location check failed", throwable)
                locationResultView.text = getString(
                    R.string.prototype_location_failure,
                    getString(R.string.notif_failure_unknown),
                )
            } finally {
                locationCheckRunning = false
            }
        }
    }

    private fun sendTestSms() {
        if (smsTestRunning) {
            Toast.makeText(this, R.string.notif_already_running, Toast.LENGTH_SHORT).show()
            return
        }
        val e164 = AppGraph.phoneNumbers.normalize(smsRecipientView.text.toString())
        if (e164 == null) {
            Toast.makeText(this, R.string.prototype_sms_invalid_number, Toast.LENGTH_LONG).show()
            return
        }

        val required = listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
        val missing = required.filterNot { isGranted(it) }
        if (missing.isNotEmpty()) {
            Toast.makeText(this, R.string.prototype_sms_no_permission, Toast.LENGTH_LONG).show()
            smsPermissionLauncher.launch(missing.toTypedArray())
            return
        }

        smsTestRunning = true
        smsResultView.isVisible = true
        smsResultView.text = getString(R.string.prototype_sms_running)
        val dryRun = smsDryRunView.isChecked

        lifecycleScope.launch {
            try {
                val transport = if (dryRun) {
                    DryRunSmsTransport(this@PrototypeActivity, lifecycleScope)
                } else {
                    AndroidSmsTransport(this@PrototypeActivity)
                }
                val dispatcher = SmsDispatcher(
                    context = this@PrototypeActivity,
                    sims = AppGraph.sims,
                    phoneNumbers = AppGraph.phoneNumbers,
                    transport = transport,
                )
                val result = dispatcher.send(
                    selectedSubscriptionId = AppGraph.settings.state.value.selectedSubscriptionId,
                    recipients = listOf(
                        SmsRecipient(contactId = null, name = e164, phoneE164 = e164),
                    ),
                    message = getString(R.string.prototype_sms_test_message),
                    onUpdate = { snapshot ->
                        runOnUiThread {
                            smsResultView.text = snapshot.toResultText(this@PrototypeActivity)
                        }
                    },
                )
                val text = when (result) {
                    is SmsSendResult.Completed ->
                        result.snapshot.toResultText(this@PrototypeActivity)

                    is SmsSendResult.Rejected ->
                        result.reason.toUserMessage(this@PrototypeActivity)
                }
                smsResultView.text = text
                Log.i(LOG_TAG, "SMS test finished: ${result::class.simpleName}")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(LOG_TAG, "SMS test failed", throwable)
                smsResultView.text = getString(
                    R.string.prototype_location_failure,
                    getString(R.string.notif_failure_unknown),
                )
            } finally {
                smsTestRunning = false
            }
        }
    }

    private fun updateStatus() {
        val resolver = LocationResolver(this)
        val locationGranted = resolver.hasLocationPermission()
        val fineGranted = resolver.hasFinePermission()
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(Manifest.permission.POST_NOTIFICATIONS)
        val smsGranted = isGranted(Manifest.permission.SEND_SMS)
        val simGranted = isGranted(Manifest.permission.READ_PHONE_STATE)

        val locationStatus = if (locationGranted) {
            getString(R.string.prototype_status_granted)
        } else {
            getString(R.string.prototype_status_denied)
        }
        val fineStatus = when {
            fineGranted -> getString(R.string.prototype_status_granted)
            locationGranted -> getString(R.string.prototype_status_approximate)
            else -> getString(R.string.prototype_status_denied)
        }
        val notificationStatus = if (notificationsGranted) {
            getString(R.string.prototype_status_granted)
        } else {
            getString(R.string.prototype_status_denied)
        }
        val servicesStatus = if (resolver.isLocationEnabled()) {
            getString(R.string.prototype_status_enabled)
        } else {
            getString(R.string.prototype_status_disabled)
        }
        val playServicesStatus = if (resolver.isPlayServicesAvailable()) {
            getString(R.string.prototype_status_available)
        } else {
            getString(R.string.prototype_status_unavailable)
        }

        statusView.text = getString(
            R.string.prototype_permissions_status,
            locationStatus,
            fineStatus,
            notificationStatus,
            if (smsGranted) {
                getString(R.string.prototype_status_granted)
            } else {
                getString(R.string.prototype_status_denied)
            },
            if (simGranted) {
                getString(R.string.prototype_status_granted)
            } else {
                getString(R.string.prototype_status_denied)
            },
            servicesStatus,
            playServicesStatus,
        )

        val showSettings = !locationGranted
        openSettingsButton.isVisible = showSettings
        deniedHint.isVisible = showSettings &&
            PrototypeSettings.permissionsRequested(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        private const val LOG_TAG = "ImHerePrototype"
        private const val ADDRESS_TIMEOUT_MS = 2_000L
    }
}
