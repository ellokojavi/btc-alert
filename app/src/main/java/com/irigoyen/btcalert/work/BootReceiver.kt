package com.irigoyen.btcalert.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms polling after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Both actions are protected broadcasts only the system can send, but check anyway rather
        // than re-arming polling for whatever else might arrive here.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Scheduler.apply(context)
    }
}
