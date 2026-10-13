package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min

/**
 * Rounded corners for the CarPlay picture. CarPlay's own display has them; a head unit shows the
 * picture as a plain rectangle, so the corners are clipped here and the car's black shows through.
 */
internal object VideoCorners {
    /** Corner radius as a share of the shorter side: about 61 px on a 1280x720 picture. */
    const val RADIUS_FRACTION = 0.085f

    /**
     * The saved choice, unless "Square corners, no safe area" is on: that asks the iPhone for a square
     * full-screen picture, and clipping it would cut off the screen's own corners.
     */
    fun enabled(context: Context): Boolean =
        AirPlayPersistence.loadRoundedVideoCorners(context) && !AirPlayPersistence.loadMainDisplayDisableSafeArea(context)

    fun radiusPx(width: Int, height: Int): Float =
        if (width <= 0 || height <= 0) 0f else min(width, height) * RADIUS_FRACTION

    /** Clips [view] to a rounded rectangle, or removes the clip. A [android.view.TextureView] honours it. */
    fun setRounded(view: View, enabled: Boolean) {
        if (enabled) {
            view.outlineProvider = provider
            view.clipToOutline = true
        } else {
            view.clipToOutline = false
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        view.invalidateOutline()
    }

    private val provider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, radiusPx(view.width, view.height))
        }
    }
}
