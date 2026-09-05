package com.irigoyen.btcalert.data

import com.irigoyen.btcalert.model.ChainInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Chain state for the block card, from mempool.space's free keyless API.
 *
 * Three small requests, and only while the app is on screen — nothing here runs in the
 * background, so a stale card costs nothing and a missing one is never an alert that didn't fire.
 */
object ChainData {

    /** The card is only visible with the app open; half a minute is plenty for ~10-minute blocks. */
    const val REFRESH_MS = 30_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    fun isStale(info: ChainInfo?, now: Long): Boolean =
        info == null || info.height == 0L || now - info.fetchedAt > REFRESH_MS

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "btcalert-personal/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body!!.string()
        }
    }

    suspend fun fetch(now: Long): ChainInfo = withContext(Dispatchers.IO) {
        val tip = json.parseToJsonElement(get("https://mempool.space/api/v1/blocks")).jsonArray
            .firstOrNull()?.jsonObject ?: throw Exception("no blocks")
        val adj = json.parseToJsonElement(get("https://mempool.space/api/v1/difficulty-adjustment")).jsonObject
        // Fees and mempool depth are garnish: a failure there shouldn't cost us the block itself.
        val fee = runCatching {
            json.parseToJsonElement(get("https://mempool.space/api/v1/fees/recommended"))
                .jsonObject["fastestFee"]?.jsonPrimitive?.intOrNull ?: 0
        }.getOrDefault(0)
        val mempool = runCatching {
            json.parseToJsonElement(get("https://mempool.space/api/mempool"))
                .jsonObject["count"]?.jsonPrimitive?.intOrNull ?: 0
        }.getOrDefault(0)

        ChainInfo(
            height = tip["height"]?.jsonPrimitive?.longOrNull ?: 0L,
            minedAt = (tip["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000L,
            txCount = tip["tx_count"]?.jsonPrimitive?.intOrNull ?: 0,
            pool = tip["extras"]?.jsonObject?.get("pool")?.jsonObject
                ?.get("name")?.jsonPrimitive?.content.orEmpty(),
            feeSatVb = fee,
            mempoolCount = mempool,
            retargetBlocks = adj["remainingBlocks"]?.jsonPrimitive?.intOrNull ?: 0,
            difficultyChangePct = adj["difficultyChange"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            fetchedAt = now,
        )
    }
}
