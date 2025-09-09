/*
 * This file is part of AccIDE.
 *
 * AccIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AccIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AccIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.acc_ide.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.acc_ide.ui.terminal.TerminalActivity
import java.io.File

/**
 * Utility class for terminal operations
 * 
 * @author AccIDE Team
 */
object TerminalUtils {
    
    /**
     * Start terminal with default working directory
     */
    fun startTerminal(context: Context) {
        startTerminal(context, null, "AccIDE Terminal")
    }
    
    /**
     * Start terminal with specified working directory
     */
    fun startTerminal(context: Context, workingDirectory: String?, sessionName: String? = null) {
        try {
            val intent = Intent(context, TerminalActivity::class.java).apply {
                // 设置工作目录 - 使用内部存储确保权限正确
                val workDir = workingDirectory ?: run {
                    // 使用AndroidIDE方式：内部存储的home目录，确保有执行权限
                    val homeDir = File(context.filesDir, "home")
                    if (!homeDir.exists()) {
                        homeDir.mkdirs()
                    }
                    homeDir.absolutePath
                }
                
                putExtra("TERMUX_ACTIVITY.EXTRA_SESSION_WORKING_DIR", workDir)
                putExtra("TERMUX_ACTIVITY.EXTRA_SESSION_NAME", sessionName ?: "AccIDE Terminal")
                putExtra("TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION", false)
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("TerminalUtils", "Failed to start terminal", e)
            Toast.makeText(context, "Failed to start terminal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Start terminal in project directory
     */
    fun startTerminalInProject(context: Context, projectPath: String) {
        val projectDir = File(projectPath)
        val workingDir = if (projectDir.exists() && projectDir.isDirectory()) {
            projectPath
        } else {
            context.getExternalFilesDir(null)?.absolutePath ?: "/data/data/${context.packageName}"
        }
        
        startTerminal(context, workingDir, "AccIDE - ${File(projectPath).name}")
    }
    
    /**
     * Start failsafe terminal session
     */
    fun startFailsafeTerminal(context: Context) {
        try {
            val intent = Intent(context, TerminalActivity::class.java).apply {
                putExtra("TERMUX_ACTIVITY.EXTRA_SESSION_WORKING_DIR", "/data/data/${context.packageName}")
                putExtra("TERMUX_ACTIVITY.EXTRA_SESSION_NAME", "AccIDE - Failsafe")
                putExtra("TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION", true)
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("TerminalUtils", "Failed to start failsafe terminal", e)
            Toast.makeText(context, "Failed to start terminal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
