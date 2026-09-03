package com.irigoyen.btcalert.work

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.irigoyen.btcalert.data.PriceChecker
import com.irigoyen.btcalert.data.Store
import com.irigoyen.btcalert.model.usd2
import com.irigoyen.btcalert.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Real-time mode: a foreground service that polls on a fixed interval. */
class PriceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifier.ensureChannels(this)
        startForeground(
            Notifier.SERVICE_NOTIFICATION_ID,
            Notifier.serviceNotification(this, statusText()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (loop?.isActive != true) startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "btcalert:poll").apply { acquire() }
        loop = scope.launch {
            while (isActive) {
                PriceChecker.runOnce(this@PriceService)
                // Always refresh the notification, including after a failure: a persistent
                // notification showing a price with no hint that it's hours stale is worse
                // than no notification at all.
                Notifier.safeNotify(
                    this@PriceService,
                    Notifier.SERVICE_NOTIFICATION_ID,
                    Notifier.serviceNotification(this@PriceService, statusText()),
                )
                val sec = Store.get(this@PriceService).state.value.settings.realtimeIntervalSec.coerceIn(15, 3600)
                delay(sec * 1000L)
            }
        }
    }

    /** Live price, or the last one plus why it stopped moving. */
    private fun statusText(): String {
        val state = Store.get(this).state.value
        val price = state.history.lastOrNull()?.let { "BTC ${usd2(it.price)}" }
        val err = state.lastFetchError ?: return price ?: "BTC monitor starting…"
        val reason = if (err.kind.isConnectivity) "no connection" else "prices unavailable"
        return if (price != null) "$price · $reason" else "BTC monitor · $reason"
    }

    override fun onDestroy() {
        loop?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
