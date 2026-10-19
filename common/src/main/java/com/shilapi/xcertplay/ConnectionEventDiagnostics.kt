package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import java.security.MessageDigest

/**
 * Writes the radio and power events around a disconnect to the session log, so a report shows what the
 * head unit saw when an iPhone session ended: whether the Bluetooth link to the phone stayed up or came
 * back, when the Wi-Fi Direct group went away, and whether the screen or power changed first.
 *
 * Device addresses are never logged. A device shows as a 4-digit hash, enough to tell two phones apart.
 */
internal class ConnectionEventDiagnostics(private val log: (String) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        describe(intent)?.let { log("CONNECTION_EVENT $it") }
    }

    companion object {
        private const val A2DP_SINK_ACTION = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"
        private const val HEADSET_CLIENT_ACTION = "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED"
        private const val A2DP_ACTION = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        private const val HEADSET_ACTION = "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"

        fun filter(): IntentFilter = IntentFilter().apply {
            listOf(
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                A2DP_ACTION, HEADSET_ACTION, A2DP_SINK_ACTION, HEADSET_CLIENT_ACTION,
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION,
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION,
                WifiManager.WIFI_STATE_CHANGED_ACTION,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                "android.intent.action.ACTION_SHUTDOWN",
            ).forEach(::addAction)
        }

        /** A line for [intent], or null for an action this does not record. */
        @Suppress("DEPRECATION")
        fun describe(intent: Intent): String? = when (val action = intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> "bluetooth-acl connected ${device(intent)}"
            BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED -> "bluetooth-acl disconnect-requested ${device(intent)}"
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> "bluetooth-acl disconnected ${device(intent)}"
            BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
                "bluetooth-bond ${device(intent)} state=${bond(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1))} " +
                    "previous=${bond(intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1))}"
            BluetoothAdapter.ACTION_STATE_CHANGED ->
                "bluetooth-adapter state=${adapter(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1))}"
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED ->
                "bluetooth-adapter-link ${device(intent)} " +
                    "state=${link(intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1))} " +
                    "previous=${link(intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, -1))}"
            A2DP_ACTION, HEADSET_ACTION, A2DP_SINK_ACTION, HEADSET_CLIENT_ACTION ->
                "bluetooth-profile ${profile(action)} ${device(intent)} " +
                    "state=${link(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1))} " +
                    "previous=${link(intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1))}"
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION ->
                "wifi-p2p state=${if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) ==
                    WifiP2pManager.WIFI_P2P_STATE_ENABLED) "enabled" else "disabled"}"
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val info = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                val group = intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                "wifi-p2p connection state=${info?.detailedState ?: "unknown"} " +
                    (if (group == null) "group=none" else "group=present owner=${group.isGroupOwner} clients=${group.clientList.size}")
            }
            WifiManager.WIFI_STATE_CHANGED_ACTION ->
                "wifi state=${wifi(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1))}"
            Intent.ACTION_SCREEN_ON -> "screen on"
            Intent.ACTION_SCREEN_OFF -> "screen off"
            Intent.ACTION_POWER_CONNECTED -> "power connected"
            Intent.ACTION_POWER_DISCONNECTED -> "power disconnected"
            "android.intent.action.ACTION_SHUTDOWN" -> "system shutdown"
            else -> null
        }

        private fun device(intent: Intent): String {
            val device = try {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            } catch (_: RuntimeException) {
                null
            } ?: return "device=unknown"
            val major = try { device.bluetoothClass?.majorDeviceClass } catch (_: SecurityException) { null }
            return "device=${key(device.address)} class=${major?.let { "0x" + it.toString(16) } ?: "unknown"} " +
                "bond=${bond(device.bondState)}"
        }

        internal fun key(address: String?): String {
            if (address == null) return "none"
            val digest = MessageDigest.getInstance("SHA-256").digest(address.toByteArray())
            return digest.take(2).joinToString("") { "%02x".format(it) }
        }

        private fun bond(state: Int) = when (state) {
            BluetoothDevice.BOND_NONE -> "none"
            BluetoothDevice.BOND_BONDING -> "bonding"
            BluetoothDevice.BOND_BONDED -> "bonded"
            else -> "unknown"
        }

        private fun link(state: Int) = when (state) {
            BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
            BluetoothProfile.STATE_CONNECTING -> "connecting"
            BluetoothProfile.STATE_CONNECTED -> "connected"
            BluetoothProfile.STATE_DISCONNECTING -> "disconnecting"
            else -> "unknown"
        }

        private fun adapter(state: Int) = when (state) {
            BluetoothAdapter.STATE_OFF -> "off"
            BluetoothAdapter.STATE_TURNING_ON -> "turning-on"
            BluetoothAdapter.STATE_ON -> "on"
            BluetoothAdapter.STATE_TURNING_OFF -> "turning-off"
            else -> "unknown"
        }

        private fun wifi(state: Int) = when (state) {
            WifiManager.WIFI_STATE_DISABLED -> "disabled"
            WifiManager.WIFI_STATE_DISABLING -> "disabling"
            WifiManager.WIFI_STATE_ENABLED -> "enabled"
            WifiManager.WIFI_STATE_ENABLING -> "enabling"
            else -> "unknown"
        }

        private fun profile(action: String) = when (action) {
            A2DP_ACTION -> "a2dp"
            HEADSET_ACTION -> "headset"
            A2DP_SINK_ACTION -> "a2dp-sink"
            else -> "headset-client"
        }
    }
}
