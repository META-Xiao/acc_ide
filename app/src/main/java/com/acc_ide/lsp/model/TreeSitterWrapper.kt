package com.acc_ide.lsp.model

import android.util.Log

/**
 * Tree-sitter JNI包装类
 * 连接到自构建的Tree-sitter库，提供语法分析和查询功能
 */
object TreeSitterWrapper {
    private const val TAG = "TreeSitterWrapper"
    
    // 静态加载JNI库
    init {
        try {
            // 按依赖顺序加载库
            System.loadLibrary("tree-sitter")
            Log.d(TAG, "Successfully loaded tree-sitter library")
            
            System.loadLibrary("tree-sitter-jni")
            Log.d(TAG, "Successfully loaded tree-sitter-jni library")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load tree-sitter libraries", e)
        }
    }
    
    /**
     * 基础Tree-sitter语言类
     */
    abstract class TSLanguage {
        abstract fun getLanguageName(): String
        abstract fun getInstance(): Long
        open fun isAvailable(): Boolean = getInstance() != 0L
    }
    
    /**
     * Java语言支持
     */
    class TSLanguageJava : TSLanguage() {
        companion object {
            @JvmStatic
            external fun getNativePtr(): Long
            
            fun create(): TSLanguageJava {
                return TSLanguageJava()
            }
        }
        
        override fun getInstance(): Long {
            return Companion.getNativePtr()
        }
        
        override fun getLanguageName(): String = "java"
    }
    
    /**
     * C++语言支持
     */
    class TSLanguageCpp : TSLanguage() {
        companion object {
            @JvmStatic
            external fun getNativePtr(): Long
            
            fun create(): TSLanguageCpp {
                return TSLanguageCpp()
            }
        }
        
        override fun getInstance(): Long {
            return Companion.getNativePtr()
        }
        
        override fun getLanguageName(): String = "cpp"
    }
    
    /**
     * Python语言支持
     */
    class TSLanguagePython : TSLanguage() {
        companion object {
            @JvmStatic
            external fun getNativePtr(): Long
            
            fun create(): TSLanguagePython {
                return TSLanguagePython()
            }
        }
        
        override fun getInstance(): Long {
            return Companion.getNativePtr()
        }
        
        override fun getLanguageName(): String = "python"
    }
    
    /**
     * Tree-sitter查询类
     */
    class TSQuery(private val language: TSLanguage, private val queryString: String) {
        companion object {
            @JvmStatic
            external fun create(languagePtr: Long, queryString: String): Long
        }
        
        private val queryPtr: Long = create(language.getInstance(), queryString)
        
        fun isValid(): Boolean = queryPtr != 0L
        fun getLanguage(): TSLanguage = language
        fun getQueryString(): String = queryString
        fun getQueryPtr(): Long = queryPtr
        
        protected fun finalize() {
            if (queryPtr != 0L) {
                deleteQuery(queryPtr)
            }
        }
    }
    
    /**
     * Tree-sitter解析器类
     */
    class TSParser {
        private var parserPtr: Long = 0L
        private var currentLanguage: TSLanguage? = null
        
        init {
            parserPtr = createParser()
        }
        
        fun setLanguage(language: TSLanguage): Boolean {
            if (parserPtr == 0L) return false
            
            val result = setLanguage(parserPtr, language.getInstance())
            if (result) {
                currentLanguage = language
            }
            return result
        }
        
        fun parseString(sourceCode: String): TSTree? {
            if (parserPtr == 0L || currentLanguage == null) return null
            
            val treePtr = parseString(parserPtr, sourceCode)
            return if (treePtr != 0L) TSTree(treePtr) else null
        }
        
        fun reset() {
            // TODO: 实现解析器重置
        }
        
        fun close() {
            if (parserPtr != 0L) {
                deleteParser(parserPtr)
                parserPtr = 0L
            }
        }
        
        protected fun finalize() {
            close()
        }
    }
    
    /**
     * Tree-sitter语法树类
     */
    class TSTree(private val treePtr: Long) {
        fun getRootNode(): TSNode? {
            // TODO: 实现获取根节点
            return null
        }
        
        fun close() {
            if (treePtr != 0L) {
                deleteTree(treePtr)
            }
        }
        
        protected fun finalize() {
            close()
        }
    }
    
    /**
     * Tree-sitter节点类
     */
    class TSNode {
        // TODO: 实现节点功能
    }
    
    // =================================================================
    // JNI Native Methods
    // =================================================================
    
    @JvmStatic
    external fun createParser(): Long
    
    @JvmStatic
    external fun setLanguage(parserPtr: Long, languagePtr: Long): Boolean
    
    @JvmStatic
    external fun parseString(parserPtr: Long, sourceCode: String): Long
    
    @JvmStatic
    external fun deleteParser(parserPtr: Long)
    
    @JvmStatic
    external fun deleteTree(treePtr: Long)
    
    @JvmStatic
    external fun deleteQuery(queryPtr: Long)
    
    // =================================================================
    // 实用方法
    // =================================================================
    
    /**
     * 检查native库是否已加载
     */
    fun isNativeLibraryLoaded(): Boolean {
        return try {
            // 测试创建语言实例
            val javaLang = TSLanguageJava.create()
            val cppLang = TSLanguageCpp.create()
            val pythonLang = TSLanguagePython.create()
            
            val allLoaded = javaLang.isAvailable() && cppLang.isAvailable() && pythonLang.isAvailable()
            
            Log.d(TAG, "Native library status:")
            Log.d(TAG, "- Java: ${javaLang.getLanguageName()} (${javaLang.getInstance()})")
            Log.d(TAG, "- C++: ${cppLang.getLanguageName()} (${cppLang.getInstance()})")  
            Log.d(TAG, "- Python: ${pythonLang.getLanguageName()} (${pythonLang.getInstance()})")
            
            allLoaded
        } catch (e: Exception) {
            Log.w(TAG, "Error checking native library: ${e.message}")
            false
        }
    }
    
    /**
     * 获取支持的语言列表
     */
    fun getSupportedLanguages(): List<String> {
        return listOf("java", "cpp", "python")
    }
    
    /**
     * 检查特定语言是否支持
     */
    fun isLanguageSupported(language: String): Boolean {
        return getSupportedLanguages().contains(language.lowercase())
    }
    
    /**
     * 创建语言实例
     */
    fun createLanguage(languageName: String): TSLanguage? {
        return when (languageName.lowercase()) {
            "java" -> TSLanguageJava.create()
            "cpp", "c++" -> TSLanguageCpp.create()
            "python", "py" -> TSLanguagePython.create()
            else -> null
        }
    }
} 