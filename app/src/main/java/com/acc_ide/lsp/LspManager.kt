package com.acc_ide.lsp

import android.content.Context
import android.util.Log
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * LSP管理器 - 负责管理语言服务器协议的集成
 * LSP Manager - Responsible for managing Language Server Protocol integration
 */
class LspManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "LspManager"
        private var instance: LspManager? = null
        
        fun getInstance(context: Context): LspManager {
            return instance ?: synchronized(this) {
                instance ?: LspManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // LSP服务器端口缓存
    private val languageServerPorts = ConcurrentHashMap<String, Int>()
    private val activeEditors = ConcurrentHashMap<String, CodeEditor>()
    
    /**
     * 检查指定语言是否支持LSP
     */
    fun isLanguageSupported(language: String): Boolean {
        return when (language.lowercase()) {
            "java", "cpp", "c", "python", "py" -> true
            else -> false
        }
    }
    
    /**
     * 为编辑器启用LSP支持
     */
    fun enableLspForEditor(editor: CodeEditor, language: String): Boolean {
        if (!isLanguageSupported(language)) {
            Log.w(TAG, "Language not supported for LSP: $language")
            return false
        }
        
        Log.d(TAG, "Enabling LSP support for $language")
        
        try {
            // 在后台线程中设置LSP
            CoroutineScope(Dispatchers.IO).launch {
                setupLspAsync(editor, language)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable LSP for $language", e)
            return false
        }
    }
    
    /**
     * 异步设置LSP支持
     */
    private suspend fun setupLspAsync(editor: CodeEditor, language: String) {
        try {
            val port = getAvailablePort()
            languageServerPorts[language] = port
            
            // 1. 尝试启动语言服务器
            val serverStarted = LanguageServerStarter.startLanguageServer(language, port)
            
            if (!serverStarted) {
                // 检查是否有外部服务器在运行
                if (!LanguageServerStarter.checkExternalLspServer(port)) {
                    Log.w(TAG, "No LSP server available for $language, falling back to TextMate")
                    // 回退到TextMate
                    withContext(Dispatchers.Main) {
                        setupTextMateLanguage(editor, language)
                    }
                    return
                }
            }
            
            // 2. 尝试使用LSP功能 (这里是模拟)
            Log.d(TAG, "LSP server setup initiated for $language on port $port")
            
            // TODO: 真正的LSP集成
            // 由于editor-lsp的API可能有版本差异，我们先用TextMate作为基础
            // 然后在未来版本中添加真正的LSP功能
            
            // 3. 在主线程中设置编辑器
            withContext(Dispatchers.Main) {
                setupEnhancedTextMateLanguage(editor, language)
                activeEditors[editor.toString()] = editor
            }
            
            Log.d(TAG, "Enhanced language support enabled for $language")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up LSP for $language", e)
            
            // 回退到基本TextMate
            withContext(Dispatchers.Main) {
                setupTextMateLanguage(editor, language)
            }
        }
    }
    
    /**
     * 设置增强的TextMate语言支持 (为LSP做准备)
     */
    private fun setupEnhancedTextMateLanguage(editor: CodeEditor, language: String) {
        val scopeName = when (language.lowercase()) {
            "java" -> "source.java"
            "cpp", "c" -> "source.cpp"
            "python", "py" -> "source.python"
            else -> "text.plain"
        }
        
        try {
            // 创建启用自动补全的TextMate语言
            val textMateLanguage = TextMateLanguage.create(scopeName, true)
            editor.setEditorLanguage(textMateLanguage)
            
            Log.d(TAG, "Enhanced TextMate language set for $language with scope: $scopeName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create enhanced TextMate language", e)
            setupTextMateLanguage(editor, language)
        }
    }
    
    /**
     * 设置基本TextMate语言支持
     */
    private fun setupTextMateLanguage(editor: CodeEditor, language: String) {
        val scopeName = when (language.lowercase()) {
            "java" -> "source.java"
            "cpp", "c" -> "source.cpp"
            "python", "py" -> "source.python"
            else -> "text.plain"
        }
        
        try {
            val textMateLanguage = TextMateLanguage.create(scopeName, false)
            editor.setEditorLanguage(textMateLanguage)
            
            Log.d(TAG, "Basic TextMate language set for $language")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TextMate language", e)
        }
    }
    
    /**
     * 获取项目路径
     */
    private fun getProjectPath(): String {
        return context.filesDir.absolutePath + "/lsp_workspace"
    }
    
    /**
     * 创建工作区目录
     */
    private fun createWorkspaceDir(): File {
        val workspaceDir = File(getProjectPath())
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs()
        }
        return workspaceDir
    }
    
    /**
     * 获取可用端口
     */
    private fun getAvailablePort(): Int {
        return try {
            val serverSocket = ServerSocket(0)
            val port = serverSocket.localPort
            serverSocket.close()
            port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available port", e)
            9999 + (1..1000).random() // 随机端口避免冲突
        }
    }
    
    /**
     * 禁用编辑器的LSP支持
     */
    fun disableLspForEditor(editor: CodeEditor, language: String) {
        Log.d(TAG, "Disabling LSP support for $language")
        
        activeEditors.remove(editor.toString())
        
        // 恢复到基本TextMate
        setupTextMateLanguage(editor, language)
    }
    
    /**
     * 获取LSP服务器状态
     */
    fun getLspServerStatus(language: String): LspServerStatus {
        return when {
            !isLanguageSupported(language) -> LspServerStatus.NOT_SUPPORTED
            languageServerPorts.containsKey(language) -> {
                val port = languageServerPorts[language]!!
                if (LanguageServerStarter.checkExternalLspServer(port)) {
                    LspServerStatus.RUNNING
                } else {
                    LspServerStatus.STOPPED
                }
            }
            else -> LspServerStatus.STOPPED
        }
    }
    
    /**
     * 获取活跃的LSP端口
     */
    fun getLspPort(language: String): Int? {
        return languageServerPorts[language]
    }
    
    /**
     * 清理所有LSP资源
     */
    fun dispose() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                activeEditors.clear()
                languageServerPorts.clear()
                
                Log.d(TAG, "LSP Manager disposed")
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing LSP Manager", e)
            }
        }
    }
    
    /**
     * LSP服务器状态枚举
     */
    enum class LspServerStatus {
        NOT_SUPPORTED,  // 不支持
        STOPPED,        // 已停止
        STARTING,       // 启动中
        RUNNING,        // 运行中
        ERROR           // 错误
    }
} 