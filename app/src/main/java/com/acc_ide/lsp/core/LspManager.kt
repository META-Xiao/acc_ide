package com.acc_ide.lsp.core

import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.dsl.languages
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import io.github.rosemoe.sora.lsp.client.connection.SocketStreamConnectionProvider
import io.github.rosemoe.sora.lsp.client.languageserver.serverdefinition.CustomLanguageServerDefinition
import io.github.rosemoe.sora.lsp.client.languageserver.wrapper.EventHandler
import io.github.rosemoe.sora.lsp.editor.LspEditor
import io.github.rosemoe.sora.lsp.editor.LspProject
import io.github.rosemoe.sora.lsp.editor.LspLanguage
import com.acc_ide.lsp.analyzer.TreeSitterAnalyzer
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * LSP管理器 - 使用Tree-sitter和TextMate提供语法高亮和智能补全
 * LSP Manager - Using Tree-sitter and TextMate for syntax highlighting and intelligent completion
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
    
    // Tree-sitter分析器
    private val treeAnalyzer = TreeSitterAnalyzer(context)
    
    // LSP项目和编辑器缓存（保留用于向后兼容）
    private var lspProject: LspProject? = null
    private val lspEditors = ConcurrentHashMap<String, LspEditor>()
    private val activeEditors = ConcurrentHashMap<String, CodeEditor>()
    private val languageServerPorts = ConcurrentHashMap<String, Int>()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 记录是否已经初始化语法注册表
    private var isGrammarRegistryInitialized = false

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
        
        Log.d(TAG, "Enabling Tree-sitter and TextMate support for $language")
        
        scope.launch {
            // 确保语法注册表已初始化
            ensureGrammarRegistryInitialized()
            // 使用内置的 Tree-sitter 分析器而不是外部 LSP 服务器
            setupTreeSitterEditor(editor, language)
        }
        return true
    }
    
    /**
     * 确保语法注册表已初始化
     */
    private suspend fun ensureGrammarRegistryInitialized() = withContext(Dispatchers.IO) {
        if (!isGrammarRegistryInitialized) {
            try {
                // 注册文件提供器
                FileProviderRegistry.getInstance().addFileProvider(
                    AssetsFileResolver(context.assets)
                )
                
                // 加载语法配置
                GrammarRegistry.getInstance().loadGrammars(
                    languages {
                        language("java") {
                            grammar = "textmate/languages/java/syntaxes/java.tmLanguage.json"
                            scopeName = "source.java"
                            languageConfiguration = "textmate/languages/java/language-configuration.json"
                        }
                        language("cpp") {
                            grammar = "textmate/languages/cpp/syntaxes/cpp.tmLanguage.json"
                            scopeName = "source.cpp"
                            languageConfiguration = "textmate/languages/cpp/language-configuration.json"
                        }
                        language("python") {
                            grammar = "textmate/languages/python/syntaxes/python.tmLanguage.json"
                            scopeName = "source.python"
                            languageConfiguration = "textmate/languages/python/language-configuration.json"
                        }
                    }
                )
                
                // 设置TextMate颜色主题
                ensureTextMateTheme()
                
                isGrammarRegistryInitialized = true
                Log.d(TAG, "Grammar registry initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize grammar registry", e)
            }
        }
    }
    
    /**
     * 确保TextMate主题已设置
     */
    private fun ensureTextMateTheme() {
        try {
            val themeRegistry = ThemeRegistry.getInstance()
            
            // 加载默认主题
            val themePath = "textmate/themes/dark.json"
            themeRegistry.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream(themePath), 
                        themePath, 
                        null
                    ), 
                    "dark-theme"
                )
            )
            
            // 设置为当前主题
            themeRegistry.setTheme("dark-theme")
            
            Log.d(TAG, "TextMate theme loaded: $themePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TextMate theme", e)
        }
    }
    
    /**
     * 设置Tree-sitter编辑器支持
     */
    private suspend fun setupTreeSitterEditor(editor: CodeEditor, language: String) {
        try {
            withContext(Dispatchers.Main) {
                // 初始化Tree-sitter分析器
                if (!treeAnalyzer.initializeParser(language)) {
                    Log.w(TAG, "Tree-sitter initialization failed for $language, using TextMate only")
                    setupTextMateLanguage(editor, language)
                    return@withContext
                }
                
                // 尝试获取Tree-sitter语言
                val tsLanguage = treeAnalyzer.getTsLanguage(language)
                if (tsLanguage != null) {
                    Log.d(TAG, "Using Tree-sitter language for $language")
                    editor.setEditorLanguage(tsLanguage)
                    
                    // 设置TextMate颜色方案作为回退
                    try {
                        val colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                        editor.colorScheme = colorScheme
                        Log.d(TAG, "TextMate color scheme applied to Tree-sitter editor")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to apply TextMate color scheme", e)
                    }
                } else {
                    Log.d(TAG, "Tree-sitter language not available for $language, using TextMate")
                    setupTextMateLanguage(editor, language)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup Tree-sitter editor for $language", e)
            // 回退到TextMate
            withContext(Dispatchers.Main) {
                setupTextMateLanguage(editor, language)
            }
        }
    }
    

    

    

    

    
    /**
     * 创建TextMate语言
     */
    private fun createTextMateLanguage(language: String): TextMateLanguage {
        val scopeName = getScopeNameForLanguage(language)
        return TextMateLanguage.create(scopeName, true)
    }
    
    /**
     * 设置基本TextMate语言支持
     */
    private fun setupTextMateLanguage(editor: CodeEditor, language: String) {
        val scopeName = getScopeNameForLanguage(language)
        
        try {
            val textMateLanguage = TextMateLanguage.create(scopeName, true)
            editor.setEditorLanguage(textMateLanguage)
            
            // 设置TextMate颜色方案
            val colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            editor.colorScheme = colorScheme
            
            Log.d(TAG, "TextMate language set for $language")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TextMate language", e)
        }
    }
    

    
    /**
     * 生成文档URI
     */
    private fun generateDocumentUri(language: String): String {
        val timestamp = System.currentTimeMillis()
        val extension = when (language.lowercase()) {
            "java" -> "java"
            "cpp", "c" -> "cpp"
            "python", "py" -> "py"
            else -> "txt"
        }
        return "${getProjectPath()}/document_${timestamp}.$extension"
    }
    
    /**
     * 获取语言的作用域名称
     */
    private fun getScopeNameForLanguage(language: String): String {
        return when (language.lowercase()) {
            "java" -> "source.java"
            "cpp", "c" -> "source.cpp"
            "python", "py" -> "source.python"
            else -> "text.plain"
        }
    }
    
    /**
     * 获取项目路径
     */
    private fun getProjectPath(): String {
        val projectDir = File(context.filesDir, "lsp_workspace")
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir.absolutePath
    }
    
    /**
     * 禁用编辑器的LSP支持
     */
    fun disableLspForEditor(editor: CodeEditor, language: String) {
        Log.d(TAG, "Disabling LSP support for $language")
        
        val editorKey = editor.toString()
        
        scope.launch {
            try {
                // 断开LSP编辑器连接并清理
                lspEditors[editorKey]?.let { lspEditor ->
                    lspEditor.dispose()
                    lspEditors.remove(editorKey)
                }
                
                activeEditors.remove(editorKey)
                
                // 在主线程中恢复到基本TextMate
                withContext(Dispatchers.Main) {
                    setupTextMateLanguage(editor, language)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling LSP", e)
            }
        }
    }
    
    /**
     * 获取LSP服务器状态
     */
    fun getLspServerStatus(language: String): LspServerStatus {
        return when {
            !isLanguageSupported(language) -> LspServerStatus.NOT_SUPPORTED
            languageServerPorts.containsKey(language) -> {
                val port = languageServerPorts[language]!!
                if (checkExternalLspServer(port)) {
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
     * 检查是否有LSP服务器在运行
     */
    private fun checkExternalLspServer(port: Int): Boolean {
        return try {
            val socket = java.net.Socket("localhost", port)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 清理所有LSP资源
     */
    fun dispose() {
        scope.launch {
            try {
                // 清理所有LSP编辑器
                lspEditors.values.forEach { lspEditor ->
                    lspEditor.dispose()
                }
                lspEditors.clear()
                
                // 清理LSP项目
                lspProject?.dispose()
                lspProject = null
                
                activeEditors.clear()
                languageServerPorts.clear()
                
                // 清理Tree-sitter分析器资源
                treeAnalyzer.dispose()
                

                
                Log.d(TAG, "LSP Manager disposed")
            } catch (e: Exception) {
                Log.e(TAG, "Error disposing LSP Manager", e)
            }
        }
    }
    
    /**
     * LSP事件监听器
     */
    private class LspEventListener : EventHandler.EventListener {
        override fun initialize(server: org.eclipse.lsp4j.services.LanguageServer?, result: org.eclipse.lsp4j.InitializeResult) {
            Log.d(TAG, "LSP server initialized with capabilities: ${result.capabilities}")
        }
    }
    
    /**
     * 服务器连接提供器
     */
    private class ServerConnectProvider(
        private val provider: () -> SocketStreamConnectionProvider
    ) : () -> SocketStreamConnectionProvider {
        override fun invoke(): SocketStreamConnectionProvider = provider()
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