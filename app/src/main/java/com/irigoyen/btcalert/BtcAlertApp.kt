package com.irigoyen.btcalert

import android.app.Application
import androidx.work.Configuration
import com.irigoyen.btcalert.notify.Notifier

class BtcAlertApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
