/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.acc_ide.termux.ide.utils

import android.content.Context
import android.os.Environment as AndroidEnvironment
import java.io.File
import java.util.*

/**
 * 环境工具类 - 基于AndroidIDE的Environment实现
 * 提供文件路径、临时目录等环境相关功能
 */
object Environment {
    
    private var context: Context? = null
    
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
    }
    
    /**
     * 检查目录是否存在，不存在则创建
     */
    fun mkdirIfNotExits(dir: File) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }
    
    /**
     * 应用程序主目录
     */
    val HOME: File by lazy {
        val ctx = context ?: throw IllegalStateException("Environment not initialized")
        File(ctx.filesDir, "home").apply { mkdirs() }
    }
    
    /**
     * 临时文件目录
     */
    val TMP_DIR: File by lazy {
        val ctx = context ?: throw IllegalStateException("Environment not initialized")
        File(ctx.cacheDir, "tmp").apply { mkdirs() }
    }
    
    /**
     * 创建临时文件
     */
    fun createTempFile(prefix: String = "tmp", suffix: String = ""): File {
        val ctx = context ?: throw IllegalStateException("Environment not initialized")
        val tempDir = File(AndroidEnvironment.getExternalStorageDirectory(), "Android/data/${ctx.packageName}/files/tmp")
        tempDir.mkdirs()
        return File.createTempFile(prefix, suffix, tempDir)
    }
}