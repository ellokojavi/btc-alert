package com.irigoyen.btcalert.data

import android.content.Context
import com.irigoyen.btcalert.engine.AlertEngine
import com.irigoyen.btcalert.model.PriceSample
import com.irigoyen.btcalert.notify.Notifier
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The one code path both polling modes call: fetch → evaluate → notify → persist.
 * Returns the fetched sample, or null if every source failed (error is recorded in state).
 */
object PriceChecker {

    private val logFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
    /** Throttles retries of failed historical lookups (in-memory; resets with the process). */
    private val lastHistoricalAttempt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun runOnce(context: Context): PriceSample? {
        val store = Store.get(context)
        val sample = try {
            PriceFetcher.fetch()
        } catch (e: Exception) {
            store.update { it.copy(lastError = "${logFmt.format(Date())}: ${e.message}") }
            return null
        }

        val before = store.state.value
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val quiet = before.settings.quietHoursEnabled &&
            AlertEngine.isInQuietHours(hour, before.settings.quietStartHour, before.settings.quietEndHour)

        val result = AlertEngine.evaluate(before.rules, before.ruleStates, before.history, sample, quiet)

        store.update { s ->
            val newLog = result.firings.map { "${logFmt.format(Date(sample.time))}  ${it.title}" } + s.log
            s.copy(
                history = AlertEngine.trimHistory(s.history + sample, sample.time),
                ruleStates = result.states,
                lastError = null,
                log = newLog.take(100),
            )
        }

        result.firings.forEach { Notifier.postAlert(context, it) }
        refreshHistorical(store, sample.time)
        return sample
    }

    /** Refetch any stale long-horizon reference prices. Failures are silent — the pill just shows "—". */
    private suspend fun refreshHistorical(store: Store, now: Long) {
        val cached = store.state.value.historical
        val stale = HistoricalPrices.remoteHorizons.filter {
            HistoricalPrices.isStale(cached[it.name], now) && now - (lastHistoricalAttempt[it.name] ?: 0L) > 5 * 60_000L
        }
        if (stale.isEmpty()) return
        val fetched = mutableMapOf<String, com.irigoyen.btcalert.model.RefPrice>()
        for (h in stale) {
            lastHistoricalAttempt[h.name] = now
            try { fetched[h.name] = HistoricalPrices.fetch(h, now) } catch (_: Exception) { }
        }
        if (fetched.isNotEmpty()) store.update { it.copy(historical = it.historical + fetched) }
    }
}
