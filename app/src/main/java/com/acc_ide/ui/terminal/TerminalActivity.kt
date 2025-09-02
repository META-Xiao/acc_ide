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

package com.acc_ide.ui.terminal

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.acc_ide.R
import com.acc_ide.termux.app.TermuxActivity
import com.acc_ide.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.acc_ide.termux.shared.termux.shell.command.runner.terminal.TermuxSession

/**
 * Terminal Activity for AccIDE
 * 
 * @author AccIDE Team
 */
class TerminalActivity : TermuxActivity() {

    override val navigationBarColor: Int
        get() = ContextCompat.getColor(this, android.R.color.black)
    override val statusBarColor: Int
        get() = ContextCompat.getColor(this, android.R.color.black)

    private var canAddNewSessions = true
        set(value) {
            field = value
            findViewById<View>(com.acc_ide.termux.R.id.new_session_button)?.isEnabled = value
        }

    companion object {
        private const val TAG = "TerminalActivity"
        private const val KEY_TERMINAL_CAN_ADD_SESSIONS = "acc_ide.terminal.sessions.canAddSessions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightNavigationBars = false
        controller.isAppearanceLightStatusBars = false
        super.onCreate(savedInstanceState)

        canAddNewSessions = savedInstanceState?.getBoolean(KEY_TERMINAL_CAN_ADD_SESSIONS, true) ?: true
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.terminal_title)
    }

    override fun onCreateTerminalSessionClient(): TermuxTerminalSessionActivityClient {
        return AccIdeTerminalSessionClient(this)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean(KEY_TERMINAL_CAN_ADD_SESSIONS, canAddNewSessions)
    }

    override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
        super.onServiceConnected(componentName, service)
        Log.d(TAG, "Terminal service connected")
    }

    override fun onCreateNewSession(
        isFailsafe: Boolean,
        sessionName: String?,
        workingDirectory: String?
    ) {
        if (canAddNewSessions) {
            super.onCreateNewSession(isFailsafe, sessionName, workingDirectory)
        } else {
            Log.w(TAG, "Cannot create new session - session creation disabled")
        }
    }

    override fun setupTermuxSessionOnServiceConnected(
        intent: Intent?,
        workingDir: String?,
        sessionName: String?,
        existingSession: TermuxSession?,
        launchFailsafe: Boolean
    ) {
        super.setupTermuxSessionOnServiceConnected(
            intent,
            workingDir,
            sessionName,
            existingSession,
            launchFailsafe
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        finish()
    }
}