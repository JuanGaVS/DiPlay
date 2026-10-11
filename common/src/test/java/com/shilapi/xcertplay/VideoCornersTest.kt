package com.shilapi.xcertplay

import android.content.Context
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VideoCornersTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() = prefs.edit().clear().apply()

    @Test fun cornersAreOffByDefaultAndRoundTrip() {
        assertFalse(AirPlayPersistence.loadRoundedVideoCorners(context))
        AirPlayPersistence.saveFps(context, 60)
        AirPlayPersistence.saveRoundedVideoCorners(context, true)
        assertTrue(AirPlayPersistence.loadRoundedVideoCorners(context))
        assertEquals(60, AirPlayPersistence.loadFps(context))
        AirPlayPersistence.saveRoundedVideoCorners(context, false)
        assertFalse(AirPlayPersistence.loadRoundedVideoCorners(context))
    }

    @Test fun squareCornersWithoutSafeAreaTurnsTheClipOff() {
        AirPlayPersistence.saveRoundedVideoCorners(context, true)
        assertTrue(VideoCorners.enabled(context))
        AirPlayPersistence.saveMainDisplayDisableSafeArea(context, true)
        assertFalse(VideoCorners.enabled(context))
        AirPlayPersistence.saveMainDisplayDisableSafeArea(context, false)
        assertTrue(VideoCorners.enabled(context))
    }

    @Test fun radiusFollowsTheShorterSide() {
        assertEquals(720 * VideoCorners.RADIUS_FRACTION, VideoCorners.radiusPx(1280, 720), 0.001f)
        assertEquals(720 * VideoCorners.RADIUS_FRACTION, VideoCorners.radiusPx(720, 1280), 0.001f)
        assertEquals(0f, VideoCorners.radiusPx(0, 720), 0f)
        assertEquals(0f, VideoCorners.radiusPx(1280, -1), 0f)
    }

    @Test fun togglingTheClipTurnsViewClippingOnAndOff() {
        val view = View(context)
        VideoCorners.setRounded(view, true)
        assertTrue(view.clipToOutline)
        VideoCorners.setRounded(view, false)
        assertFalse(view.clipToOutline)
    }
}
