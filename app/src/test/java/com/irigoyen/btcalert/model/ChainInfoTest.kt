package com.irigoyen.btcalert.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainInfoTest {

    @Test fun `pace derives from the pending difficulty adjustment`() {
        // Running 1.16% ahead of the 600 s target → blocks are arriving in ~593 s.
        assertEquals(593, paceSeconds(1.16))
        assertEquals(600, paceSeconds(0.0))
        assertEquals(632, paceSeconds(-5.0))
    }

    @Test fun `pace is clamped so a wild reading never reaches the user`() {
        assertEquals(300, paceSeconds(500.0))
        assertEquals(1200, paceSeconds(-90.0))
    }

    @Test fun `pace label rounds to whole minutes`() {
        assertEquals("~10 min", paceLabel(1.16))
        assertEquals("~10 min", paceLabel(0.0))
        assertEquals("~11 min", paceLabel(-5.0))
    }

    @Test fun `the wait note only appears once the gap is genuinely long`() {
        assertNull(blockWaitNote(0))
        assertNull(blockWaitNote(11))
        assertEquals(", still", blockWaitNote(12))
        assertTrue(blockWaitNote(30)!!.contains("variance"))
        assertTrue(blockWaitNote(60)!!.contains("miner"))
    }

    @Test fun `fresh block badge lasts two minutes`() {
        assertTrue(isFreshBlock(0))
        assertTrue(isFreshBlock(1))
        assertTrue(!isFreshBlock(2))
    }

    @Test fun `chain info defaults keep an existing state file loadable`() {
        val c = ChainInfo()
        assertEquals(0L, c.height)
        assertEquals("", c.pool)
    }
}
