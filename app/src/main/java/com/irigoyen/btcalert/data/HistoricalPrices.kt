package com.irigoyen.btcalert.data

import com.irigoyen.btcalert.model.Horizon
import com.irigoyen.btcalert.model.RefPrice
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
 * Historical BTC/USD reference prices from Coinbase Exchange's public candle API —
 * free, keyless, hourly resolution back to 2015. One small request per horizon.
 */
object HistoricalPrices {

    /** How old a cached reference may be before it's refetched. */
    const val REFRESH_MS = 30 * 60_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Horizons that need a remote lookup (1h always comes from local history). */
    val remoteHorizons = listOf(Horizon.D1, Horizon.D7, Horizon.D30, Horizon.Y1, Horizon.Y5)

    fun isStale(ref: RefPrice?, now: Long): Boolean = ref == null || now - ref.fetchedAt > REFRESH_MS

    /** Price at (now − horizon), from the hourly candle containing that instant. */
    suspend fun fetch(horizon: Horizon, now: Long): RefPrice = withContext(Dispatchers.IO) {
        val target = now - horizon.millis
        val start = Instant.ofEpochMilli(target - 3_600_000L).toString()
        val end = Instant.ofEpochMilli(target + 3_600_000L).toString()
        val url = "https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=3600&start=$start&end=$end"
        val req = Request.Builder().url(url).header("User-Agent", "btcalert-personal/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val candles = json.parseToJsonElement(resp.body!!.string()).jsonArray.mapNotNull { c ->
                val a = c.jsonArray
                val t = a[0].jsonPrimitive.longOrNull ?: return@mapNotNull null
                val open = a[3].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                val close = a[4].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
                Triple(t * 1000L, open, close)
            }
            if (candles.isEmpty()) throw Exception("no candles")
            // Candle that contains the target instant → its close; else the earliest candle's open.
            val containing = candles.filter { it.first <= target }.maxByOrNull { it.first }
            val (t, price) = if (containing != null) containing.first to containing.third
            else candles.minBy { it.first }.let { it.first to it.second }
            RefPrice(time = t, price = price, fetchedAt = now)
        }
    }
}
