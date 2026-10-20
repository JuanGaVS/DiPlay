package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
class ConnectionEventDiagnosticsTest {
    @Test fun adapterStateIsNamed() {
        val intent = Intent(BluetoothAdapter.ACTION_STATE_CHANGED)
            .putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_TURNING_OFF)
        assertEquals("bluetooth-adapter state=turning-off", ConnectionEventDiagnostics.describe(intent))
    }

    @Test fun profileChangeNamesTheProfileAndBothStates() {
        val intent = Intent("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED")
            .putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
            .putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_CONNECTED)
        assertEquals("bluetooth-profile a2dp-sink device=unknown state=disconnected previous=connected",
            ConnectionEventDiagnostics.describe(intent))
    }

    @Test fun aclEventWithoutADeviceIsStillRecorded() {
        assertEquals("bluetooth-acl disconnected device=unknown",
            ConnectionEventDiagnostics.describe(Intent("android.bluetooth.device.action.ACL_DISCONNECTED")))
    }

    @Test fun wifiDirectStateAndSystemEventsAreRecorded() {
        assertEquals("wifi-p2p state=disabled", ConnectionEventDiagnostics.describe(
            Intent(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                .putExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED)))
        assertEquals("screen off", ConnectionEventDiagnostics.describe(Intent(Intent.ACTION_SCREEN_OFF)))
        assertEquals("power disconnected", ConnectionEventDiagnostics.describe(Intent(Intent.ACTION_POWER_DISCONNECTED)))
        assertEquals("system shutdown", ConnectionEventDiagnostics.describe(Intent("android.intent.action.ACTION_SHUTDOWN")))
    }

    @Test fun otherActionsAreIgnored() {
        assertNull(ConnectionEventDiagnostics.describe(Intent("com.example.other")))
        assertNull(ConnectionEventDiagnostics.describe(Intent()))
    }

    @Test fun deviceKeyIsAShortHashThatNeverContainsTheAddress() {
        val key = ConnectionEventDiagnostics.key("AA:BB:CC:DD:EE:FF")
        assertEquals(4, key.length)
        assertFalse(key.contains(":"))
        assertEquals(key, ConnectionEventDiagnostics.key("AA:BB:CC:DD:EE:FF"))
        assertTrue(ConnectionEventDiagnostics.key("11:22:33:44:55:66") != key)
        assertEquals("none", ConnectionEventDiagnostics.key(null))
    }

    @Test fun filterCoversEveryRecordedAction() {
        val filter = ConnectionEventDiagnostics.filter()
        assertTrue(filter.hasAction(Intent.ACTION_SCREEN_OFF))
        assertTrue(filter.hasAction(BluetoothAdapter.ACTION_STATE_CHANGED))
        assertTrue(filter.hasAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
    }
}
