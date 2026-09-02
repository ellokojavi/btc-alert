package com.irigoyen.btcalert.engine

import com.irigoyen.btcalert.model.AlertRule
import com.irigoyen.btcalert.model.Direction
import com.irigoyen.btcalert.model.PriceSample
import com.irigoyen.btcalert.model.RuleState
import com.irigoyen.btcalert.model.RuleType
import com.irigoyen.btcalert.model.fmtMin
import com.irigoyen.btcalert.model.fmtPct
import com.irigoyen.btcalert.model.usd
import com.irigoyen.btcalert.model.usd2
import kotlin.math.abs

/** One notification the engine wants sent. */
data class Firing(val rule: AlertRule, val title: String, val body: String, val quiet: Boolean)

data class EvalResult(val firings: List<Firing>, val states: Map<String, RuleState>)

/**
 * Pure alert logic — no Android imports so it can be unit-tested on the JVM.
 *
 * @param history  all prior samples, oldest first, NOT including [current].
 * @param current  the sample just fetched.
 */
object AlertEngine {

    const val MS_PER_MIN = 60_000L

    fun evaluate(
        rules: List<AlertRule>,
        states: Map<String, RuleState>,
        history: List<PriceSample>,
        current: PriceSample,
        inQuietHours: Boolean = false,
    ): EvalResult {
        val prev = history.lastOrNull()
        val firings = mutableListOf<Firing>()
        val newStates = states.toMutableMap()
        val now = current.time

        for (rule in rules) {
            if (!rule.enabled) continue
            val st = states[rule.id] ?: RuleState()

            val firing: Firing? = when (rule.type) {
                RuleType.CROSS_ABOVE -> {
                    if (prev != null && snoozeOver(st, rule.snoozeMinutes, now)
                        && prev.price < rule.level && current.price >= rule.level
                    ) Firing(
                        rule,
                        "BTC crossed above ${usd(rule.level)}",
                        "Now ${usd2(current.price)} (was ${usd2(prev.price)})",
                        quiet = false,
                    ) else null
                }
                RuleType.CROSS_BELOW -> {
                    if (prev != null && snoozeOver(st, rule.snoozeMinutes, now)
                        && prev.price > rule.level && current.price <= rule.level
                    ) Firing(
                        rule,
                        "BTC crossed below ${usd(rule.level)}",
                        "Now ${usd2(current.price)} (was ${usd2(prev.price)})",
                        quiet = false,
                    ) else null
                }
                RuleType.PERCENT_MOVE -> {
                    val ref = referenceSample(history, now - rule.windowMinutes * MS_PER_MIN)
                    if (ref != null && snoozeOver(st, rule.snoozeMinutes, now)) {
                        val pct = (current.price - ref.price) / ref.price * 100.0
                        val dirOk = when (rule.direction) {
                            Direction.ANY -> true
                            Direction.UP -> pct > 0
                            Direction.DOWN -> pct < 0
                        }
                        if (dirOk && abs(pct) >= rule.percent) {
                            val word = if (pct >= 0) "up" else "down"
                            Firing(
                                rule,
                                "BTC $word ${fmtPct(abs(round1(pct)))} in ${fmtMin(rule.windowMinutes)}",
                                "${usd2(ref.price)} → ${usd2(current.price)}",
                                quiet = false,
                            )
                        } else null
                    } else null
                }
                RuleType.PERIODIC -> {
                    if (snoozeOver(st, rule.windowMinutes, now)) {
                        val ref = referenceSample(history, now - rule.windowMinutes * MS_PER_MIN)
                        val delta = if (ref != null) {
                            val pct = (current.price - ref.price) / ref.price * 100.0
                            val sign = if (pct >= 0) "+" else "−"
                            "  ($sign${fmtPct(abs(round1(pct)))} vs ${fmtMin(rule.windowMinutes)} ago)"
                        } else ""
                        Firing(rule, "BTC ${usd2(current.price)}", "Periodic check-in$delta", quiet = true)
                    } else null
                }
            }

            if (firing != null) {
                // Quiet hours suppress the notification but still consume the snooze so we
                // don't get a burst of stale alerts the moment quiet hours end.
                if (!inQuietHours) firings += firing
                newStates[rule.id] = RuleState(lastFiredAt = now, lastFiredPrice = current.price)
            }
        }
        return EvalResult(firings, newStates)
    }

    /**
     * A sample notification for [rule] exactly as it would look when it fires for real,
     * built from the current price. Used by the "Test notification" button; bypasses
     * enabled/snooze/quiet-hours and never touches rule state.
     */
    fun previewFiring(rule: AlertRule, price: Double?): Firing {
        val p = price ?: 80_000.0
        return when (rule.type) {
            RuleType.CROSS_ABOVE -> Firing(
                rule, "BTC crossed above ${usd(rule.level)}",
                "Now ${usd2(maxOf(p, rule.level))} (was ${usd2(rule.level * 0.999)}) · test", quiet = false,
            )
            RuleType.CROSS_BELOW -> Firing(
                rule, "BTC crossed below ${usd(rule.level)}",
                "Now ${usd2(minOf(p, rule.level))} (was ${usd2(rule.level * 1.001)}) · test", quiet = false,
            )
            RuleType.PERCENT_MOVE -> {
                val up = rule.direction != Direction.DOWN
                val ref = if (up) p / (1 + rule.percent / 100) else p / (1 - rule.percent / 100)
                Firing(
                    rule, "BTC ${if (up) "up" else "down"} ${fmtPct(rule.percent)} in ${fmtMin(rule.windowMinutes)}",
                    "${usd2(ref)} → ${usd2(p)} · test", quiet = false,
                )
            }
            RuleType.PERIODIC -> Firing(rule, "BTC ${usd2(p)}", "Periodic check-in · test", quiet = true)
        }
    }

    private fun snoozeOver(st: RuleState, minutes: Int, now: Long): Boolean {
        val last = st.lastFiredAt ?: return true
        return now - last >= minutes * MS_PER_MIN
    }

    /** The most recent sample taken at or before [atOrBefore]; null if history doesn't reach back that far. */
    fun referenceSample(history: List<PriceSample>, atOrBefore: Long): PriceSample? =
        history.lastOrNull { it.time <= atOrBefore }

    private fun round1(v: Double) = Math.round(v * 10.0) / 10.0

    /**
     * Keep history bounded: drop samples older than [maxAgeMs]; keep every sample from the
     * last [denseWindowMs] (the app polls every 10 s while open) but thin older samples to at
     * most one per [sparseStepMs]; finally cap the count.
     */
    fun trimHistory(
        history: List<PriceSample>,
        now: Long,
        maxAgeMs: Long = 48L * 3600_000L,
        maxCount: Int = 4000,
        denseWindowMs: Long = 10 * MS_PER_MIN,
        sparseStepMs: Long = MS_PER_MIN,
    ): List<PriceSample> {
        val cutoff = now - maxAgeMs
        val denseFrom = now - denseWindowMs
        val kept = ArrayList<PriceSample>(history.size)
        var lastKeptSparse: Long? = null
        for (s in history) {
            if (s.time < cutoff) continue
            if (s.time >= denseFrom) { kept += s; continue }
            val last = lastKeptSparse
            if (last == null || s.time - last >= sparseStepMs) { kept += s; lastKeptSparse = s.time }
        }
        return if (kept.size > maxCount) kept.takeLast(maxCount) else kept
    }

    fun isInQuietHours(hourOfDay: Int, startHour: Int, endHour: Int): Boolean =
        if (startHour == endHour) false
        else if (startHour < endHour) hourOfDay in startHour until endHour
        else hourOfDay >= startHour || hourOfDay < endHour
}
