package com.shilapi.xcertplay

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DarkModeTest {
    @Test
    fun darkModeIsDetectedWithUnrelatedConfigurationBits() {
        assertEquals(true, nightModeOrNull(Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR))
    }

    @Test
    fun lightModeIsDetectedWithUnrelatedConfigurationBits() {
        assertEquals(false, nightModeOrNull(Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_CAR))
    }

    @Test
    fun undefinedModeIsIgnored() {
        assertNull(nightModeOrNull(Configuration.UI_MODE_NIGHT_UNDEFINED))
        assertNull(nightModeOrNull(Configuration.UI_MODE_TYPE_CAR))
    }

    @Test
    fun liveSystemNightModeReplacesAStaleActivityValue() {
        val stale = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR
        val live = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
        val merged = withLiveNightMode(stale, live)
        assertEquals(false, nightModeOrNull(merged))
        assertEquals(Configuration.UI_MODE_TYPE_CAR, merged and Configuration.UI_MODE_TYPE_MASK)
    }

    @Test
    fun anUndefinedLiveNightModeKeepsTheActivityValue() {
        val activity = Configuration.UI_MODE_NIGHT_YES
        assertEquals(activity, withLiveNightMode(activity, Configuration.UI_MODE_NIGHT_UNDEFINED))
    }
}
