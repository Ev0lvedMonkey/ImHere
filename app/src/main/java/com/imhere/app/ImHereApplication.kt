package com.imhere.app

import android.app.Application
import android.content.Context
import com.imhere.app.data.AppStateStore
import com.imhere.app.data.SettingsRepository
import com.imhere.app.notifications.NotificationHelper
import com.imhere.app.sms.AndroidSmsTransport
import com.imhere.app.sms.SimManager
import com.imhere.app.sms.SmsDispatcher
import com.imhere.app.util.PhoneNumberValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ImHereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Channels must exist before any component (for example the widget
        // receiver) can post a result notification.
        NotificationHelper.ensureChannels(this)
        AppGraph.init(this)
    }
}

/**
 * Minimal service locator. The application has a single dependency graph and
 * no need for a DI framework.
 */
object AppGraph {

    lateinit var context: Context
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var phoneNumbers: PhoneNumberValidator
        private set

    lateinit var sims: SimManager
        private set

    lateinit var sms: SmsDispatcher
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        this.context = context.applicationContext
        settings = SettingsRepository(AppStateStore.create(this.context, scope), scope)
        phoneNumbers = PhoneNumberValidator(this.context)
        sims = SimManager(this.context, scope)
        sms = SmsDispatcher(
            context = this.context,
            sims = sims,
            phoneNumbers = phoneNumbers,
            transport = AndroidSmsTransport(this.context),
        )
    }
}
