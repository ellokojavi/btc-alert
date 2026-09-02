package com.irigoyen.btcalert.work

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.irigoyen.btcalert.data.Store
import com.irigoyen.btcalert.model.PollMode
import java.util.concurrent.TimeUnit

/** Turns the chosen [PollMode] into the right background mechanism, and tears down the other. */
object Scheduler {
    private const val PERIODIC = "price-check-periodic"
    private const val ONESHOT = "price-check-now"

    fun apply(context: Context) {
        val mode = Store.get(context).state.value.settings.pollMode
        val wm = WorkManager.getInstance(context)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val svc = Intent(context, PriceService::class.java)
        when (mode) {
            PollMode.BATTERY_SAVER -> {
                context.stopService(svc)
                val req = PeriodicWorkRequestBuilder<PriceCheckWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(net).build()
                wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
            }
            PollMode.REALTIME -> {
                wm.cancelUniqueWork(PERIODIC)
                context.startForegroundService(svc)
            }
        }
    }

    /** Immediate one-off check (used by the "Check now" button and on first launch). */
    fun checkNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<PriceCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONESHOT, ExistingWorkPolicy.REPLACE, req)
    }
}
