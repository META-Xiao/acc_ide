/*
 *  This file is part of AccIDE.
 *
 *  AccIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AccIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AccIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.acc_ide

import android.content.Context
import android.util.Log
import com.termux.app.TermuxApplication

/**
 * Application class for AccIDE that extends TermuxApplication
 * to provide integrated terminal functionality
 */
class AccIDEApplication : TermuxApplication() {

    companion object {
        private const val TAG = "AccIDEApplication"
        
        @JvmStatic
        lateinit var instance: AccIDEApplication
            private set
    }

    override fun onCreate() {
        super.onCreate() // This will call TermuxApplication.onCreate() first
        instance = this
        
        Log.d(TAG, "AccIDE Application starting...")
        
        // Initialize any AccIDE specific functionality here
        initializeApplication()
    }
    
    private fun initializeApplication() {
        // Initialize components that don't depend on termux
        Log.d(TAG, "AccIDE Application initialized successfully")
    }
    
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        Log.d(TAG, "Application base context attached")
    }
}
