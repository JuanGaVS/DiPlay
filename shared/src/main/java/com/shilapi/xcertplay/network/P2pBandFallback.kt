package com.shilapi.xcertplay.network

import android.content.Context

/**
 * Auto Wi-Fi Direct starts on 5 GHz when no Wi-Fi network pins the channel. Android reports that
 * group as created even when the head unit's radio cannot really serve it: the iPhone then
 * authenticates over Bluetooth, is told where to join, and never opens AirPlay, and every retry
 * builds the same 5 GHz group. On a BYD Seagull (MT6765, Android 9) 36 of 36 groups on 5 GHz timed
 * out and 11 of 11 on 2.4 GHz connected.
 *
 * This remembers repeated first-connection timeouts on a 5 GHz group and then makes Auto use 2.4 GHz.
 * It never overrides a band or channel the driver chose, and it lets go if 2.4 GHz times out too.
 */
class P2pBandFallback(private val store: Store) {
    interface Store {
        var fiveGhzTimeouts: Int
        var twoGhzForced: Boolean
    }

    enum class Outcome { UNCHANGED, COUNTED, SWITCHED_TO_2_4_GHZ, RELEASED }

    /** The channel setting to use for the next Wi-Fi Direct group. */
    fun channelFor(preferredChannel: Int): Int =
        if (preferredChannel == WifiP2pChannels.AUTO && store.twoGhzForced) WifiP2pChannels.AUTO_2_4_GHZ
        else preferredChannel

    /** Call when the iPhone was told to join a group on [groupFrequencyMHz] and never connected. */
    fun onFirstTcpTimeout(preferredChannel: Int, groupFrequencyMHz: Int?): Outcome {
        if (preferredChannel != WifiP2pChannels.AUTO) return Outcome.UNCHANGED
        val onFiveGhz = isFiveGhz(groupFrequencyMHz)
        if (store.twoGhzForced) {
            if (onFiveGhz) return Outcome.UNCHANGED
            // 2.4 GHz did not help either, so stop forcing it and let Auto choose again.
            reset()
            return Outcome.RELEASED
        }
        if (!onFiveGhz) return Outcome.UNCHANGED
        val timeouts = store.fiveGhzTimeouts + 1
        if (timeouts < TIMEOUTS_BEFORE_FALLBACK) {
            store.fiveGhzTimeouts = timeouts
            return Outcome.COUNTED
        }
        store.fiveGhzTimeouts = 0
        store.twoGhzForced = true
        return Outcome.SWITCHED_TO_2_4_GHZ
    }

    /** Call when the iPhone really connected over a group on [groupFrequencyMHz]. */
    fun onSessionConfirmed(preferredChannel: Int, groupFrequencyMHz: Int?) {
        if (preferredChannel == WifiP2pChannels.AUTO && isFiveGhz(groupFrequencyMHz)) {
            store.fiveGhzTimeouts = 0
        }
    }

    fun reset() {
        store.fiveGhzTimeouts = 0
        store.twoGhzForced = false
    }

    val forced: Boolean get() = store.twoGhzForced

    private fun isFiveGhz(frequencyMHz: Int?): Boolean =
        frequencyMHz != null && frequencyMHz in WifiP2pBand.FIVE_GHZ.frequenciesMHz

    companion object {
        /** One timeout can be the iPhone's own doing; two in a row on 5 GHz is a pattern. */
        const val TIMEOUTS_BEFORE_FALLBACK = 2

        fun of(context: Context) = P2pBandFallback(PreferencesStore(context.applicationContext))
    }

    /** Only the counter and the flag are stored: no network names, addresses or phone identifiers. */
    private class PreferencesStore(context: Context) : Store {
        private val prefs = context.getSharedPreferences("carplay_wifi_p2p_band_fallback", Context.MODE_PRIVATE)
        override var fiveGhzTimeouts: Int
            get() = prefs.getInt("five_ghz_timeouts", 0)
            set(value) { prefs.edit().putInt("five_ghz_timeouts", value).apply() }
        override var twoGhzForced: Boolean
            get() = prefs.getBoolean("two_ghz_forced", false)
            set(value) { prefs.edit().putBoolean("two_ghz_forced", value).apply() }
    }
}
