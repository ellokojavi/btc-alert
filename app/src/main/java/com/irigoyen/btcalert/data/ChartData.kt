package com.irigoyen.btcalert.data

import com.irigoyen.btcalert.model.ChartSeries
import com.irigoyen.btcalert.model.Horizon
import com.irigoyen.btcalert.model.PriceSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Price series for the home-screen chart, from Coinbase Exchange's public candle API
 * (free, keyless; granularities 60 / 300 / 900 / 3600 / 21600 / 86400 s, ≤ 300 candles per call).
 *
 * Granularity is chosen per timeframe so every chart lands at roughly 150–300 points —
 * dense enough to look like a smooth curve, sparse enough to stay light:
 *
 *   1h  → 1-min candles     (60 pts,  1 call)   refresh 1 min
 *   24h → 5-min candles     (288 pts, 1 call)   refresh 5 min
 *   7d  → 1-hour candles    (168 pts, 1 call)   refresh 1 h
 *   30d → 6-hour candles    (120 pts, 1 call)   refresh 6 h
 *   1y  → daily candles     (365 pts, 2 calls)  refresh 24 h
 *   5y  → daily, 1 in 7 kept (261 pts, 7 calls) refresh 24 h
 */
object ChartData {

    private data class Plan(val granularitySec: Long, val keepEvery: Int, val refreshMs: Long)

    private fun plan(h: Horizon): Plan = when (h) {
        Horizon.H1 -> Plan(60, 1, 60_000L)
        Horizon.D1 -> Plan(300, 1, 5 * 60_000L)
        Horizon.D7 -> Plan(3600, 1, 60 * 60_000L)
        Horizon.D30 -> Plan(21600, 1, 6 * 60 * 60_000L)
        Horizon.Y1 -> Plan(86400, 1, 24 * 60 * 60_000L)
        Horizon.Y5 -> Plan(86400, 7, 24 * 60 * 60_000L)
    }

    fun isStale(series: ChartSeries?, h: Horizon, now: Long): Boolean =
        series == null || now - series.fetchedAt > plan(h).refreshMs

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(h: Horizon, now: Long): ChartSeries = withContext(Dispatchers.IO) {
        val p = plan(h)
        val stepMs = p.granularitySec * 1000L
        val from = now - h.millis
        val chunkMs = 300L * stepMs // API cap: 300 candles per request
        val all = sortedMapOf<Long, Double>()
        var start = from
        while (start < now) {
            val end = minOf(start + chunkMs, now)
            for ((t, close) in candles(p.granularitySec, start, end)) all[t] = close
            start = end
        }
        if (all.isEmpty()) throw Exception("no candles")
        var pts = all.entries.filter { it.key >= from }.map { PriceSample(it.key, it.value, "Coinbase") }
        if (p.keepEvery > 1) {
            // Thin from the newest end so the last point is always the most recent candle.
            pts = pts.reversed().filterIndexed { i, _ -> i % p.keepEvery == 0 }.reversed()
        }
        ChartSeries(points = pts, fetchedAt = now)
    }

    /** (candle start ms → close) for one request window. */
    private fun candles(granularitySec: Long, startMs: Long, endMs: Long): List<Pair<Long, Double>> {
        val url = "https://api.exchange.coinbase.com/products/BTC-USD/candles" +
            "?granularity=$granularitySec&start=${Instant.ofEpochMilli(startMs)}&end=${Instant.ofEpochMilli(endMs)}"
        val req = Request.Builder().url(url).header("User-Agent", "btcalert-personal/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return json.parseToJsonElement(resp.body!!.string()).jsonArray.mapNotNull { c ->
                val a = c.jsonArray
                val t = a[0].jsonPrimitive.longOrNull ?: return@mapNotNull null
                val close = a[4].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                (t * 1000L) to close
            }
        }
    }
}
