package com.shilapi.xcertplay.orchestration

/**
 * Pause before the wireless stack is rebuilt right after a short session.
 *
 * Logs from a BYD Seagull (Android 9) show Save and reconnect failing when the previous session
 * had been active for well under two minutes: the iPhone joins or ignores the new Wi-Fi Direct
 * group and never opens the AirPlay TCP connection. A session that had run for minutes
 * reconnected in under two seconds. The evidence is four attempts, so this only adds a pause; it
 * does not change what is rebuilt.
 */
object RestartCooldown {
    const val SHORT_SESSION_MILLIS = 120_000L
    const val COOLDOWN_MILLIS = 6_000L

    /**
     * @param sessionAgeMillis how long the session that is being closed had been active, or null
     * when it never became active (a startup retry, which already has its own back-off).
     */
    fun delayMillis(sessionAgeMillis: Long?): Long = when {
        sessionAgeMillis == null -> 0L
        sessionAgeMillis < 0L -> 0L
        sessionAgeMillis >= SHORT_SESSION_MILLIS -> 0L
        else -> COOLDOWN_MILLIS
    }

    /**
     * The pause applies only to a restart that rebuilds the Wi-Fi Direct group after a lost connection or saved
     * settings ([pauseApplies]), never to display, rotation or cluster-map restarts or to wired ones.
     */
    fun delayMillis(sessionAgeMillis: Long?, pauseApplies: Boolean, wifiDirect: Boolean): Long =
        if (pauseApplies && wifiDirect) delayMillis(sessionAgeMillis) else 0L
}
