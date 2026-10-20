package com.shilapi.xcertplay

import android.content.res.Configuration

/**
 * Returns the explicit night state carried by [uiMode], or null when the head unit reports
 * UI_MODE_NIGHT_UNDEFINED so callers keep their previous state instead of flipping to light.
 */
internal fun nightModeOrNull(uiMode: Int): Boolean? =
    when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true
        Configuration.UI_MODE_NIGHT_NO -> false
        else -> null
    }

/**
 * [activityUiMode] with its day/night bits replaced by those of [liveUiMode], when the latter has an explicit
 * night state. A BYD head unit updates the system and application configuration when the car switches between
 * day and night, while the activity's own configuration stays at the value it had when it started
 * (the activity keeps uiMode in configChanges and never gets the callback): a diagnostic log shows
 * systemUiMode 0x21 -> 0x11 with the activity still on 0x21.
 */
internal fun withLiveNightMode(activityUiMode: Int, liveUiMode: Int): Int {
    if (nightModeOrNull(liveUiMode) == null) return activityUiMode
    return (activityUiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        (liveUiMode and Configuration.UI_MODE_NIGHT_MASK)
}
