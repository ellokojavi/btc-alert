package com.irigoyen.btcalert.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Latest state of the chain itself, for the block card. */
@Serializable
data class ChainInfo(
    val height: Long = 0,
    /** When the tip block was mined (epoch ms). */
    val minedAt: Long = 0,
    val txCount: Int = 0,
    val pool: String = "",
    /** Fastest fee tier, sat/vB. */
    val feeSatVb: Int = 0,
    val mempoolCount: Int = 0,
    /** Blocks left in this difficulty epoch. */
    val retargetBlocks: Int = 0,
    /**
     * How far ahead (+) or behind (−) the network is running this epoch, in percent. The next
     * difficulty adjustment is this figure, and it's also what the current pace is derived from.
     */
    val difficultyChangePct: Double = 0.0,
    val fetchedAt: Long = 0,
)

/**
 * Mean seconds between blocks right now. Difficulty targets 600 s, and the pending adjustment
 * says by how much the network has been beating that target, so pace = 600 / (1 + change).
 * Clamped because a wild reading would put a nonsense number in front of the user.
 */
fun paceSeconds(difficultyChangePct: Double): Int =
    (600.0 / (1.0 + difficultyChangePct / 100.0)).coerceIn(300.0, 1200.0).roundToInt()

/** "~10 min" — the wait for the next block, which does not shrink as you watch. */
fun paceLabel(difficultyChangePct: Double): String =
    "~${(paceSeconds(difficultyChangePct) / 60.0).roundToInt()} min"

/**
 * Blocks are memoryless: after a long gap the next one is still a full interval away. Rather than
 * hide that, the copy leans on it once the wait is clearly long.
 */
fun blockWaitNote(minutesSinceBlock: Long): String? = when {
    minutesSinceBlock < 12 -> null
    minutesSinceBlock < 25 -> ", still"
    minutesSinceBlock < 45 -> ", still · variance is a feature"
    else -> ", still · somewhere a miner is having a day"
}

/** True for the couple of minutes after a block lands, which earns the NEW badge. */
fun isFreshBlock(minutesSinceBlock: Long): Boolean = minutesSinceBlock < 2
