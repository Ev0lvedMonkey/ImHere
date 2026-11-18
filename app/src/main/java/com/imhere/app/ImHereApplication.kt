package com.imhere.app

import android.app.Application
import android.content.Context
import com.imhere.app.data.AppStateStore
import com.imhere.app.data.SettingsRepository
import com.imhere.app.util.PhoneNumberValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ImHereApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}

/**
 * Minimal service locator. The application has a single dependency graph and
 * no need for a DI framework.
 */
object AppGraph {

    private lateinit var applicationContext: Context

    lateinit var context: Context
        private set

    lateinit var settings: SettingsRepository
        private set

    lateinit var phoneNumbers: PhoneNumberValidator
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        this.context = context.applicationContext
        settings = SettingsRepository(AppStateStore.create(this.context, scope), scope)
        phoneNumbers = PhoneNumberValidator(this.context)
    }
}
