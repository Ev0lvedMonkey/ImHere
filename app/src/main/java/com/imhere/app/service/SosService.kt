package com.imhere.app.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.imhere.app.AppGraph
import com.imhere.app.R
import com.imhere.app.data.model.Contact
import com.imhere.app.location.AddressResolver
import com.imhere.app.location.LocationFix
import com.imhere.app.location.LocationOutcome
import com.imhere.app.location.LocationResolver
import com.imhere.app.notifications.NotificationHelper
import com.imhere.app.sms.SimAvailability
import com.imhere.app.sms.SmsRecipient
import com.imhere.app.sms.SmsRejection
import com.imhere.app.sms.SmsSendResult
import com.imhere.app.sms.SmsSnapshot
import com.imhere.app.sms.SmsOperationRegistry
import com.imhere.app.sms.resolveSimAvailability
import com.imhere.app.ui.PrototypeSettings
import com.imhere.app.ui.toResultText
import com.imhere.app.ui.toUserMessage
import com.imhere.app.util.MessageTemplate
import com.imhere.app.util.MessageValues
import com.imhere.app.widget.WidgetRenderer
import com.imhere.app.widget.WidgetStateStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the user asked for: the emergency SOS message or the plain
 * "send my location" message. Both use the same pipeline and the same SOS
 * recipients; only the template differs.
 */
enum class SosMode {
    SOS,
    LOCATION;

    companion object {
        fun fromKey(key: String?): SosMode =
            entries.firstOrNull { it.name == key } ?: SOS
    }
}

/**
 * Foreground service that performs one complete SOS operation:
 * validate permissions, recipients and SIM, acquire a fresh location fix,
 * render the message template and send one SMS per recipient.
 *
 * The service stays in the foreground until every sent report is received (or
 * the dispatch timeout expires). Delivery reports that arrive later update the
 * result notification through [NotificationHelper].
 */
class SosService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentMode = SosMode.SOS

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
        val mode = SosMode.fromKey(intent.getStringExtra(EXTRA_MODE))
        Log.i(TAG, "onStartCommand: source=$source mode=$mode startId=$startId")

        val resolver = LocationResolver(this)
        if (!resolver.hasLocationPermission()) {
            Log.w(TAG, "SOS aborted: location permission is missing")
            abort(startId, getString(R.string.notif_failure_permission), mode)
            return START_NOT_STICKY
        }

        try {
            val notification = NotificationHelper.progress(
                this,
                getString(R.string.notif_preparing),
            )
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
            abort(startId, getString(R.string.notif_start_failed), mode)
            return START_NOT_STICKY
        }

        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "SOS is already running, duplicate start ignored")
            return START_NOT_STICKY
        }

        currentMode = mode
        WidgetStateStore.setOperationActive(this, true)
        WidgetRenderer.updateAll(this)
        acquireWakeLock()

        scope.launch {
            var successful = false
            try {
                successful = runOperation(resolver, mode)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "SOS operation crashed", throwable)
                notifyFailure(getString(R.string.notif_failure_unknown), mode)
            } finally {
                withContext(Dispatchers.Main) { finishOperation(successful) }
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Returns `true` when every recipient was at least handed to the operator.
     * Any explicit send failure makes the operation unsuccessful.
     */
    private suspend fun runOperation(resolver: LocationResolver, mode: SosMode): Boolean {
        val state = AppGraph.settings.current()
        val recipients = state.sosContacts
        if (recipients.isEmpty()) {
            Log.w(TAG, "SOS aborted: no SOS contacts")
            notifyFailure(getString(R.string.notif_no_recipients), mode)
            return false
        }
        if (!isGranted(Manifest.permission.SEND_SMS)) {
            Log.w(TAG, "SOS aborted: SMS permission is missing")
            notifyFailure(SmsRejection.NO_PERMISSION.toUserMessage(this), mode)
            return false
        }

        // Fail fast before waiting for a location fix when SMS cannot be sent.
        val simState = AppGraph.sims.loadFresh()
        val availability = resolveSimAvailability(state.selectedSubscriptionId, simState)
        if (availability !is SimAvailability.Ready) {
            val rejection = when (availability) {
                is SimAvailability.Ready -> error("unreachable")
                SimAvailability.PermissionRequired -> SmsRejection.SIM_PERMISSION
                SimAvailability.NoSims -> SmsRejection.NO_SIM
                is SimAvailability.NotSelected -> SmsRejection.SIM_NOT_SELECTED
                is SimAvailability.Unavailable -> SmsRejection.SIM_UNAVAILABLE
            }
            Log.w(TAG, "SOS aborted: SIM is not usable ($availability)")
            notifyFailure(rejection.toUserMessage(this), mode)
            return false
        }

        val acceptInexact = PrototypeSettings.acceptInexactLocation(this)
        val artificialDelay = PrototypeSettings.artificialDelayMillis(this)
        if (artificialDelay > 0L) {
            Log.i(TAG, "prototype: holding the operation for ${artificialDelay}ms")
            delay(artificialDelay)
        }

        NotificationHelper.updateProgress(this, getString(R.string.notif_locating))
        val outcome = resolver.resolve(acceptInexact)
        val suffix = if (acceptInexact) getString(R.string.notif_debug_suffix) else ""

        when (outcome) {
            is LocationOutcome.Failure -> {
                Log.w(TAG, "SOS location failure: ${outcome.reason}")
                notifyFailure(outcome.reason.toUserMessage(this) + suffix, mode)
                return false
            }

            is LocationOutcome.Success -> {
                val fix = outcome.fix
                Log.i(TAG, "SOS location success: $fix")

                val address = withTimeoutOrNull(ADDRESS_TIMEOUT_MS) {
                    AddressResolver(this@SosService).resolve(fix.latitude, fix.longitude)
                }
                Log.i(TAG, "SOS address: ${address ?: "not available"}")

                val message = buildMessage(mode, state.sosTemplate, state.plainTemplate, fix, address)
                if (message.isBlank()) {
                    Log.w(TAG, "SOS aborted: rendered message is empty")
                    notifyFailure(getString(R.string.sms_rejection_empty_message), mode)
                    return false
                }

                NotificationHelper.updateProgress(this, getString(R.string.notif_sending))
                val notificationText = if (suffix.isBlank()) message else message + "\n" + suffix
                val appContext = applicationContext
                val result = AppGraph.sms.send(
                    selectedSubscriptionId = state.selectedSubscriptionId,
                    recipients = recipients.map { it.toSmsRecipient() },
                    message = message,
                    onUpdate = { snapshot ->
                        // Ignore late reports from an older operation that share
                        // the result notification id with a newer one.
                        if (latestNotificationToken.get() == snapshot.token) {
                            showSmsNotification(
                                context = appContext,
                                snapshot = snapshot,
                                message = notificationText,
                                mode = mode,
                                onlyAlertOnce = true,
                            )
                        }
                    },
                )

                when (result) {
                    is SmsSendResult.Completed -> {
                        val snapshot = result.snapshot
                        latestNotificationToken.set(snapshot.token)
                        // Pick up reports that arrived between the dispatch timeout
                        // and the moment the notification is built.
                        val current = SmsOperationRegistry.snapshot(snapshot.token) ?: snapshot
                        showSmsNotification(
                            context = this,
                            snapshot = current,
                            message = notificationText,
                            mode = mode,
                            onlyAlertOnce = false,
                        )
                        Log.i(
                            TAG,
                            "SOS SMS finished: " +
                                current.recipients.joinToString { "${it.name}=${it.state}" },
                        )
                        return !current.hasFailures
                    }

                    is SmsSendResult.Rejected -> {
                        Log.w(TAG, "SOS SMS rejected: ${result.reason}")
                        notifyFailure(result.reason.toUserMessage(this), mode, notificationText)
                        return false
                    }
                }
            }
        }
    }

    private fun buildMessage(
        mode: SosMode,
        sosTemplate: String?,
        plainTemplate: String?,
        fix: LocationFix,
        address: String?,
    ): String {
        val acquiredAt = System.currentTimeMillis() - fix.ageMillis
        val values = MessageValues.forLocation(
            latitude = fix.latitude,
            longitude = fix.longitude,
            address = address,
            acquiredAtMillis = acquiredAt,
        )
        val template = when (mode) {
            SosMode.SOS -> sosTemplate ?: getString(R.string.sos_template_default)
            SosMode.LOCATION -> plainTemplate ?: getString(R.string.plain_template_default)
        }
        return MessageTemplate.render(template, values)
    }

    private fun notifyFailure(message: String, mode: SosMode, details: String? = null) {
        val text = if (details.isNullOrBlank()) message else details + "\n\n" + message
        NotificationHelper.showResult(this, failureTitle(this, mode), text)
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

    private fun finishOperation(successful: Boolean) {
        Log.i(TAG, "SOS operation finished, successful=$successful mode=$currentMode")
        running.set(false)
        WidgetStateStore.setOperationActive(this, false)
        if (successful && currentMode == SosMode.SOS) {
            WidgetStateStore.startCooldown(this, SOS_COOLDOWN_MS)
        }
        WidgetRenderer.updateAll(this)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun abort(startId: Int, message: String, mode: SosMode) {
        NotificationHelper.showResult(this, failureTitle(this, mode), message)
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

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "ImHerePrototype"
        private const val ACTION_START_SOS = "com.imhere.app.action.START_SOS"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_MODE = "mode"
        private const val WAKE_LOCK_TIMEOUT_MS = 2 * 60_000L

        /** Reverse geocoding is optional and must not hold the flow for long. */
        private const val ADDRESS_TIMEOUT_MS = 2_000L

        /** Extra taps right after a successful SOS operation are ignored. */
        private const val SOS_COOLDOWN_MS = 5_000L

        private val running = AtomicBoolean(false)

        /** Token of the operation that owns the current result notification. */
        private val latestNotificationToken = AtomicReference<String?>(null)

        fun start(context: Context, source: String, mode: SosMode = SosMode.SOS): Boolean = try {
            val intent = Intent(context, SosService::class.java)
                .setAction(ACTION_START_SOS)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_MODE, mode.name)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (throwable: Throwable) {
            Log.e(TAG, "cannot start SOS service", throwable)
            false
        }

        fun isRunning(): Boolean = running.get()
    }
}

private fun Contact.toSmsRecipient(): SmsRecipient =
    SmsRecipient(contactId = id, name = name, phoneE164 = phoneE164)

private fun failureTitle(context: Context, mode: SosMode): String = context.getString(
    if (mode == SosMode.SOS) {
        R.string.notif_failure_title
    } else {
        R.string.notif_failure_title_plain
    },
)

/**
 * Kept outside [SosService] so late delivery reports can update the
 * notification without keeping a destroyed service instance alive.
 */
private fun showSmsNotification(
    context: Context,
    snapshot: SmsSnapshot,
    message: String,
    mode: SosMode,
    onlyAlertOnce: Boolean,
) {
    val title = if (snapshot.hasFailures) {
        failureTitle(context, mode)
    } else {
        context.getString(R.string.notif_sms_sent_title)
    }
    val text = message + "\n\n" + snapshot.toResultText(context)
    NotificationHelper.showResult(context, title, text, onlyAlertOnce)
}
