package com.acc_ide.util

import android.content.Context
import android.util.Log

/**
 * 管理原生库的加载
 * 使用jniLibs目录中的自构建Tree-sitter库
 */
class NativeLibraryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "NativeLibraryManager"
        
        // 需要加载的库（按依赖顺序）
        private val REQUIRED_LIBRARIES = arrayOf(
            "tree-sitter",           // 核心库
            "tree-sitter-java",      // Java语法解析
            "tree-sitter-cpp",       // C++语法解析  
            "tree-sitter-python"     // Python语法解析
        )
    }
    
    private var librariesLoaded = false
    
    /**
     * 初始化并加载所有原生库
     */
    fun initializeNativeLibraries(): Boolean {
        if (librariesLoaded) {
            Log.d(TAG, "Native libraries already loaded")
            return true
        }
        
        return try {
            // 按依赖顺序加载库
            loadNativeLibrariesInOrder()
            librariesLoaded = true
            Log.d(TAG, "All native libraries loaded successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native libraries", e)
            false
        }
    }
    
    /**
     * 按依赖顺序加载原生库
     */
    private fun loadNativeLibrariesInOrder() {
        for (libName in REQUIRED_LIBRARIES) {
            try {
                System.loadLibrary(libName)
                Log.d(TAG, "Loaded lib$libName.so")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Failed to load lib$libName.so: ${e.message}")
                // 对于可选的语言库，继续尝试加载其他库
                if (libName == "tree-sitter") {
                    // 核心库必须成功加载
                    throw e
                }
            }
        }
    }
    
    /**
     * 检查所有必需的库是否可用
     */
    fun areLibrariesAvailable(): Boolean {
        return librariesLoaded || initializeNativeLibraries()
    }
    
    /**
     * 清理操作 - 系统管理的库无需手动清理
     */
    fun cleanupLibraries() {
        Log.d(TAG, "Libraries are managed by system, no cleanup needed")
    }
    
    /**
     * 检查特定语言库是否可用
     */
    fun isLanguageSupported(language: String): Boolean {
        val libName = "tree-sitter-$language"
        return REQUIRED_LIBRARIES.contains(libName)
    }
} 