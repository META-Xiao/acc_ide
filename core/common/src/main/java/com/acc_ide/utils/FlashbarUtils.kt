package com.acc_ide.utils

import android.app.Activity
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.snackbar.Snackbar

fun flashMessage(msg: String?, type: FlashType) {
    withActivity { flashMessage(msg, type) }
}

fun flashMessage(@StringRes msg: Int, type: FlashType) {
    withActivity { flashMessage(msg, type) }
}

fun flashSuccess(msg: String?) {
    withActivity { flashSuccess(msg) }
}

fun flashSuccess(@StringRes msg: Int) {
    withActivity { flashSuccess(msg) }
}

fun flashError(msg: String?) {
    withActivity { flashError(msg) }
}

fun flashError(@StringRes msg: Int) {
    withActivity { flashError(msg) }
}

fun flashInfo(msg: String?) {
    withActivity { flashInfo(msg) }
}

fun flashInfo(@StringRes msg: Int) {
    withActivity { flashInfo(msg) }
}

private fun Activity.flashMessage(msg: String?, type: FlashType) {
    if (msg.isNullOrEmpty()) return
    
    // Try to find a suitable view for Snackbar, fallback to Toast if not found
    val rootView = findViewById<View>(android.R.id.content)
    if (rootView != null) {
        val snackbar = Snackbar.make(rootView, msg, Snackbar.LENGTH_SHORT)
        
        when (type) {
            FlashType.SUCCESS -> {
                snackbar.setBackgroundTint(getColor(android.R.color.holo_green_dark))
                snackbar.setTextColor(getColor(android.R.color.white))
            }
            FlashType.ERROR -> {
                snackbar.setBackgroundTint(getColor(android.R.color.holo_red_dark))
                snackbar.setTextColor(getColor(android.R.color.white))
            }
            FlashType.INFO -> {
                snackbar.setBackgroundTint(getColor(android.R.color.holo_blue_dark))
                snackbar.setTextColor(getColor(android.R.color.white))
            }
        }
        
        snackbar.show()
    } else {
        // Fallback to Toast
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

private fun Activity.flashMessage(@StringRes msg: Int, type: FlashType) {
    flashMessage(getString(msg), type)
}

private fun Activity.flashSuccess(msg: String?) {
    flashMessage(msg, FlashType.SUCCESS)
}

private fun Activity.flashSuccess(@StringRes msg: Int) {
    flashMessage(msg, FlashType.SUCCESS)
}

private fun Activity.flashError(msg: String?) {
    flashMessage(msg, FlashType.ERROR)
}

private fun Activity.flashError(@StringRes msg: Int) {
    flashMessage(msg, FlashType.ERROR)
}

private fun Activity.flashInfo(msg: String?) {
    flashMessage(msg, FlashType.INFO)
}

private fun Activity.flashInfo(@StringRes msg: Int) {
    flashMessage(msg, FlashType.INFO)
}

private fun <T> withActivity(action: Activity.() -> T?): T? {
    // For now, return null as we don't have access to top activity
    // This would need to be implemented with proper activity tracking
    return null
}

enum class FlashType {
    ERROR,
    INFO,
    SUCCESS
}