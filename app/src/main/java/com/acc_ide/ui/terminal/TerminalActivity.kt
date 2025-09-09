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

package com.acc_ide.ui.terminal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Terminal Activity that launches the actual Termux terminal
 * This acts as a bridge to start the integrated Termux terminal
 * 
 * @author AccIDE Team
 */
class TerminalActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TerminalActivity"
        
        // Termux Activity constants from termux-am-library
        private const val TERMUX_PACKAGE = "com.acc_ide"
        private const val TERMUX_ACTIVITY = "com.termux.app.TermuxActivity"
        
        // Intent extras
        private const val EXTRA_SESSION_WORKING_DIR = "TERMUX_ACTIVITY.EXTRA_SESSION_WORKING_DIR"
        private const val EXTRA_SESSION_NAME = "TERMUX_ACTIVITY.EXTRA_SESSION_NAME"
        private const val EXTRA_FAILSAFE_SESSION = "TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "TerminalActivity starting...")
        
        // 首先检查并安装Termux bootstrap
        setupTermuxBootstrap()
    }
    
    private fun setupTermuxBootstrap() {
        Log.d(TAG, "Checking Termux bootstrap...")
        
        try {
            // 使用TermuxBootstrap检查并安装
            val bootstrapClass = Class.forName("com.termux.app.TermuxBootstrap")
            val downloadMethod = bootstrapClass.getMethod("downloadAndInstallBootstrap", 
                android.app.Activity::class.java, Runnable::class.java)
            
            downloadMethod.invoke(null, this) {
                Log.d(TAG, "Bootstrap setup completed, starting terminal...")
                startTermuxActivity()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup bootstrap", e)
            // 如果bootstrap安装失败，仍然尝试启动终端（可能会使用fallback）
            Toast.makeText(this, 
                "Bootstrap setup failed, using basic terminal environment", 
                Toast.LENGTH_SHORT).show()
            startTermuxActivity()
        }
    }
    
    private fun startTermuxActivity() {
        try {
            // 启动termux application模块中的TermuxActivity
            val termuxIntent = Intent().apply {
                setClassName(TERMUX_PACKAGE, TERMUX_ACTIVITY)
                
                // 传递所有从原intent接收的extras
                intent.extras?.let { extras ->
                    putExtras(extras)
                }
                
                // 设置默认工作目录（如果没有指定的话）- 使用内部存储确保权限正确
                if (!intent.hasExtra(EXTRA_SESSION_WORKING_DIR)) {
                    // 使用Termux标准路径：内部存储的home目录
                    val homeDir = File(filesDir, "home")
                    if (!homeDir.exists()) {
                        homeDir.mkdirs()
                    }
                    val workingDir = homeDir.absolutePath
                    putExtra(EXTRA_SESSION_WORKING_DIR, workingDir)
                    Log.d(TAG, "Set working directory: $workingDir")
                }
                
                // 设置会话名称（如果没有指定的话）  
                if (!intent.hasExtra(EXTRA_SESSION_NAME)) {
                    putExtra(EXTRA_SESSION_NAME, "AccIDE Terminal")
                }
                
                // 设置为非failsafe模式（除非明确指定）
                if (!intent.hasExtra(EXTRA_FAILSAFE_SESSION)) {
                    putExtra(EXTRA_FAILSAFE_SESSION, false)
                }
                
                // 添加启动标志
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            Log.d(TAG, "Starting Termux Activity: $TERMUX_ACTIVITY")
            startActivity(termuxIntent)
            
        } catch (e: Exception) {
            // 如果启动失败，显示错误信息
            Log.e(TAG, "Failed to start terminal", e)
            Toast.makeText(
                this, 
                "Failed to start terminal: ${e.message}\nTermux integration may not be fully configured.", 
                Toast.LENGTH_LONG
            ).show()
        }
        
        // 结束当前Activity，让Termux Activity接管
        finish()
    }
}
