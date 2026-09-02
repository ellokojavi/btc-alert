package com.irigoyen.btcalert.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms polling after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.apply(context)
    }
}
