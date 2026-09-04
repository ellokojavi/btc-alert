package com.irigoyen.btcalert.data

import com.irigoyen.btcalert.engine.Extremes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * What the market actually did between two samples, from Coinbase Exchange's 1-minute candles.
 *
 * Polling sees points; the market moves continuously. A spike that starts and ends between two
 * polls is invisible to sampling — with 15-minute checks, a 2-minute spike through a threshold
 * is missed ~87% of the time. Asking the exchange for the interval's high and low makes cross
 * alerts independent of the poll interval, at one small request per check.
 */
object IntervalExtremes {

    /** Below this gap the spot samples already cover the interval; don't spend a request. */
    const val MIN_GAP_MS = 90_000L

    /** 1-min candles, 300 per request → don't try to fill a gap longer than this. */
    private const val MAX_GAP_MS = 300 * 60_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** High/low over (fromMs, toMs], or null if the window is too short, too long, or unavailable. */
    suspend fun fetch(fromMs: Long, toMs: Long): Extremes? = withContext(Dispatchers.IO) {
        val gap = toMs - fromMs
        if (gap < MIN_GAP_MS || gap > MAX_GAP_MS) return@withContext null
        val url = "https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=60" +
            "&start=${Instant.ofEpochMilli(fromMs)}&end=${Instant.ofEpochMilli(toMs)}"
        val req = Request.Builder().url(url).header("User-Agent", "btcalert-personal/1.0").build()
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                var hi = Double.NEGATIVE_INFINITY
                var lo = Double.POSITIVE_INFINITY
                json.parseToJsonElement(resp.body!!.string()).jsonArray.forEach { c ->
                    val a = c.jsonArray                       // [time, low, high, open, close, volume]
                    val low = a[1].jsonPrimitive.doubleOrNull ?: return@forEach
                    val high = a[2].jsonPrimitive.doubleOrNull ?: return@forEach
                    if (high > hi) hi = high
                    if (low < lo) lo = low
                }
                if (hi.isFinite() && lo.isFinite()) Extremes(hi, lo) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
