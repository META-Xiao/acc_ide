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

import com.acc_ide.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.acc_ide.termux.terminal.TerminalSession

/**
 * TerminalSessionClient delegate for AccIDE.
 *
 * @author AccIDE Team
 */
class AccIdeTerminalSessionClient(
    activity: TerminalActivity
) : TermuxTerminalSessionActivityClient(activity) {

    override fun onSessionFinished(finishedSession: TerminalSession) {
        // Handle session completion for AccIDE specific operations
        super.onSessionFinished(finishedSession)
    }
}