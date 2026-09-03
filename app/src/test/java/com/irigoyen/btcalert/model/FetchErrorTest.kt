package com.irigoyen.btcalert.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchErrorTest {
    private val t0 = 1_700_000_000_000L
    private fun dns(name: String) = SourceFailure(name, "Unable to resolve host \"api.$name.com\"", transport = true)
    private fun http(name: String, code: Int) = SourceFailure(name, "HTTP $code", transport = false)

    @Test fun `no network beats whatever the sources reported`() {
        val e = classifyFetchError(NetworkStatus.NONE, listOf(dns("coinbase"), http("kraken", 500)), t0)
        assertEquals(FetchErrorKind.OFFLINE, e.kind)
        assertTrue(e.kind.isConnectivity)
    }

    @Test fun `offline with nothing even attempted still explains itself`() {
        val e = classifyFetchError(NetworkStatus.NONE, emptyList(), t0)
        assertEquals(FetchErrorKind.OFFLINE, e.kind)
        assertEquals("", e.detail)
        assertEquals(t0, e.at)
    }

    @Test fun `a network Android could not validate reads as a captive portal`() {
        val e = classifyFetchError(NetworkStatus.UNVALIDATED, listOf(dns("coinbase")), t0)
        assertEquals(FetchErrorKind.NO_INTERNET, e.kind)
        assertTrue(e.kind.isConnectivity)
    }

    @Test fun `online but every source failed before answering is unreachable`() {
        val failures = listOf(dns("coinbase"), dns("coingecko"), dns("kraken"), dns("binance"))
        val e = classifyFetchError(NetworkStatus.ONLINE, failures, t0)
        assertEquals(FetchErrorKind.UNREACHABLE, e.kind)
        assertFalse(e.kind.isConnectivity)
    }

    @Test fun `one source answering badly makes it the sources' problem, not the connection's`() {
        val failures = listOf(http("coinbase", 503), dns("coingecko"), dns("kraken"), dns("binance"))
        val e = classifyFetchError(NetworkStatus.ONLINE, failures, t0)
        assertEquals(FetchErrorKind.SOURCE, e.kind)
    }

    @Test fun `detail keeps every source's reason for the log screen`() {
        val e = classifyFetchError(NetworkStatus.ONLINE, listOf(http("coinbase", 503), http("kraken", 429)), t0)
        assertEquals("coinbase: HTTP 503; kraken: HTTP 429", e.detail)
    }

    @Test fun `every kind has copy a person can act on`() {
        FetchErrorKind.entries.forEach {
            assertTrue(it.headline.isNotBlank())
            assertTrue(it.hint.isNotBlank())
        }
    }
}
