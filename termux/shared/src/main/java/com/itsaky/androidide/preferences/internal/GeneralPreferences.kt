package com.itsaky.androidide.preferences.internal

import androidx.appcompat.app.AppCompatDelegate

/**
 * Stub implementation for GeneralPreferences.
 */
object GeneralPreferences {
    
    val INSTANCE = this
    
    fun getUiMode(): Int {
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
}