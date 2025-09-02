package com.acc_ide.util

import android.content.Context
import android.os.Environment as AndroidEnvironment
import java.io.File
import java.util.*

/**
 * 环境工具类 - 基于AndroidIDE的Environment实现
 * 提供文件路径、临时目录等环境相关功能
 */
object Environment {
    
    private lateinit var context: Context
    
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
    }
    
    /**
     * 应用程序主目录
     */
    val HOME: File by lazy {
        File(context.filesDir, "home").apply { mkdirs() }
    }
    
    /**
     * 临时文件目录
     */
    val TMP_DIR: File by lazy {
        File(context.cacheDir, "tmp").apply { mkdirs() }
    }
    
    /**
     * Termux兼容的数据目录
     */
    val DATA_DIR: File by lazy {
        File(context.filesDir, "termux").apply { mkdirs() }
    }
    
    /**
     * 编译器工具链目录
     */
    val TOOLCHAIN_DIR: File by lazy {
        File(context.filesDir, "toolchain").apply { mkdirs() }
    }
    
    /**
     * 用户项目目录
     */
    val PROJECTS_DIR: File by lazy {
        File(HOME, "projects").apply { mkdirs() }
    }
    
    /**
     * 日志目录
     */
    val LOGS_DIR: File by lazy {
        File(context.filesDir, "logs").apply { mkdirs() }
    }
    
    /**
     * 创建临时文件
     */
    fun createTempFile(prefix: String = "tmp", suffix: String = ""): File {
        TMP_DIR.mkdirs()
        return File.createTempFile(prefix, suffix, TMP_DIR)
    }
    
    /**
     * 创建临时目录
     */
    fun createTempDir(prefix: String = "tmp"): File {
        TMP_DIR.mkdirs()
        val tempDir = File(TMP_DIR, "${prefix}_${System.currentTimeMillis()}_${Random().nextInt(1000)}")
        tempDir.mkdirs()
        return tempDir
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
     * 获取外部存储目录
     */
    fun getExternalStorageDir(): File? {
        return if (AndroidEnvironment.getExternalStorageState() == AndroidEnvironment.MEDIA_MOUNTED) {
            AndroidEnvironment.getExternalStorageDirectory()
        } else {
            null
        }
    }
    
    /**
     * 获取应用专用外部存储目录
     */
    fun getExternalFilesDir(): File? {
        return context.getExternalFilesDir(null)
    }
    
    /**
     * 清理临时文件
     */
    fun cleanupTempFiles() {
        try {
            if (TMP_DIR.exists()) {
                TMP_DIR.listFiles()?.forEach { file ->
                    if (file.isFile && System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                        // 删除24小时前的临时文件
                        file.delete()
                    } else if (file.isDirectory) {
                        // 清理空的临时目录
                        if (file.listFiles()?.isEmpty() == true) {
                            file.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }
    
    /**
     * 获取系统架构信息
     */
    fun getArch(): String {
        val arch = System.getProperty("os.arch") ?: "unknown"
        return when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64-v8a"
            arch.contains("arm") -> "armeabi-v7a"
            arch.contains("x86_64") -> "x86_64"
            arch.contains("x86") -> "x86"
            else -> "arm64-v8a" // 默认为arm64
        }
    }
    
    /**
     * 检查是否为Android环境
     */
    fun isAndroid(): Boolean = true
    
    /**
     * 获取环境变量
     */
    fun getEnvironmentVariables(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        
        // 基础环境变量
        env["HOME"] = HOME.absolutePath
        env["TMPDIR"] = TMP_DIR.absolutePath
        env["PATH"] = buildPath()
        env["LD_LIBRARY_PATH"] = buildLibraryPath()
        
        // Termux兼容变量
        env["PREFIX"] = DATA_DIR.absolutePath
        env["TERMUX_VERSION"] = "0.118.0"
        
        // 编译器相关
        env["CC"] = "clang"
        env["CXX"] = "clang++"
        env["CFLAGS"] = "-I${DATA_DIR.absolutePath}/include"
        env["CXXFLAGS"] = "-I${DATA_DIR.absolutePath}/include"
        env["LDFLAGS"] = "-L${DATA_DIR.absolutePath}/lib"
        
        return env
    }
    
    /**
     * 构建PATH环境变量
     */
    private fun buildPath(): String {
        val paths = mutableListOf<String>()
        
        // 添加工具链路径
        paths.add("${TOOLCHAIN_DIR.absolutePath}/bin")
        paths.add("${DATA_DIR.absolutePath}/bin")
        
        // 添加系统路径
        paths.add("/system/bin")
        paths.add("/system/xbin")
        paths.add("/vendor/bin")
        
        return paths.joinToString(":")
    }
    
    /**
     * 构建LD_LIBRARY_PATH环境变量
     */
    private fun buildLibraryPath(): String {
        val paths = mutableListOf<String>()
        
        paths.add("${DATA_DIR.absolutePath}/lib")
        paths.add("${TOOLCHAIN_DIR.absolutePath}/lib")
        paths.add("/system/lib64")
        paths.add("/system/lib")
        paths.add("/vendor/lib64")
        paths.add("/vendor/lib")
        
        return paths.joinToString(":")
    }
}