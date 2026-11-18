package com.imhere.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.imhere.app.R
import com.imhere.app.location.LocationResolver
import com.imhere.app.service.SosService

class PrototypeActivity : ComponentActivity() {

    private lateinit var statusView: TextView
    private lateinit var openSettingsButton: Button
    private lateinit var deniedHint: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prototype)

        statusView = findViewById(R.id.tv_status)
        openSettingsButton = findViewById(R.id.btn_open_settings)
        deniedHint = findViewById(R.id.tv_denied_hint)
        val grantButton = findViewById<Button>(R.id.btn_grant)
        val testButton = findViewById<Button>(R.id.btn_test)
        val debugCheckBox = findViewById<CheckBox>(R.id.cb_debug_inexact)

        debugCheckBox.isChecked = PrototypeSettings.acceptInexactLocation(this)
        debugCheckBox.setOnCheckedChangeListener { _, checked ->
            PrototypeSettings.setAcceptInexactLocation(this, checked)
        }

        grantButton.setOnClickListener { requestPermissions() }

        testButton.setOnClickListener {
            val resolver = LocationResolver(this)
            if (!resolver.hasLocationPermission()) {
                Toast.makeText(this, R.string.notif_failure_permission, Toast.LENGTH_LONG).show()
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
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun updateStatus() {
        val resolver = LocationResolver(this)
        val locationGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(Manifest.permission.POST_NOTIFICATIONS)

        val locationStatus = if (locationGranted) {
            getString(R.string.prototype_status_granted)
        } else {
            getString(R.string.prototype_status_denied)
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
            notificationStatus,
            servicesStatus,
            playServicesStatus,
        )

        val showSettings = !locationGranted
        openSettingsButton.isVisible = showSettings
        deniedHint.isVisible = showSettings
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
