package com.shilapi.xcertplay.network

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Auto Wi-Fi Direct starts on 5 GHz when no Wi-Fi network pins the channel. Android reports that
 * group as created even when the head unit's radio cannot really serve it: the iPhone then
 * authenticates over Bluetooth, is told where to join, and never opens AirPlay, and every retry
 * builds the same 5 GHz group. On a BYD Seagull (MT6765, Android 9) 36 of 36 groups on 5 GHz timed
 * out and 11 of 11 on 2.4 GHz connected.
 *
 * This remembers repeated first-connection timeouts on a 5 GHz group and then makes Auto use 2.4 GHz.
 * It never overrides a band or channel the driver chose. The fallback does not last forever: it ends
 * after [FORCED_LIFETIME_MILLIS], when the car joins a different Wi-Fi network, when 2.4 GHz times out
 * as often as 5 GHz did, and when the driver picks a channel. Settings shows while it is active.
 */
class P2pBandFallback(
    private val store: Store,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    interface Store {
        /** Consecutive first-connection timeouts: on 5 GHz while not forced, on 2.4 GHz while forced. */
        var timeouts: Int
        var twoGhzForced: Boolean
        /** Wall-clock time the fallback started, or 0. */
        var forcedAtMillis: Long
        /** Opaque key of the Wi-Fi network the car was on when the fallback started, or null for none. */
        var networkKey: String?
    }

    enum class Outcome { UNCHANGED, COUNTED, SWITCHED_TO_2_4_GHZ, RELEASED }

    /**
     * The channel setting to use for the next Wi-Fi Direct group.
     * @param networkKey opaque key of the Wi-Fi network the car is on now, null when it is on none.
     */
    fun channelFor(preferredChannel: Int, networkKey: String?): Int {
        expireIfStale(true, networkKey)
        return if (preferredChannel == WifiP2pChannels.AUTO && store.twoGhzForced) WifiP2pChannels.AUTO_2_4_GHZ
        else preferredChannel
    }

    /**
     * Call when the iPhone was told to join a group on [groupFrequencyMHz] and never connected.
     * The same number of timeouts that starts the fallback also ends it.
     */
    fun onFirstTcpTimeout(preferredChannel: Int, groupFrequencyMHz: Int?, networkKey: String?): Outcome {
        if (preferredChannel != WifiP2pChannels.AUTO) return Outcome.UNCHANGED
        expireIfStale(true, networkKey)
        val onFiveGhz = isFiveGhz(groupFrequencyMHz)
        if (store.twoGhzForced) {
            if (onFiveGhz) return Outcome.UNCHANGED
            val timeouts = store.timeouts + 1
            if (timeouts < TIMEOUTS_BEFORE_CHANGE) {
                store.timeouts = timeouts
                return Outcome.COUNTED
            }
            // 2.4 GHz did not help either, so stop forcing it and let Auto choose again.
            reset()
            return Outcome.RELEASED
        }
        if (!onFiveGhz) return Outcome.UNCHANGED
        val timeouts = store.timeouts + 1
        if (timeouts < TIMEOUTS_BEFORE_CHANGE) {
            store.timeouts = timeouts
            return Outcome.COUNTED
        }
        store.timeouts = 0
        store.twoGhzForced = true
        store.forcedAtMillis = clock()
        store.networkKey = networkKey
        return Outcome.SWITCHED_TO_2_4_GHZ
    }

    /** Call when the iPhone really connected, over any band and in any hotspot mode. */
    fun onSessionConfirmed() {
        store.timeouts = 0
    }

    fun reset() {
        store.timeouts = 0
        store.twoGhzForced = false
        store.forcedAtMillis = 0L
        store.networkKey = null
    }

    /** Whether Auto currently uses 2.4 GHz because of failures; shown in Settings. */
    val forced: Boolean
        get() {
            expireIfStale(false, null)
            return store.twoGhzForced
        }

    private fun expireIfStale(checkNetwork: Boolean, networkKey: String?) {
        if (!store.twoGhzForced) return
        val age = clock() - store.forcedAtMillis
        val expired = age < 0L || age >= FORCED_LIFETIME_MILLIS
        val networkChanged = checkNetwork && networkKey != store.networkKey
        if (expired || networkChanged) reset()
    }

    private fun isFiveGhz(frequencyMHz: Int?): Boolean =
        frequencyMHz != null && frequencyMHz in WifiP2pBand.FIVE_GHZ.frequenciesMHz

    companion object {
        /** One timeout can be the iPhone's own doing; two in a row is a pattern. Starts and ends the fallback. */
        const val TIMEOUTS_BEFORE_CHANGE = 2
        val FORCED_LIFETIME_MILLIS: Long = TimeUnit.DAYS.toMillis(7)

        /**
         * An opaque key for the Wi-Fi network the car is on now, or null when it is on none. Only a
         * short hash of the network name is kept, so a different network is noticed without storing it.
         */
        @Suppress("DEPRECATION")
        fun currentNetworkKey(context: Context): String? = try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val ssid = wifi?.connectionInfo?.takeIf { it.networkId != -1 }?.ssid
                ?.removeSurrounding("\"")
                ?.takeUnless { it.isEmpty() || it == android.net.wifi.WifiManager.UNKNOWN_SSID }
            ssid?.let {
                java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .take(6).joinToString("") { b -> "%02x".format(b) }
            }
        } catch (_: SecurityException) {
            null
        }

        fun of(context: Context) = P2pBandFallback(PreferencesStore(context.applicationContext))
    }

    /** Only counters, a flag, a time and an opaque network key are stored: no network names, addresses or phone identifiers. */
    private class PreferencesStore(context: Context) : Store {
        private val prefs = context.getSharedPreferences("carplay_wifi_p2p_band_fallback", Context.MODE_PRIVATE)
        override var timeouts: Int
            get() = prefs.getInt("timeouts", 0)
            set(value) { prefs.edit().putInt("timeouts", value).apply() }
        override var twoGhzForced: Boolean
            get() = prefs.getBoolean("two_ghz_forced", false)
            set(value) { prefs.edit().putBoolean("two_ghz_forced", value).apply() }
        override var forcedAtMillis: Long
            get() = prefs.getLong("forced_at_millis", 0L)
            set(value) { prefs.edit().putLong("forced_at_millis", value).apply() }
        override var networkKey: String?
            get() = prefs.getString("network_key", null)
            set(value) { prefs.edit().putString("network_key", value).apply() }
    }
}
