package com.itsaky.androidide.utils

import android.content.Context
import android.content.res.Configuration

/**
 * Utility functions for resource handling.
 */
object ResourceUtilsKt {
    
    /**
     * Check if the system is in dark mode.
     */
    @JvmStatic
    fun isSystemInDarkMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}