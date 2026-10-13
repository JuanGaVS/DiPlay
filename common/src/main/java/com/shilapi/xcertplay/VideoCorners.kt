package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.min
import kotlin.math.roundToInt

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
        if (view.outlineProvider !is PictureOutline) view.outlineProvider = PictureOutline()
        view.clipToOutline = enabled
        view.invalidateOutline()
    }

    /**
     * Where the picture sits inside [view] after scaling. A picture narrower or shorter than the view
     * leaves black bars, and the corners belong to the picture, not to the view's edges.
     */
    fun setPictureBounds(view: View, left: Float, top: Float, width: Float, height: Float) {
        val outline = view.outlineProvider as? PictureOutline ?: return
        outline.picture = Rect(left.roundToInt(), top.roundToInt(), (left + width).roundToInt(), (top + height).roundToInt())
        view.invalidateOutline()
    }

    /** The visible part of [picture] in a [viewWidth] x [viewHeight] view; the whole view without one. */
    fun clipBounds(viewWidth: Int, viewHeight: Int, picture: Rect?): Rect {
        val view = Rect(0, 0, maxOf(viewWidth, 0), maxOf(viewHeight, 0))
        if (picture == null) return view
        val visible = Rect(picture)
        return if (visible.intersect(view)) visible else view
    }

    private class PictureOutline : ViewOutlineProvider() {
        var picture: Rect? = null

        override fun getOutline(view: View, outline: Outline) {
            val clip = clipBounds(view.width, view.height, picture)
            outline.setRoundRect(clip, radiusPx(clip.width(), clip.height()))
        }
    }
}
