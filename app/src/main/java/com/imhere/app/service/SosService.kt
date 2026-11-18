package com.imhere.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.imhere.app.R
import com.imhere.app.location.LocationFailure
import com.imhere.app.location.LocationOutcome
import com.imhere.app.location.LocationResolver
import com.imhere.app.notifications.NotificationHelper
import com.imhere.app.ui.PrototypeSettings
import com.imhere.app.widget.WidgetRenderer
import com.imhere.app.widget.WidgetStateStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that performs the location part of the SOS flow.
 *
 * The prototype deliberately does not send SMS yet. It verifies that the service
 * can be started from the widget without opening an Activity, acquires a foreground
 * location fix and reports the result through a notification.
 */
class SosService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_SOS) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val source = intent.getStringExtra(EXTRA_SOURCE) ?: "unknown"
        Log.i(TAG, "onStartCommand: source=$source, startId=$startId")

        val resolver = LocationResolver(this)
        if (!resolver.hasLocationPermission()) {
            Log.w(TAG, "SOS aborted: location permission is missing")
            abort(startId, getString(R.string.notif_failure_permission))
            return START_NOT_STICKY
        }

        try {
            val notification = NotificationHelper.progress(this, getString(R.string.notif_locating))
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NotificationHelper.PROGRESS_NOTIFICATION_ID,
                notification,
                type,
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "startForeground failed", throwable)
            abort(startId, getString(R.string.notif_start_failed))
            return START_NOT_STICKY
        }

        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "SOS is already running, duplicate start ignored")
            return START_NOT_STICKY
        }

        WidgetStateStore.setOperationActive(this, true)
        WidgetRenderer.updateAll(this)
        acquireWakeLock()

        scope.launch {
            var shown = false
            try {
                val acceptInexact = PrototypeSettings.acceptInexactLocation(this@SosService)
                val artificialDelay = PrototypeSettings.artificialDelayMillis(this@SosService)
                if (artificialDelay > 0L) {
                    Log.i(TAG, "prototype: holding the operation for ${artificialDelay}ms")
                    delay(artificialDelay)
                }
                val outcome = resolver.resolve(acceptInexact)
                val suffix = if (acceptInexact) getString(R.string.notif_debug_suffix) else ""

                when (outcome) {
                    is LocationOutcome.Success -> {
                        val fix = outcome.fix
                        Log.i(TAG, "SOS location success: $fix")
                        val text = getString(
                            R.string.notif_success_text,
                            fix.latitude,
                            fix.longitude,
                            fix.accuracyMeters,
                            fix.ageMillis / 1_000L,
                            fix.provider,
                        ) + suffix
                        NotificationHelper.showResult(
                            this@SosService,
                            getString(R.string.notif_success_title),
                            text,
                        )
                        shown = true
                    }

                    is LocationOutcome.Failure -> {
                        Log.w(TAG, "SOS location failure: ${outcome.reason}")
                        val text = failureText(outcome.reason) + suffix
                        NotificationHelper.showResult(
                            this@SosService,
                            getString(R.string.notif_failure_title),
                            text,
                        )
                        shown = true
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "SOS operation crashed", throwable)
                if (!shown) {
                    NotificationHelper.showResult(
                        this@SosService,
                        getString(R.string.notif_failure_title),
                        getString(R.string.notif_failure_unknown),
                    )
                }
            } finally {
                withContext(Dispatchers.Main) { finishOperation() }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        scope.cancel()
        releaseWakeLock()
        if (running.compareAndSet(true, false)) {
            WidgetStateStore.setOperationActive(this, false)
            WidgetRenderer.updateAll(this)
        }
        super.onDestroy()
    }

    private fun finishOperation() {
        Log.i(TAG, "SOS operation finished")
        running.set(false)
        WidgetStateStore.setOperationActive(this, false)
        WidgetRenderer.updateAll(this)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun abort(startId: Int, message: String) {
        NotificationHelper.showResult(
            this,
            getString(R.string.notif_failure_title),
            message,
        )
        WidgetStateStore.setOperationActive(this, false)
        WidgetRenderer.updateAll(this)
        releaseWakeLock()
        stopSelf(startId)
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val manager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ImHere:sos").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        Log.i(TAG, "partial wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun failureText(reason: LocationFailure): String = when (reason) {
        LocationFailure.PermissionDenied -> getString(R.string.notif_failure_permission)
        LocationFailure.LocationDisabled -> getString(R.string.notif_failure_disabled)
        LocationFailure.Timeout -> getString(R.string.notif_failure_timeout)
        LocationFailure.NoFix -> getString(R.string.notif_failure_no_fix)
        LocationFailure.Stale -> getString(R.string.notif_failure_stale)
        is LocationFailure.LowAccuracy -> reason.bestAccuracyMeters?.let { accuracy ->
            getString(R.string.notif_failure_low_accuracy, accuracy)
        } ?: getString(R.string.notif_failure_low_accuracy_unknown)
    }

    companion object {
        private const val TAG = "ImHerePrototype"
        private const val ACTION_START_SOS = "com.imhere.app.action.START_SOS"
        private const val EXTRA_SOURCE = "source"
        private const val WAKE_LOCK_TIMEOUT_MS = 2 * 60_000L

        private val running = AtomicBoolean(false)

        fun start(context: Context, source: String): Boolean = try {
            val intent = Intent(context, SosService::class.java)
                .setAction(ACTION_START_SOS)
                .putExtra(EXTRA_SOURCE, source)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (throwable: Throwable) {
            Log.e(TAG, "cannot start SOS service", throwable)
            false
        }

        fun isRunning(): Boolean = running.get()
    }
}
