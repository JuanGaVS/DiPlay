package com.shilapi.xcertplay

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import com.shilapi.xcertplay.host.R

internal interface AppAppearanceOwner {
    val currentAppNight: Boolean
}

private fun Context.appDialogNight(): Boolean =
    (this as? AppAppearanceOwner)?.currentAppNight ?: resolveAppNightNow()

internal fun Context.appDialogTheme(): Int = if (appDialogNight()) {
    R.style.Theme_Xcertplay_Dialog_Dark
} else {
    R.style.Theme_Xcertplay_Dialog_Light
}

internal fun Context.appDialogContext(): Context = ContextThemeWrapper(this, appDialogTheme())

/**
 * The dialog surface, applied to the window in code as well as by the theme. Some head-unit
 * firmware ignores the theme's window background and draws every Settings dialog transparent, so
 * its choices print over the page behind. Setting the drawable on the window does not depend on
 * the theme.
 */
private fun Dialog.applyAppSurface(night: Boolean) {
    val color = context.getColor(if (night) R.color.dialog_surface_dark else R.color.dialog_surface_light)
    window?.setBackgroundDrawable(ColorDrawable(color))
}

/** An [AlertDialog.Builder] whose dialogs always have the opaque DiPlay surface. */
private class AppDialogBuilder(context: Context, private val night: Boolean) :
    AlertDialog.Builder(context, if (night) R.style.Theme_Xcertplay_Dialog_Dark else R.style.Theme_Xcertplay_Dialog_Light) {
    override fun create(): AlertDialog = super.create().also { it.applyAppSurface(night) }
}

internal fun Context.appDialogBuilder(): AlertDialog.Builder = appDialogBuilder(appDialogNight())

internal fun Context.appDialogBuilder(night: Boolean): AlertDialog.Builder = AppDialogBuilder(this, night)

internal fun Context.appDialog(): Dialog {
    val night = appDialogNight()
    return Dialog(this, appDialogTheme()).also { it.applyAppSurface(night) }
}
