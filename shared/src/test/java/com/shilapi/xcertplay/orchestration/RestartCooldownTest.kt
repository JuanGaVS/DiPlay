package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertEquals
import org.junit.Test

class RestartCooldownTest {
    @Test
    fun noPauseWhenTheSessionNeverBecameActive() {
        assertEquals(0L, RestartCooldown.delayMillis(null))
    }

    @Test
    fun pausesAfterAShortSession() {
        assertEquals(RestartCooldown.COOLDOWN_MILLIS, RestartCooldown.delayMillis(0L))
        assertEquals(RestartCooldown.COOLDOWN_MILLIS, RestartCooldown.delayMillis(68_000L))
        assertEquals(
            RestartCooldown.COOLDOWN_MILLIS,
            RestartCooldown.delayMillis(RestartCooldown.SHORT_SESSION_MILLIS - 1),
        )
    }

    @Test
    fun noPauseAfterALongSession() {
        assertEquals(0L, RestartCooldown.delayMillis(RestartCooldown.SHORT_SESSION_MILLIS))
        assertEquals(0L, RestartCooldown.delayMillis(17 * 60_000L))
    }

    @Test
    fun noPauseWhenTheClockWentBackwards() {
        assertEquals(0L, RestartCooldown.delayMillis(-5L))
    }

    @Test
    fun pausesOnlyWhenTheRestartMayPauseOnWifiDirect() {
        assertEquals(RestartCooldown.COOLDOWN_MILLIS, RestartCooldown.delayMillis(30_000L, true, true))
        assertEquals(0L, RestartCooldown.delayMillis(30_000L, false, true))
        assertEquals(0L, RestartCooldown.delayMillis(30_000L, true, false))
        assertEquals(0L, RestartCooldown.delayMillis(null, true, true))
    }
}
