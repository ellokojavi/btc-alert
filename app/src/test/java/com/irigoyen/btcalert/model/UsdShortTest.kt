package com.irigoyen.btcalert.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UsdShortTest {

    @Test fun `thousands keep one decimal`() {
        assertEquals("$81.2k", usdShort(81_240.0))
        assertEquals("$105.3k", usdShort(105_340.0))
    }

    @Test fun `rounds rather than truncates`() {
        assertEquals("$81.3k", usdShort(81_260.0))
        assertEquals("$82.0k", usdShort(81_990.0))
    }

    @Test fun `the k boundary holds from both sides`() {
        assertEquals("$999", usdShort(999.0))
        assertEquals("$1.0k", usdShort(1_000.0))
    }

    @Test fun `millions switch unit so the label stays short`() {
        assertEquals("$1.05M", usdShort(1_050_000.0))
        assertEquals("$1.00M", usdShort(1_000_000.0))
    }
}
