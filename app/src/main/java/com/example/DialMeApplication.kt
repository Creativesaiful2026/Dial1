package com.example

import android.app.Application
import com.example.core.telecom.CallNotificationManager

class DialMeApplication : Application() {
    lateinit var container: DialMeAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.ensureChannels(this)
        container = DialMeAppContainer(this)
    }
}
