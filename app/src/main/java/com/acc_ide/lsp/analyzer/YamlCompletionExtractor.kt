package com.acc_ide.lsp.analyzer

import android.content.Context
import android.util.Log
import com.acc_ide.lsp.model.CompletionItem
import com.acc_ide.lsp.model.CompletionItemKind
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader
import java.util.Locale

/**
 * YAML补全配置解析器
 * 支持多语言配置文件，根据当前系统语言自动选择
 */
class YamlCompletionExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "YamlCompletionExtractor"
        private const val DEFAULT_LOCALE = "en"
        private const val COMPLETION_CONFIG_PATH = "completion"
    }
    
    private val yaml = Yaml()
    
    /**
     * 从YAML配置文件中提取补全数据
     */
    fun extractCompletions(language: String): List<CompletionItem> {
        return try {
            val currentLocale = getCurrentLocale()
            val normalizedLanguage = normalizeLanguageName(language)
            val configPath = "$COMPLETION_CONFIG_PATH/$currentLocale/$normalizedLanguage.yml"
            
            Log.d(TAG, "Loading completion config: $configPath")
            
            val completions = loadCompletionConfig(configPath)
            Log.d(TAG, "Loaded ${completions.size} completions for $language in locale $currentLocale")
            
            completions
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading YAML completions for $language", e)
            // 失败时尝试默认语言
            tryLoadDefaultConfig(language)
        }
    }
    
    /**
     * 标准化语言名称，处理别名
     */
    private fun normalizeLanguageName(language: String): String {
        return when (language.lowercase()) {
            "py" -> "python"
            "c" -> "cpp"
            else -> language.lowercase()
        }
    }
    
    /**
     * 加载补全配置文件
     */
    private fun loadCompletionConfig(configPath: String): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        try {
            val inputStream = context.assets.open(configPath)
            val reader = InputStreamReader(inputStream, "UTF-8")
            
            @Suppress("UNCHECKED_CAST")
            val config = yaml.load(reader) as Map<String, Any>
            
            // 动态解析所有类别的补全项（除了元数据字段）
            val metadataFields = setOf("language", "locale", "version")
            config.keys.forEach { categoryKey ->
                if (categoryKey !in metadataFields) {
                    parseCompletionCategory(config[categoryKey], completions)
                }
            }
            
            reader.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config from $configPath", e)
            throw e
        }
        
        return completions
    }
    
    /**
     * 解析补全项类别
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseCompletionCategory(category: Any?, completions: MutableList<CompletionItem>) {
        if (category !is List<*>) return
        
        category.forEach { item ->
            if (item is Map<*, *>) {
                try {
                    val itemMap = item as Map<String, Any>
                    val label = itemMap["label"] as? String ?: ""
                    val insertText = itemMap["insert_text"] as? String ?: label
                    val detail = itemMap["detail"] as? String ?: ""
                    val kindStr = itemMap["kind"] as? String ?: "text"
                    val priority = (itemMap["priority"] as? Number)?.toInt() ?: 10
                    
                    val kind = mapKindStringToInt(kindStr)
                    
                    if (label.isNotEmpty()) {
                        completions.add(CompletionItem(
                            label = label,
                            insertText = insertText,
                            detail = detail,
                            kind = kind
                        ))
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing completion item: $item", e)
                }
            }
        }
    }
    
    /**
     * 将字符串类型转换为LSP CompletionItemKind常量
     */
    private fun mapKindStringToInt(kindStr: String): Int {
        return when (kindStr.lowercase()) {
            "text" -> CompletionItemKind.TEXT
            "method" -> CompletionItemKind.METHOD
            "function" -> CompletionItemKind.FUNCTION
            "constructor" -> CompletionItemKind.CONSTRUCTOR
            "field" -> CompletionItemKind.FIELD
            "variable" -> CompletionItemKind.VARIABLE
            "class" -> CompletionItemKind.CLASS
            "interface" -> CompletionItemKind.INTERFACE
            "module" -> CompletionItemKind.MODULE
            "property" -> CompletionItemKind.PROPERTY
            "unit" -> CompletionItemKind.UNIT
            "value" -> CompletionItemKind.VALUE
            "enum" -> CompletionItemKind.ENUM
            "keyword" -> CompletionItemKind.KEYWORD
            "snippet" -> CompletionItemKind.SNIPPET
            "color" -> CompletionItemKind.COLOR
            "file" -> CompletionItemKind.FILE
            "reference" -> CompletionItemKind.REFERENCE
            "folder" -> CompletionItemKind.FOLDER
            "enum_member" -> CompletionItemKind.ENUM_MEMBER
            "constant" -> CompletionItemKind.CONSTANT
            "struct" -> CompletionItemKind.STRUCT
            "event" -> CompletionItemKind.EVENT
            "operator" -> CompletionItemKind.OPERATOR
            "type_parameter" -> CompletionItemKind.TYPE_PARAMETER
            else -> CompletionItemKind.TEXT
        }
    }
    
    /**
     * 获取当前系统语言
     */
    private fun getCurrentLocale(): String {
        val locale = Locale.getDefault()
        return when (locale.language) {
            "zh" -> "zh"  // 中文
            "en" -> "en"  // 英文
            else -> DEFAULT_LOCALE  // 默认英文
        }
    }
    
    /**
     * 尝试加载默认语言配置
     */
    private fun tryLoadDefaultConfig(language: String): List<CompletionItem> {
        return try {
            val normalizedLanguage = normalizeLanguageName(language)
            val defaultConfigPath = "$COMPLETION_CONFIG_PATH/$DEFAULT_LOCALE/$normalizedLanguage.yml"
            Log.w(TAG, "Falling back to default config: $defaultConfigPath")
            loadCompletionConfig(defaultConfigPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load default config for $language", e)
            emptyList()
        }
    }
    
    /**
     * 检查配置文件是否存在
     */
    fun hasConfigForLanguage(language: String): Boolean {
        val currentLocale = getCurrentLocale()
        val normalizedLanguage = normalizeLanguageName(language)
        val configPath = "$COMPLETION_CONFIG_PATH/$currentLocale/$normalizedLanguage.yml"
        
        return try {
            context.assets.open(configPath).use { true }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取支持的语言列表
     */
    fun getSupportedLanguages(): List<String> {
        val supportedLanguages = mutableListOf<String>()
        val currentLocale = getCurrentLocale()
        val configDir = "$COMPLETION_CONFIG_PATH/$currentLocale"
        
        try {
            val files = context.assets.list(configDir) ?: emptyArray()
            files.forEach { filename ->
                if (filename.endsWith(".yml")) {
                    val language = filename.removeSuffix(".yml")
                    supportedLanguages.add(language)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing supported languages", e)
        }
        
        return supportedLanguages
    }
} 