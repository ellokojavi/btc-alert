package com.irigoyen.btcalert.engine

import com.irigoyen.btcalert.model.AlertRule
import com.irigoyen.btcalert.model.Direction
import com.irigoyen.btcalert.model.PriceSample
import com.irigoyen.btcalert.model.RuleState
import com.irigoyen.btcalert.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEngineTest {
    private val min = AlertEngine.MS_PER_MIN
    private val t0 = 1_700_000_000_000L
    private fun s(minutesAfterT0: Int, price: Double) = PriceSample(t0 + minutesAfterT0 * min, price)

    @Test fun `cross above fires once when price rises through level`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 80_000.0, snoozeMinutes = 60)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 79_900.0)), s(1, 80_050.0))
        assertEquals(1, r.firings.size)
        assertTrue(r.firings[0].title.contains("above"))
    }

    @Test fun `cross above does not fire while already above`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 80_000.0)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 80_100.0)), s(1, 80_200.0))
        assertTrue(r.firings.isEmpty())
    }

    @Test fun `no firing on very first sample (no previous price)`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 80_000.0)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), emptyList(), s(0, 85_000.0))
        assertTrue(r.firings.isEmpty())
    }

    @Test fun `cross below fires when price drops through level`() {
        val rule = AlertRule(type = RuleType.CROSS_BELOW, level = 70_000.0)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 70_500.0)), s(1, 69_999.0))
        assertEquals(1, r.firings.size)
    }

    @Test fun `snooze suppresses re-crossings inside the window and allows after`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 80_000.0, snoozeMinutes = 30)
        var states: Map<String, RuleState> = emptyMap()
        var history = listOf(s(0, 79_000.0))

        // t=1: cross → fires
        var r = AlertEngine.evaluate(listOf(rule), states, history, s(1, 80_100.0)); states = r.states; history = history + s(1, 80_100.0)
        assertEquals(1, r.firings.size)
        // t=10: dip below, t=11: cross again → snoozed
        history = history + s(10, 79_900.0)
        r = AlertEngine.evaluate(listOf(rule), states, history, s(11, 80_100.0)); states = r.states; history = history + s(11, 80_100.0)
        assertEquals(0, r.firings.size)
        // t=40: dip, t=41: cross again → 40 min since last fire ≥ 30 → fires
        history = history + s(40, 79_900.0)
        r = AlertEngine.evaluate(listOf(rule), states, history, s(41, 80_100.0))
        assertEquals(1, r.firings.size)
    }

    @Test fun `disabled rule never fires`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 80_000.0, enabled = false)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 79_000.0)), s(1, 81_000.0))
        assertTrue(r.firings.isEmpty())
    }

    @Test fun `percent move uses sample at or before window start`() {
        val rule = AlertRule(type = RuleType.PERCENT_MOVE, percent = 5.0, windowMinutes = 60, direction = Direction.ANY)
        val history = listOf(s(0, 100_000.0), s(30, 101_000.0), s(59, 104_000.0))
        // 6% up vs the sample 60 min ago
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), history, s(60, 106_000.0))
        assertEquals(1, r.firings.size)
        assertTrue(r.firings[0].title.startsWith("BTC up 6%"))
    }

    @Test fun `percent move does not fire when history is too short`() {
        val rule = AlertRule(type = RuleType.PERCENT_MOVE, percent = 5.0, windowMinutes = 60)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(30, 100_000.0)), s(45, 110_000.0))
        assertTrue(r.firings.isEmpty())
    }

    @Test fun `percent move respects direction`() {
        val down = AlertRule(type = RuleType.PERCENT_MOVE, percent = 5.0, windowMinutes = 60, direction = Direction.DOWN)
        val history = listOf(s(0, 100_000.0))
        assertTrue(AlertEngine.evaluate(listOf(down), emptyMap(), history, s(60, 106_000.0)).firings.isEmpty())
        assertEquals(1, AlertEngine.evaluate(listOf(down), emptyMap(), history, s(60, 94_000.0)).firings.size)
    }

    @Test fun `periodic fires on first check then every interval`() {
        val rule = AlertRule(type = RuleType.PERIODIC, windowMinutes = 120)
        var r = AlertEngine.evaluate(listOf(rule), emptyMap(), emptyList(), s(0, 80_000.0))
        assertEquals(1, r.firings.size)
        assertTrue(r.firings[0].quiet)
        r = AlertEngine.evaluate(listOf(rule), r.states, listOf(s(0, 80_000.0)), s(60, 81_000.0))
        assertEquals(0, r.firings.size)
        r = AlertEngine.evaluate(listOf(rule), r.states, listOf(s(0, 80_000.0)), s(120, 81_000.0))
        assertEquals(1, r.firings.size)
    }

    @Test fun `quiet hours suppress notification but consume snooze`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 80_000.0, snoozeMinutes = 60)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 79_000.0)), s(1, 81_000.0), inQuietHours = true)
        assertTrue(r.firings.isEmpty())
        assertEquals(t0 + 1 * min, r.states[rule.id]?.lastFiredAt)
    }

    @Test fun `quiet hours wrap around midnight`() {
        assertTrue(AlertEngine.isInQuietHours(23, 23, 7))
        assertTrue(AlertEngine.isInQuietHours(3, 23, 7))
        assertFalse(AlertEngine.isInQuietHours(7, 23, 7))
        assertFalse(AlertEngine.isInQuietHours(12, 23, 7))
        assertTrue(AlertEngine.isInQuietHours(10, 9, 17))
        assertFalse(AlertEngine.isInQuietHours(9, 9, 9))
    }

    @Test fun `history keeps 10s samples recent and thins older ones to one per minute`() {
        // 2 hours of 10-second samples
        val tenSec = 10_000L
        val samples = (0 until 720).map { PriceSample(t0 + it * tenSec, 1.0) }
        val now = t0 + 720 * tenSec
        val trimmed = AlertEngine.trimHistory(samples, now)
        val dense = trimmed.count { it.time >= now - 10 * min }
        val sparse = trimmed.size - dense
        assertEquals(60, dense)        // last 10 min: 6/min × 10
        assertEquals(110, sparse)      // first 110 min: 1/min
        // Thinned samples are ≥ 60 s apart
        val sparseTimes = trimmed.filter { it.time < now - 10 * min }.map { it.time }
        assertTrue(sparseTimes.zipWithNext().all { (a, b) -> b - a >= min })
    }

    @Test fun `cross above fires on a spike that happened entirely between two samples`() {
        // Both sampled prices sit below the level; the market touched 82,283 in between.
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 82_000.0)
        val gap = Extremes(high = 82_283.0, low = 81_400.0)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 81_500.0)), s(15, 81_600.0), gap = gap)
        assertEquals(1, r.firings.size)
        assertTrue(r.firings[0].body.startsWith("Peaked at"))
        // Without the gap data the same samples fire nothing — that was the v1.5 miss.
        assertTrue(AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 81_500.0)), s(15, 81_600.0)).firings.isEmpty())
    }

    @Test fun `cross below fires on a dip between samples`() {
        val rule = AlertRule(type = RuleType.CROSS_BELOW, level = 70_000.0)
        val gap = Extremes(high = 71_000.0, low = 69_800.0)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 70_500.0)), s(15, 70_400.0), gap = gap)
        assertEquals(1, r.firings.size)
        assertTrue(r.firings[0].body.startsWith("Dipped to"))
    }

    @Test fun `gap extremes do not fire a rule the price was already above`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 82_000.0)
        val gap = Extremes(high = 82_500.0, low = 82_100.0)
        val r = AlertEngine.evaluate(listOf(rule), emptyMap(), listOf(s(0, 82_200.0)), s(15, 82_300.0), gap = gap)
        assertTrue(r.firings.isEmpty())
    }

    @Test fun `gap extremes still respect snooze`() {
        val rule = AlertRule(type = RuleType.CROSS_ABOVE, level = 82_000.0, snoozeMinutes = 60)
        val gap = Extremes(high = 82_283.0, low = 81_400.0)
        val states = mapOf(rule.id to RuleState(lastFiredAt = t0 + 10 * min, lastFiredPrice = 82_100.0))
        val r = AlertEngine.evaluate(listOf(rule), states, listOf(s(0, 81_500.0)), s(15, 81_600.0), gap = gap)
        assertTrue(r.firings.isEmpty())
    }

    @Test fun `preview firing matches real title format and never needs history`() {
        val above = AlertRule(type = RuleType.CROSS_ABOVE, level = 80_000.0)
        val f = AlertEngine.previewFiring(above, 77_500.0)
        assertEquals("BTC crossed above $80,000", f.title)
        assertTrue(f.body.endsWith("· test"))
        assertFalse(f.quiet)
        val periodic = AlertRule(type = RuleType.PERIODIC, windowMinutes = 60)
        assertTrue(AlertEngine.previewFiring(periodic, null).quiet)
        val pct = AlertRule(type = RuleType.PERCENT_MOVE, percent = 5.0, windowMinutes = 60, direction = Direction.DOWN)
        assertEquals("BTC down 5% in 1 h", AlertEngine.previewFiring(pct, 80_000.0).title)
    }

    @Test fun `history is trimmed by age and count`() {
        val many = (0 until 5000).map { s(it, 1.0) }
        val trimmed = AlertEngine.trimHistory(many, t0 + 5000 * min, maxAgeMs = 3000 * min, maxCount = 1000)
        assertEquals(1000, trimmed.size)
        assertEquals(t0 + 4999 * min, trimmed.last().time)
    }
}
