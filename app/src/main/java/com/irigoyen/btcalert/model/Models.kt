package com.irigoyen.btcalert.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class RuleType(val label: String) {
    CROSS_ABOVE("Crosses above"),
    CROSS_BELOW("Crosses below"),
    PERCENT_MOVE("Percent move"),
    PERIODIC("Periodic check-in"),
}

enum class Direction(val label: String) { ANY("Up or down"), UP("Up only"), DOWN("Down only") }

@Serializable
data class AlertRule(
    val id: String = UUID.randomUUID().toString(),
    val type: RuleType,
    val enabled: Boolean = true,
    /** CROSS_ABOVE / CROSS_BELOW: the USD level. */
    val level: Double = 0.0,
    /** PERCENT_MOVE: size of move in percent (e.g. 5.0). */
    val percent: Double = 5.0,
    /** PERCENT_MOVE: look-back window in minutes. PERIODIC: interval in minutes. */
    val windowMinutes: Int = 60,
    val direction: Direction = Direction.ANY,
    /** Minimum minutes between two firings of this rule. Ignored for PERIODIC (uses windowMinutes). */
    val snoozeMinutes: Int = 60,
) {
    fun describe(): String = when (type) {
        RuleType.CROSS_ABOVE -> "BTC rises through ${usd(level)}"
        RuleType.CROSS_BELOW -> "BTC drops through ${usd(level)}"
        RuleType.PERCENT_MOVE -> {
            val dir = when (direction) { Direction.ANY -> "moves"; Direction.UP -> "rises"; Direction.DOWN -> "falls" }
            "BTC $dir ≥ ${fmtPct(percent)} in ${fmtMin(windowMinutes)}"
        }
        RuleType.PERIODIC -> "Price check-in every ${fmtMin(windowMinutes)}"
    }
}

/** Per-rule mutable state kept separately from the rule definition. */
@Serializable
data class RuleState(
    val lastFiredAt: Long? = null,
    val lastFiredPrice: Double? = null,
)

@Serializable
data class PriceSample(val time: Long, val price: Double, val source: String = "")

enum class PollMode(val label: String, val blurb: String) {
    BATTERY_SAVER("Battery saver", "Checks every ~15 min via Android's background scheduler. No persistent notification."),
    REALTIME("Real-time", "A persistent service polls on your chosen interval. Shows a small status-bar notification."),
}

@Serializable
data class Settings(
    val pollMode: PollMode = PollMode.BATTERY_SAVER,
    /** Real-time mode poll interval in seconds. */
    val realtimeIntervalSec: Int = 60,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 7,
    /** Timeframe selected for the home-screen chart ([Horizon.name]). */
    val chartHorizon: String = Horizon.D1.name,
)

/** Look-back horizons shown as change pills on the home screen. */
enum class Horizon(val label: String, val minutes: Long) {
    H1("1h", 60),
    D1("24h", 24 * 60),
    D7("7d", 7 * 24 * 60),
    D30("30d", 30 * 24 * 60),
    Y1("1y", 365L * 24 * 60),
    Y5("5y", 1826L * 24 * 60);

    val millis: Long get() = minutes * 60_000L
}

/** A historical reference price fetched from an exchange's candle API. */
@Serializable
data class RefPrice(val time: Long, val price: Double, val fetchedAt: Long)

/** A price series for the chart: candle closes over one [Horizon], oldest first. */
@Serializable
data class ChartSeries(val points: List<PriceSample>, val fetchedAt: Long)

@Serializable
data class AppState(
    val rules: List<AlertRule> = emptyList(),
    val ruleStates: Map<String, RuleState> = emptyMap(),
    val history: List<PriceSample> = emptyList(),
    /** Keyed by [Horizon.name]; filled lazily, refreshed every ~30 min. */
    val historical: Map<String, RefPrice> = emptyMap(),
    /** Chart data keyed by [Horizon.name]; fetched on demand for the selected timeframe. */
    val charts: Map<String, ChartSeries> = emptyMap(),
    val settings: Settings = Settings(),
    /** Why the last fetch failed, or null after any success. See [classifyFetchError]. */
    val lastFetchError: FetchError? = null,
    val log: List<String> = emptyList(),
)

fun usd(v: Double): String = "$" + String.format(java.util.Locale.US, "%,.0f", v)
fun usd2(v: Double): String = "$" + String.format(java.util.Locale.US, "%,.2f", v)
fun fmtPct(v: Double): String = if (v == v.toLong().toDouble()) "${v.toLong()}%" else String.format(java.util.Locale.US, "%.1f%%", v)
fun fmtMin(m: Int): String = when {
    m % 1440 == 0 -> "${m / 1440} d"
    m % 60 == 0 -> "${m / 60} h"
    else -> "$m min"
}
