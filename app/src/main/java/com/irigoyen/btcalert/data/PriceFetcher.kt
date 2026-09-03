package com.irigoyen.btcalert.data

import com.irigoyen.btcalert.model.PriceSample
import com.irigoyen.btcalert.model.SourceFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Fetches the BTC/USD spot price from free, keyless public endpoints, in order,
 * returning the first that works. Each source is a tiny parser over the JSON it returns.
 */
object PriceFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    private data class Source(val name: String, val url: String, val parse: (String) -> Double)

    private val sources = listOf(
        Source("Coinbase", "https://api.coinbase.com/v2/prices/BTC-USD/spot") { body ->
            json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject["amount"]!!.jsonPrimitive.content.toDouble()
        },
        Source("CoinGecko", "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd") { body ->
            json.parseToJsonElement(body).jsonObject["bitcoin"]!!.jsonObject["usd"]!!.jsonPrimitive.content.toDouble()
        },
        Source("Kraken", "https://api.kraken.com/0/public/Ticker?pair=XBTUSD") { body ->
            val result = json.parseToJsonElement(body).jsonObject["result"]!!.jsonObject
            result.values.first().jsonObject["c"]!!.jsonArray[0].jsonPrimitive.content.toDouble()
        },
        Source("Binance", "https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT") { body ->
            json.parseToJsonElement(body).jsonObject["price"]!!.jsonPrimitive.content.toDouble()
        },
    )

    class AllSourcesFailed(val failures: List<SourceFailure>) :
        Exception(failures.joinToString("; ") { "${it.source}: ${it.message}" })

    /** True when the request never reached a server, as opposed to returning something unusable. */
    private fun isTransport(e: Exception): Boolean =
        e is UnknownHostException || e is SocketException || e is InterruptedIOException || e is SSLException

    suspend fun fetch(): PriceSample = withContext(Dispatchers.IO) {
        val failures = mutableListOf<SourceFailure>()
        for (s in sources) {
            try {
                val req = Request.Builder().url(s.url).header("User-Agent", "btcalert-personal/1.0").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    val price = s.parse(resp.body!!.string())
                    if (price <= 0 || price.isNaN()) throw Exception("bad price $price")
                    return@withContext PriceSample(System.currentTimeMillis(), price, s.name)
                }
            } catch (e: Exception) {
                failures += SourceFailure(s.name, e.message ?: e.javaClass.simpleName, isTransport(e))
            }
        }
        throw AllSourcesFailed(failures)
    }
}
