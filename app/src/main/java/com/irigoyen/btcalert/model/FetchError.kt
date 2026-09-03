package com.irigoyen.btcalert.model

import kotlinx.serialization.Serializable

/** The device's network state at the moment a fetch was attempted. */
enum class NetworkStatus {
    /** No active network at all — airplane mode, Wi-Fi off, no mobile data. */
    NONE,

    /** A network is up but Android hasn't confirmed it reaches the internet (captive portal, dead hotspot). */
    UNVALIDATED,

    /** A validated internet connection. */
    ONLINE,
}

/**
 * One price source's failure. [transport] separates "the request never got an answer"
 * (DNS, routing, timeout, TLS) from "we got an answer we couldn't use" (HTTP status, bad JSON) —
 * the two mean very different things to the person holding the phone.
 */
data class SourceFailure(val source: String, val message: String, val transport: Boolean)

/** Why a price fetch failed, phrased for the home screen rather than for a stack trace. */
enum class FetchErrorKind(val headline: String, val hint: String) {
    OFFLINE(
        "No internet connection",
        "Check Wi-Fi or mobile data. Checks resume on their own.",
    ),
    NO_INTERNET(
        "Network has no internet access",
        "This Wi-Fi may need a sign-in.",
    ),
    UNREACHABLE(
        "Can't reach the price services",
        "Your connection is up, so this is probably temporary.",
    ),
    SOURCE(
        "Price services returned errors",
        "Nothing to do — the app keeps retrying.",
    );

    /** True when the cause is the phone's connection rather than the exchanges. */
    val isConnectivity: Boolean get() = this == OFFLINE || this == NO_INTERNET
}

@Serializable
data class FetchError(
    val kind: FetchErrorKind = FetchErrorKind.SOURCE,
    /** Per-source technical detail, kept for the log screen; never shown in the price hero. */
    val detail: String = "",
    val at: Long = 0L,
)

/**
 * Device connectivity wins over whatever the sources said: with no network, "no internet" is
 * the whole story and the four DNS failures underneath it are noise. Pure so it can be tested
 * on the JVM — the Android-specific part is reading [NetworkStatus], see `data/Connectivity.kt`.
 */
fun classifyFetchError(network: NetworkStatus, failures: List<SourceFailure>, at: Long): FetchError {
    val kind = when {
        network == NetworkStatus.NONE -> FetchErrorKind.OFFLINE
        network == NetworkStatus.UNVALIDATED -> FetchErrorKind.NO_INTERNET
        // Every source failed before getting a reply: the exchanges are unlikely to all be down
        // at once, so this reads as a connection that is up but not carrying traffic.
        failures.isEmpty() || failures.all { it.transport } -> FetchErrorKind.UNREACHABLE
        else -> FetchErrorKind.SOURCE
    }
    return FetchError(kind, failures.joinToString("; ") { "${it.source}: ${it.message}" }, at)
}
