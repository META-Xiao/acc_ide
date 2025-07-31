package com.acc_ide.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 管理原生库的加载，从assets中提取.so文件
 * 解决16KB页面大小兼容性问题
 */
class NativeLibraryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "NativeLibraryManager"
        private const val NATIVE_DIR = "native"
            private val REQUIRED_LIBRARIES = arrayOf(
        "libtree-sitter.so",
        "libtree-sitter-java.so",
        "libtree-sitter-cpp.so",
        "libtree-sitter-python.so"
    )
    }
    
    private val nativeLibDir: File by lazy {
        File(context.filesDir, "native_libs").apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * 初始化并加载所有原生库
     */
    fun initializeNativeLibraries(): Boolean {
        return try {
            // 1. 提取原生库文件
            if (!extractNativeLibraries()) {
                Log.e(TAG, "Failed to extract native libraries")
                return false
            }
            
            // 2. 按顺序加载库
            loadNativeLibrariesInOrder()
            
            Log.d(TAG, "All native libraries loaded successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native libraries", e)
            false
        }
    }
    
    /**
     * 从assets提取原生库到内部存储
     */
    private fun extractNativeLibraries(): Boolean {
        val abi = getDeviceAbi()
        Log.d(TAG, "Device ABI: $abi")
        
        return try {
            for (libName in REQUIRED_LIBRARIES) {
                val assetPath = "$NATIVE_DIR/$abi/$libName"
                val targetFile = File(nativeLibDir, libName)
                
                // 检查文件是否已存在且是最新的
                if (targetFile.exists() && isLibraryUpToDate(targetFile)) {
                    Log.d(TAG, "Library $libName already exists and is up to date")
                    continue
                }
                
                // 从assets复制文件
                if (copyAssetToFile(assetPath, targetFile)) {
                    Log.d(TAG, "Extracted $libName successfully")
                } else {
                    Log.w(TAG, "Library $libName not found in assets, skipping")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting native libraries", e)
            false
        }
    }
    
    /**
     * 按依赖顺序加载原生库
     */
    private fun loadNativeLibrariesInOrder() {
        // 按照依赖关系顺序加载
        val loadOrder = arrayOf(
            "libtree-sitter.so",
            "libandroid-tree-sitter.so",
            "libtree-sitter-java.so", 
            "libtree-sitter-cpp.so",
            "libtree-sitter-python.so"
        )
        
        for (libName in loadOrder) {
            val libFile = File(nativeLibDir, libName)
            if (libFile.exists()) {
                try {
                    System.load(libFile.absolutePath)
                    Log.d(TAG, "Loaded $libName")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "Failed to load $libName: ${e.message}")
                    // 继续尝试加载其他库
                }
            } else {
                Log.w(TAG, "Library $libName not found, skipping")
            }
        }
    }
    
    /**
     * 从assets复制文件到目标位置
     */
    private fun copyAssetToFile(assetPath: String, targetFile: File): Boolean {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // 设置可执行权限
            targetFile.setExecutable(true)
            true
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset $assetPath", e)
            false
        }
    }
    
    /**
     * 获取设备ABI
     */
    private fun getDeviceAbi(): String {
        val supportedAbis = android.os.Build.SUPPORTED_ABIS
        
        // 优先选择64位架构
        for (abi in supportedAbis) {
            when (abi) {
                "arm64-v8a" -> return "arm64-v8a"
                "x86_64" -> return "x86_64"
            }
        }
        
        // 回退到32位架构
        for (abi in supportedAbis) {
            when (abi) {
                "armeabi-v7a" -> return "armeabi-v7a"
                "x86" -> return "x86"
            }
        }
        
        // 默认使用arm64-v8a
        return "arm64-v8a"
    }
    
    /**
     * 检查库文件是否为最新版本
     */
    private fun isLibraryUpToDate(libFile: File): Boolean {
        // 简单的时间戳检查，可以根据需要改进
        val appInstallTime = try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        
        return libFile.lastModified() >= appInstallTime
    }
    
    /**
     * 检查所有必需的库是否可用
     */
    fun areLibrariesAvailable(): Boolean {
        return REQUIRED_LIBRARIES.all { libName ->
            File(nativeLibDir, libName).exists()
        }
    }
    
    /**
     * 清理提取的库文件
     */
    fun cleanupLibraries() {
        try {
            if (nativeLibDir.exists()) {
                nativeLibDir.deleteRecursively()
                Log.d(TAG, "Cleaned up native libraries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up libraries", e)
        }
    }
} 