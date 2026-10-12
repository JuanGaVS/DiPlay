package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pBandFallbackTest {
    private class FakeStore : P2pBandFallback.Store {
        override var fiveGhzTimeouts = 0
        override var twoGhzForced = false
    }

    private val store = FakeStore()
    private val fallback = P2pBandFallback(store)
    private val auto = WifiP2pChannels.AUTO

    @Test fun oneTimeoutOnlyCounts() {
        assertEquals(P2pBandFallback.Outcome.COUNTED, fallback.onFirstTcpTimeout(auto, 5180))
        assertEquals(auto, fallback.channelFor(auto))
    }

    @Test fun repeatedFiveGhzTimeoutsSwitchAutoToTwoGhz() {
        fallback.onFirstTcpTimeout(auto, 5180)
        assertEquals(P2pBandFallback.Outcome.SWITCHED_TO_2_4_GHZ, fallback.onFirstTcpTimeout(auto, 5745))
        assertTrue(fallback.forced)
        assertEquals(WifiP2pChannels.AUTO_2_4_GHZ, fallback.channelFor(auto))
    }

    @Test fun confirmedFiveGhzSessionResetsTheCounter() {
        fallback.onFirstTcpTimeout(auto, 5180)
        fallback.onSessionConfirmed(auto, 5180)
        assertEquals(P2pBandFallback.Outcome.COUNTED, fallback.onFirstTcpTimeout(auto, 5180))
        assertFalse(fallback.forced)
    }

    @Test fun twoGhzTimeoutsNeverCount() {
        assertEquals(P2pBandFallback.Outcome.UNCHANGED, fallback.onFirstTcpTimeout(auto, 2437))
        assertEquals(P2pBandFallback.Outcome.UNCHANGED, fallback.onFirstTcpTimeout(auto, null))
        assertEquals(0, store.fiveGhzTimeouts)
    }

    @Test fun driverChoiceIsNeverOverridden() {
        repeat(5) {
            assertEquals(P2pBandFallback.Outcome.UNCHANGED,
                fallback.onFirstTcpTimeout(WifiP2pChannels.AUTO_5_GHZ, 5180))
        }
        assertFalse(fallback.forced)
        store.twoGhzForced = true
        assertEquals(WifiP2pChannels.AUTO_5_GHZ, fallback.channelFor(WifiP2pChannels.AUTO_5_GHZ))
    }

    @Test fun timeoutOnTwoGhzWhileForcedReleasesTheFallback() {
        store.twoGhzForced = true
        assertEquals(P2pBandFallback.Outcome.RELEASED, fallback.onFirstTcpTimeout(auto, 2437))
        assertFalse(fallback.forced)
        assertEquals(auto, fallback.channelFor(auto))
    }

    @Test fun resetClearsEverything() {
        fallback.onFirstTcpTimeout(auto, 5180)
        fallback.onFirstTcpTimeout(auto, 5180)
        fallback.reset()
        assertFalse(fallback.forced)
        assertEquals(0, store.fiveGhzTimeouts)
    }
}
