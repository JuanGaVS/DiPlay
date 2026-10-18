package com.shilapi.xcertplay.network

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pBandFallbackTest {
    private class FakeStore : P2pBandFallback.Store {
        override var timeouts = 0
        override var twoGhzForced = false
        override var forcedAtMillis = 0L
        override var networkKey: String? = null
    }

    private val store = FakeStore()
    private var now = 1_000_000L
    private val fallback = P2pBandFallback(store) { now }
    private val auto = WifiP2pChannels.AUTO
    private val net = "aa11"

    private fun timeout(frequency: Int?, network: String? = net) =
        fallback.onFirstTcpTimeout(auto, frequency, network)

    private fun force() {
        timeout(5180)
        assertEquals(P2pBandFallback.Outcome.SWITCHED_TO_2_4_GHZ, timeout(5745))
    }

    @Test fun oneTimeoutOnlyCounts() {
        assertEquals(P2pBandFallback.Outcome.COUNTED, timeout(5180))
        assertEquals(auto, fallback.channelFor(auto, net))
    }

    @Test fun repeatedFiveGhzTimeoutsSwitchAutoToTwoGhz() {
        force()
        assertTrue(fallback.forced)
        assertEquals(WifiP2pChannels.AUTO_2_4_GHZ, fallback.channelFor(auto, net))
    }

    @Test fun anyConfirmedSessionResetsTheCounter() {
        timeout(5180)
        fallback.onSessionConfirmed()
        assertEquals(P2pBandFallback.Outcome.COUNTED, timeout(5180))
        assertFalse(fallback.forced)
        // Also while forced: one good session wipes the release counter.
        force()
        timeout(2437)
        fallback.onSessionConfirmed()
        assertEquals(P2pBandFallback.Outcome.COUNTED, timeout(2437))
        assertTrue(fallback.forced)
    }

    @Test fun twoGhzTimeoutsNeverCountBeforeTheFallback() {
        assertEquals(P2pBandFallback.Outcome.UNCHANGED, timeout(2437))
        assertEquals(P2pBandFallback.Outcome.UNCHANGED, timeout(null))
        assertEquals(0, store.timeouts)
    }

    @Test fun driverChoiceIsNeverOverridden() {
        repeat(5) {
            assertEquals(P2pBandFallback.Outcome.UNCHANGED,
                fallback.onFirstTcpTimeout(WifiP2pChannels.AUTO_5_GHZ, 5180, net))
        }
        assertFalse(fallback.forced)
        store.twoGhzForced = true
        store.forcedAtMillis = now
        store.networkKey = net
        assertEquals(WifiP2pChannels.AUTO_5_GHZ, fallback.channelFor(WifiP2pChannels.AUTO_5_GHZ, net))
    }

    @Test fun releasingNeedsAsManyTimeoutsAsForcing() {
        force()
        assertEquals(P2pBandFallback.Outcome.COUNTED, timeout(2437))
        assertTrue(fallback.forced)
        assertEquals(P2pBandFallback.Outcome.RELEASED, timeout(2437))
        assertFalse(fallback.forced)
        assertEquals(auto, fallback.channelFor(auto, net))
    }

    @Test fun fiveGhzTimeoutsWhileForcedChangeNothing() {
        force()
        assertEquals(P2pBandFallback.Outcome.UNCHANGED, timeout(5180))
        assertTrue(fallback.forced)
    }

    @Test fun forcedFallbackExpiresAfterSevenDays() {
        force()
        now += TimeUnit.DAYS.toMillis(7) - 1
        assertTrue(fallback.forced)
        assertEquals(WifiP2pChannels.AUTO_2_4_GHZ, fallback.channelFor(auto, net))
        now += 1
        assertFalse(fallback.forced)
        assertEquals(auto, fallback.channelFor(auto, net))
        assertEquals(0L, store.forcedAtMillis)
    }

    @Test fun clockGoingBackwardsEndsTheFallback() {
        force()
        now -= 10
        assertFalse(fallback.forced)
    }

    @Test fun aDifferentNetworkEndsTheFallback() {
        force()
        assertEquals(auto, fallback.channelFor(auto, "bb22"))
        assertFalse(fallback.forced)
    }

    @Test fun noNetworkAtStartAndNowStaysForced() {
        timeout(5180, null)
        timeout(5180, null)
        assertEquals(WifiP2pChannels.AUTO_2_4_GHZ, fallback.channelFor(auto, null))
        assertNull(store.networkKey)
        assertEquals(auto, fallback.channelFor(auto, net))
    }

    @Test fun resetClearsEverything() {
        force()
        fallback.reset()
        assertFalse(fallback.forced)
        assertEquals(0, store.timeouts)
        assertEquals(0L, store.forcedAtMillis)
        assertNull(store.networkKey)
    }
}
