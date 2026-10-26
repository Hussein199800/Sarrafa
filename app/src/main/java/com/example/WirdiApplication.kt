package com.example

import android.app.Application
import android.content.Context
import com.example.data.AssetsContentRepository
import com.example.data.CompassAndLocationHelper
import com.example.data.WirdiPreferencesRepository
import com.example.notifications.WirdiNotificationScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val assetsRepository: AssetsContentRepository by lazy {
        AssetsContentRepository(appContext)
    }

    val preferencesRepository: WirdiPreferencesRepository by lazy {
        WirdiPreferencesRepository(appContext)
    }

    val compassAndLocationHelper: CompassAndLocationHelper by lazy {
        CompassAndLocationHelper(appContext)
    }

    val notificationScheduler: WirdiNotificationScheduler by lazy {
        WirdiNotificationScheduler(appContext)
    }
}

class WirdiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationScheduler.createNotificationChannels()
        container.notificationScheduler.enqueueDailyPreventiveWork()
    }
}
