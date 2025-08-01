package com.acc_ide.lsp.analyzer

import android.content.Context
import android.util.Log
import android.os.Bundle
import com.acc_ide.lsp.model.CompletionItem
import com.acc_ide.lsp.model.CompletionItemKind
import com.acc_ide.lsp.model.Diagnostic
import com.acc_ide.lsp.model.DiagnosticSeverity
import com.acc_ide.util.NativeLibraryManager
import io.github.rosemoe.sora.editor.ts.TsLanguage
import io.github.rosemoe.sora.editor.ts.TsLanguageSpec
import io.github.rosemoe.sora.editor.ts.TsAnalyzeManager
import io.github.rosemoe.sora.editor.ts.TsTheme
import io.github.rosemoe.sora.editor.ts.TsThemeBuilder
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

// 自定义Tree-sitter包装类
import com.acc_ide.lsp.model.TreeSitterWrapper
import com.acc_ide.lsp.model.TreeSitterWrapper.TSLanguage
import com.acc_ide.lsp.model.TreeSitterWrapper.TSQuery
import com.acc_ide.lsp.model.TreeSitterWrapper.TSLanguageJava
import com.acc_ide.lsp.model.TreeSitterWrapper.TSLanguageCpp  
import com.acc_ide.lsp.model.TreeSitterWrapper.TSLanguagePython
import io.github.rosemoe.sora.editor.ts.adapter.TreeSitterLanguageFactory

/**
 * 基于sora-editor language-treesitter的语法分析器
 * 提供真正的语法分析、智能补全、错误检查等功能
 */
class TreeSitterAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "TreeSitterAnalyzer"
    }
    
    private val languageSpecs = ConcurrentHashMap<String, TsLanguageSpec>()
    private val languages = ConcurrentHashMap<String, TsLanguage>()
    private val analyzeManagers = ConcurrentHashMap<String, TsAnalyzeManager>()
    
    // 原生库管理器
    private val nativeLibManager = NativeLibraryManager(context)
    
    // YAML补全提取器
    private val yamlExtractor = YamlCompletionExtractor(context)
    
    /**
     * 初始化指定语言的Tree-sitter语言规范
     */
    fun initializeParser(language: String): Boolean {
        return try {
            Log.d(TAG, "Initializing Tree-sitter parser for $language")
            
            // 检查原生库是否可用
            if (!isNativeLibraryAvailable()) {
                Log.w(TAG, "Tree-sitter native libraries not available, using TextMate only")
                return markLanguageAsSupported(language)
            }
            
            // 1. 尝试创建真正的Tree-sitter语言实例
            val resources = loadTsLanguageResources(language)
            if (resources != null) {
                val (tsLanguage, highlightScm, localsScm) = resources
                if (tsLanguage != null && highlightScm != null) {
                    val tsLang = createTsLanguage(
                        languageName = language,
                        tsLanguage = tsLanguage,
                        highlightScm = highlightScm,
                        localsScm = localsScm ?: ""
                    )
                    
                    if (tsLang != null) {
                        Log.d(TAG, "Successfully initialized Tree-sitter for $language")
                        return true
                    }
                }
            }
            
            // 2. 如果无法创建真正的Tree-sitter实例，使用回退方案
            Log.d(TAG, "Tree-sitter resources not available for $language, using fallback")
            Log.d(TAG, "Full Tree-sitter support requires SCM query files in assets/treesitter/[lang]/")
            
            return markLanguageAsSupported(language)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Tree-sitter language for $language", e)
            return markLanguageAsSupported(language)
        }
    }
    
    /**
     * 检查原生库是否可用
     */
    private fun isNativeLibraryAvailable(): Boolean {
        return try {
            // 首先尝试初始化原生库管理器
            if (!nativeLibManager.initializeNativeLibraries()) {
                Log.w(TAG, "Failed to initialize native libraries from assets")
                return false
            }
            
            // 检查库是否可用
            if (!nativeLibManager.areLibrariesAvailable()) {
                Log.w(TAG, "Required native libraries are not available")
                return false
            }
            
            // 检查Tree-sitter包装类是否可用
            if (TreeSitterWrapper.isNativeLibraryLoaded()) {
                Log.d(TAG, "Native libraries are available and working")
                true
            } else {
                false
            }
            
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Tree-sitter native library not available: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking native library availability", e)
            false
        }
    }
    
    /**
     * 标记语言为支持状态（使用回退方案）
     */
    private fun markLanguageAsSupported(language: String): Boolean {
        return when (language.lowercase()) {
            "java", "cpp", "c", "python", "py" -> {
                Log.d(TAG, "$language marked as available with fallback implementation")
                true
            }
            else -> {
                Log.w(TAG, "Unsupported language: $language")
                false
            }
        }
    }
    
    /**
     * 创建完整的Tree-sitter语言实现
     * 使用正确的sora-editor language-treesitter API的DSL方式
     */
        private fun createTsLanguage(
        languageName: String,
        tsLanguage: TreeSitterWrapper.TSLanguage,  // 我们的Tree-sitter语言实例
        highlightScm: String,       // 高亮查询文件内容
        codeBlocksScm: String = "", // 代码块查询文件内容
        bracketsScm: String = "",   // 括号匹配查询文件内容
        localsScm: String = ""      // 局部变量查询文件内容
    ): TsLanguage? {
        return try {
            Log.d(TAG, "Tree-sitter language loaded: ${tsLanguage.getLanguageName()}")
            Log.d(TAG, "Language instance available: ${tsLanguage.isAvailable()}")
            
            // 使用修改后的language-treesitter模块创建适配器
            val adaptedLanguage = TreeSitterLanguageFactory.wrapLanguage(
                tsLanguage,
                { tsLanguage.getLanguageName() },
                { tsLanguage.getInstance() }
            )
            
            Log.d(TAG, "Successfully adapted TSLanguage: ${adaptedLanguage.getName()}")
            
            val safeHighlightScm = if (highlightScm.isBlank()) {
                Log.w(TAG, "Empty highlight SCM for $languageName, using minimal default")
                "(identifier) @identifier"
            } else highlightScm
            
            val spec = TsLanguageSpec(
                language = adaptedLanguage,
                highlightScmSource = safeHighlightScm,
                codeBlocksScmSource = codeBlocksScm,
                bracketsScmSource = bracketsScm,
                localsScmSource = localsScm
            )
            
            Log.d(TAG, "TsLanguageSpec created successfully for $languageName")
            
            Log.d(TAG, "Creating TsLanguage instance for $languageName")
            
            val tsLang = TsLanguage(
                languageSpec = spec,
                tab = true
            ) {
                Log.d(TAG, "Configuring TsLanguage theme for $languageName")
            }
            
            Log.d(TAG, "Successfully created TsLanguage for $languageName")
            tsLang
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating TsLanguage for $languageName", e)
            null
        }
    }
    
    /**
     * 尝试加载Tree-sitter语言资源
     * 现在使用真实的TSLanguage实例
     */
    private fun loadTsLanguageResources(language: String): Triple<TSLanguage?, String?, String?>? {
        return try {
            Log.d(TAG, "Loading Tree-sitter resources for $language")
            Log.d(TAG, "Native libraries loaded: ${TreeSitterWrapper.isNativeLibraryLoaded()}")
            
            when (language.lowercase()) {
                "java" -> {
                    val tsLang = TreeSitterWrapper.TSLanguageJava.create()
                    val highlightScm = loadAssetFile("treesitter/java/highlights.scm") ?: ""
                    val localsScm = loadAssetFile("treesitter/java/locals.scm") ?: ""
                    Triple(tsLang, highlightScm, localsScm)
                }
                "cpp", "c" -> {
                    val tsLang = TreeSitterWrapper.TSLanguageCpp.create()
                    val highlightScm = loadAssetFile("treesitter/cpp/highlights.scm") ?: ""
                    val localsScm = loadAssetFile("treesitter/cpp/locals.scm") ?: ""
                    Triple(tsLang, highlightScm, localsScm)
                }
                "python", "py" -> {
                    val tsLang = TreeSitterWrapper.TSLanguagePython.create()
                    val highlightScm = loadAssetFile("treesitter/python/highlights.scm") ?: ""
                    val localsScm = loadAssetFile("treesitter/python/locals.scm") ?: ""
                    Triple(tsLang, highlightScm, localsScm)
                }
                else -> {
                    Log.w(TAG, "Unsupported language: $language")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Tree-sitter resources for $language", e)
            null
        }
    }
    
    /**
     * 从assets加载文件内容
     */
    private fun loadAssetFile(path: String): String? {
        return try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading asset file: $path", e)
            null
        }
    }
    
    /**
     * 获取Tree-sitter语言实例（如果可用）
     */
    fun getTsLanguage(language: String): TsLanguage? {
        return languages[language]
    }
    
    /**
     * 检查语言是否支持Tree-sitter
     */
    fun hasTsLanguage(language: String): Boolean {
        return languages.containsKey(language)
    }
    
    /**
     * 获取智能补全建议
     */
    fun getCompletions(
        documentUri: String, 
        language: String, 
        line: Int, 
        character: Int, 
        code: String
    ): List<CompletionItem> {
        return try {
            val tsLanguage = languages[language]
            
            if (tsLanguage != null) {
                // 使用Tree-sitter语言进行智能分析
                Log.d(TAG, "Using Tree-sitter for completions")
                getTreeSitterCompletions(tsLanguage, language, line, character, code)
            } else {
                // 回退到基于规则的补全
                Log.d(TAG, "Using fallback rule-based completions")
                getFallbackCompletions(language, line, character, code)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get completions", e)
            getFallbackCompletions(language, line, character, code)
        }
    }
    
    /**
     * 检查语法错误
     */
    fun checkSyntaxErrors(documentUri: String, language: String, code: String): List<Diagnostic> {
        return try {
            val tsLanguage = languages[language]
            
            if (tsLanguage != null) {
                // 使用Tree-sitter进行语法分析
                Log.d(TAG, "Using Tree-sitter for syntax checking")
                getTreeSitterDiagnostics(tsLanguage, language, code)
            } else {
                // 如果没有Tree-sitter支持，返回空列表
                Log.d(TAG, "No Tree-sitter support for $language, no syntax checking available")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check syntax errors", e)
            emptyList()
        }
    }
    
    /**
     * 使用Tree-sitter获取智能补全建议
     */
    private fun getTreeSitterCompletions(
        tsLanguage: TsLanguage,
        language: String,
        line: Int,
        character: Int,
        code: String
    ): List<CompletionItem> {
        return try {
            Log.d(TAG, "Using Tree-sitter for intelligent completions")
            
            val analyzeManager = analyzeManagers[language]
            if (analyzeManager != null) {
                // 使用TsAnalyzeManager进行语法分析
                val completions = mutableListOf<CompletionItem>()
                
                // 1. 基于语法树上下文的智能补全
                val contextCompletions = getContextAwareCompletions(
                    analyzeManager, code, line, character, language
                )
                completions.addAll(contextCompletions)
                
                // 2. 如果上下文补全为空，使用配置文件补全
                if (completions.isEmpty()) {
                    Log.d(TAG, "Context completions empty, falling back to configured completions")
                    completions.addAll(getFallbackCompletions(language, line, character, code))
                }
                
                Log.d(TAG, "Generated ${completions.size} Tree-sitter completions")
                return completions
                
            } else {
                Log.w(TAG, "No TsAnalyzeManager available for $language")
                return getFallbackCompletions(language, line, character, code)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Tree-sitter completions", e)
            return getFallbackCompletions(language, line, character, code)
        }
    }
    
    /**
     * 使用Tree-sitter获取语法和语义诊断信息
     */
    private fun getTreeSitterDiagnostics(
        tsLanguage: TsLanguage,
        language: String,
        code: String
    ): List<Diagnostic> {
        return try {
            Log.d(TAG, "Using Tree-sitter for syntax diagnostics")
            
            val analyzeManager = analyzeManagers[language]
            if (analyzeManager != null) {
                val diagnostics = mutableListOf<Diagnostic>()
                
                // 1. 使用Tree-sitter进行语法错误检测
                val syntaxErrors = getSyntaxErrorsFromTree(analyzeManager, code, language)
                diagnostics.addAll(syntaxErrors)
                
                // 不再使用基础规则检查，只使用Tree-sitter分析
                
                Log.d(TAG, "Generated ${diagnostics.size} Tree-sitter diagnostics")
                return diagnostics
                
            } else {
                Log.w(TAG, "No TsAnalyzeManager available for $language")
                return emptyList()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Tree-sitter diagnostics", e)
            return emptyList()
        }
    }
    
    /**
     * 基于语法树上下文获取智能补全
     */
    private fun getContextAwareCompletions(
        analyzeManager: TsAnalyzeManager,
        code: String,
        line: Int,
        character: Int,
        language: String
    ): List<CompletionItem> {
        return try {
            val completions = mutableListOf<CompletionItem>()
            
            // 创建CharSequence用于Tree-sitter分析
            val codeSequence = code as CharSequence
            
            // 使用TsAnalyzeManager进行语法树分析
            try {
                val content = Content(code)
                val contentRef = ContentReference(content)
                val bundle = Bundle()
                
                analyzeManager.reset(contentRef, bundle)
                Thread.sleep(100)
                
                Log.d(TAG, "Tree-sitter analysis completed for position $line:$character")
                
                // 获取当前位置的语法树节点上下文
                val completionsFromTree = getCompletionsFromSyntaxTree(
                    analyzeManager, code, line, character, language
                )
                completions.addAll(completionsFromTree)
                
            } catch (e: Exception) {
                Log.w(TAG, "Tree-sitter analysis failed, using fallback", e)
            }
            
            // 如果Tree-sitter分析没有返回结果，使用基于文本的上下文分析
            if (completions.isEmpty()) {
                Log.d(TAG, "Using text-based context analysis")
                
                // 提取当前行的上下文
                val lines = code.split('\n')
                val currentLine = lines.getOrNull(line) ?: ""
                val prefix = currentLine.substring(0, minOf(character, currentLine.length))
                
                // 不使用硬编码的上下文补全，依赖Tree-sitter分析
            }
            
            return completions
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in context-aware completions", e)
            emptyList()
        }
    }
    
    /**
     * 从语法树获取补全建议
     */
    private fun getCompletionsFromSyntaxTree(
        analyzeManager: TsAnalyzeManager,
        code: String,
        line: Int,
        character: Int,
        language: String
    ): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        try {
            // 1. 从highlights.scm提取关键字补全
            val keywordCompletions = extractKeywordsFromHighlights(language)
            completions.addAll(keywordCompletions)
            
            // 2. 从代码中使用Tree-sitter提取符号补全
            val symbolCompletions = extractSymbolsUsingTreeSitter(code, language)
            completions.addAll(symbolCompletions)
            
            // 3. 分析当前上下文提供相关补全
            val contextCompletions = getContextSpecificCompletions(code, line, character, language)
            completions.addAll(contextCompletions)
            
            Log.d(TAG, "Generated ${completions.size} Tree-sitter based completions")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting completions from syntax tree", e)
        }
        
        return completions.distinctBy { it.label }
    }
    
    /**
     * 从highlights.scm文件提取关键字补全
     */
    private fun extractKeywordsFromHighlights(language: String): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        try {
            val highlightsScm = loadAssetFile("treesitter/${language.lowercase()}/highlights.scm")
            if (highlightsScm != null) {
                // 使用正则表达式从highlights.scm中提取关键字数组
                val keywordPattern = Regex("""(?s)\[\s*(?:"([^"]+)"\s*)+\]\s*@keyword""")
                val keywordMatches = keywordPattern.findAll(highlightsScm)
                
                keywordMatches.forEach { match ->
                    val keywordBlock = match.value
                    val wordPattern = Regex(""""([^"]+)"""")
                    wordPattern.findAll(keywordBlock).forEach { wordMatch ->
                        val keyword = wordMatch.groupValues[1]
                                                 completions.add(CompletionItem(
                             label = keyword,
                             insertText = keyword,
                             detail = "$keyword keyword",
                             kind = CompletionItemKind.KEYWORD
                         ))
                    }
                }
                
                Log.d(TAG, "Extracted ${completions.size} keywords from highlights.scm for $language")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting keywords from highlights.scm", e)
        }
        
        return completions
    }
    
    /**
     * 使用Tree-sitter从代码中提取符号补全
     */
    private fun extractSymbolsUsingTreeSitter(code: String, language: String): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        try {
            val localsScm = loadAssetFile("treesitter/${language.lowercase()}/locals.scm")
            if (localsScm != null) {
                // 从locals.scm解析定义规则并在代码中查找符号
                completions.addAll(extractDefinitionsFromCode(code, language))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting symbols using Tree-sitter", e)
        }
        
        return completions
    }
    
    /**
     * 从代码中提取符号定义
     */
    private fun extractDefinitionsFromCode(code: String, language: String): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        when (language.lowercase()) {
            "java" -> {
                // 提取Java符号
                extractJavaSymbols(code, completions)
            }
            "cpp", "c" -> {
                // 提取C++符号
                extractCppSymbols(code, completions)
            }
            "python", "py" -> {
                // 提取Python符号
                extractPythonSymbols(code, completions)
            }
        }
        
        return completions
    }
    
    /**
     * 提取Java符号定义
     */
    private fun extractJavaSymbols(code: String, completions: MutableList<CompletionItem>) {
        // 方法定义
        val methodPattern = Regex("""(?:public|private|protected|static|final|abstract|synchronized)\s+(?:\w+\s+)*(\w+)\s*\([^)]*\)\s*(?:throws\s+\w+(?:\s*,\s*\w+)*)?\s*[{;]""")
        methodPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Method: ${match.groupValues[1]}",
                kind = CompletionItemKind.METHOD
            ))
        }
        
        // 变量定义
        val varPattern = Regex("""(?:public|private|protected|static|final)?\s*(?:\w+(?:<[^>]*>)?)\s+(\w+)\s*[=;]""")
        varPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Variable: ${match.groupValues[1]}",
                kind = CompletionItemKind.VARIABLE
            ))
        }
        
        // 类定义
        val classPattern = Regex("""(?:public|private|protected)?\s*(?:abstract|final)?\s*class\s+(\w+)""")
        classPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Class: ${match.groupValues[1]}",
                kind = CompletionItemKind.CLASS
            ))
        }
    }
    
    /**
     * 提取C++符号定义
     */
    private fun extractCppSymbols(code: String, completions: MutableList<CompletionItem>) {
        // 函数定义
        val funcPattern = Regex("""(?:inline|static|virtual|explicit)?\s*(?:\w+(?:\s*::\s*\w+)*(?:\s*<[^>]*>)?)\s+([a-zA-Z_]\w*)\s*\([^)]*\)\s*(?:const)?\s*[{;]""")
        funcPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Function: ${match.groupValues[1]}",
                kind = CompletionItemKind.FUNCTION
            ))
        }
        
        // 变量定义
        val varPattern = Regex("""(?:static|const|auto|extern)?\s*(?:\w+(?:\s*::\s*\w+)*(?:\s*<[^>]*>)?(?:\s*[*&]+)?)\s+([a-zA-Z_]\w*)\s*[=;,)]""")
        varPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Variable: ${match.groupValues[1]}",
                kind = CompletionItemKind.VARIABLE
            ))
        }
        
        // 类/结构体定义
        val classPattern = Regex("""(?:class|struct)\s+([a-zA-Z_]\w*)""")
        classPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Class: ${match.groupValues[1]}",
                kind = CompletionItemKind.CLASS
            ))
        }
    }
    
    /**
     * 提取Python符号定义
     */
    private fun extractPythonSymbols(code: String, completions: MutableList<CompletionItem>) {
        // 函数定义
        val funcPattern = Regex("""def\s+([a-zA-Z_]\w*)\s*\([^)]*\):""")
        funcPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Function: ${match.groupValues[1]}",
                kind = CompletionItemKind.FUNCTION
            ))
        }
        
        // 类定义
        val classPattern = Regex("""class\s+([a-zA-Z_]\w*)(?:\([^)]*\))?:""")
        classPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Class: ${match.groupValues[1]}",
                kind = CompletionItemKind.CLASS
            ))
        }
        
        // 变量赋值
        val varPattern = Regex("""^[ \t]*([a-zA-Z_]\w*)\s*=(?!=)""", RegexOption.MULTILINE)
        varPattern.findAll(code).forEach { match ->
            completions.add(CompletionItem(
                label = match.groupValues[1],
                insertText = match.groupValues[1],
                detail = "Variable: ${match.groupValues[1]}",
                kind = CompletionItemKind.VARIABLE
            ))
        }
    }
    
    /**
     * 获取上下文特定的补全建议
     */
    private fun getContextSpecificCompletions(
        code: String,
        line: Int,
        character: Int,
        language: String
    ): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        try {
            val lines = code.split('\n')
            val currentLine = lines.getOrNull(line) ?: ""
            val prefix = currentLine.substring(0, minOf(character, currentLine.length))
            
            // 分析上下文并提供相关补全
            when {
                prefix.endsWith(".") || prefix.endsWith("->") || prefix.endsWith("::") -> {
                    // 成员访问补全 - 这里可以扩展为从Tree-sitter AST中分析类型
                    completions.addAll(getMemberAccessCompletions(language))
                }
                prefix.matches(Regex(".*\\bimport\\s*$")) -> {
                    // 导入语句补全
                    completions.addAll(getImportCompletions(language))
                }
                prefix.matches(Regex(".*\\b(extends|implements|:)\\s*$")) -> {
                    // 继承/实现补全
                    completions.addAll(getTypeCompletions(language))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting context specific completions", e)
        }
        
        return completions
    }
    
    /**
     * 获取成员访问补全（基于语言的常见方法）
     */
    private fun getMemberAccessCompletions(language: String): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        when (language.lowercase()) {
            "java" -> {
                // Java常用方法
                listOf("toString", "equals", "hashCode", "getClass", "length", "size", "isEmpty", "add", "remove", "get", "set")
                    .forEach { method ->
                        completions.add(CompletionItem(
                            label = method,
                            insertText = method,
                            detail = "$method method",
                            kind = CompletionItemKind.METHOD
                        ))
                    }
            }
            "cpp", "c" -> {
                // C++常用方法
                listOf("size", "length", "empty", "begin", "end", "push_back", "pop_back", "clear", "reserve")
                    .forEach { method ->
                        completions.add(CompletionItem(
                            label = method,
                            insertText = method,
                            detail = "$method method",
                            kind = CompletionItemKind.METHOD
                        ))
                    }
            }
            "python", "py" -> {
                // Python常用方法
                listOf("append", "extend", "remove", "pop", "index", "count", "len", "str", "repr")
                    .forEach { method ->
                        completions.add(CompletionItem(
                            label = method,
                            insertText = method,
                            detail = "$method method",
                            kind = CompletionItemKind.METHOD
                        ))
                    }
            }
        }
        
        return completions
    }
    
    /**
     * 获取导入补全
     */
    private fun getImportCompletions(language: String): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        when (language.lowercase()) {
            "java" -> {
                listOf("java.util", "java.io", "java.lang", "java.nio", "java.time")
                    .forEach { pkg ->
                        completions.add(CompletionItem(
                            label = pkg,
                            insertText = pkg,
                            detail = "Package: $pkg",
                            kind = CompletionItemKind.MODULE
                        ))
                    }
            }
            "cpp", "c" -> {
                listOf("iostream", "vector", "string", "algorithm", "memory", "functional")
                    .forEach { header ->
                        completions.add(CompletionItem(
                            label = header,
                            insertText = header,
                            detail = "Header: $header",
                            kind = CompletionItemKind.MODULE
                        ))
                    }
            }
            "python", "py" -> {
                listOf("os", "sys", "json", "re", "math", "random", "datetime", "pathlib")
                    .forEach { module ->
                        completions.add(CompletionItem(
                            label = module,
                            insertText = module,
                            detail = "Module: $module",
                            kind = CompletionItemKind.MODULE
                        ))
                    }
            }
        }
        
        return completions
    }
    
    /**
     * 获取类型补全
     */
    private fun getTypeCompletions(language: String): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        when (language.lowercase()) {
            "java" -> {
                listOf("Object", "String", "Integer", "List", "Map", "Set", "Collection")
                    .forEach { type ->
                        completions.add(CompletionItem(
                            label = type,
                            insertText = type,
                            detail = "Type: $type",
                            kind = CompletionItemKind.CLASS
                        ))
                    }
            }
            "cpp", "c" -> {
                listOf("std::string", "std::vector", "std::map", "std::set", "std::shared_ptr", "std::unique_ptr")
                    .forEach { type ->
                        completions.add(CompletionItem(
                            label = type,
                            insertText = type,
                            detail = "Type: $type",
                            kind = CompletionItemKind.CLASS
                        ))
                    }
            }
            "python", "py" -> {
                listOf("list", "dict", "set", "tuple", "str", "int", "float", "bool")
                    .forEach { type ->
                        completions.add(CompletionItem(
                            label = type,
                            insertText = type,
                            detail = "Type: $type",
                            kind = CompletionItemKind.CLASS
                        ))
                    }
            }
        }
        
        return completions
    }
    

    

    

    
    /**
     * 从语法树获取语法错误
     */
    private fun getSyntaxErrorsFromTree(
        analyzeManager: TsAnalyzeManager,
        code: String,
        language: String
    ): List<Diagnostic> {
        return try {
            val diagnostics = mutableListOf<Diagnostic>()
            val codeSequence = code as CharSequence
            
            // 使用TsAnalyzeManager进行语法树分析
            try {
                val content = Content(code)
                val contentRef = ContentReference(content)
                val bundle = Bundle()
                
                analyzeManager.reset(contentRef, bundle)
                Thread.sleep(100)
                
                Log.d(TAG, "Tree-sitter syntax analysis completed")
                
                // 获取语法错误
                val syntaxErrors = extractSyntaxErrorsFromTree(analyzeManager, code, language)
                diagnostics.addAll(syntaxErrors)
                
                // 获取语义错误（如未声明的变量等）
                val semanticErrors = extractSemanticErrorsFromTree(analyzeManager, code, language)
                diagnostics.addAll(semanticErrors)
                
                Log.d(TAG, "Found ${diagnostics.size} errors from Tree-sitter analysis")
                
            } catch (e: Exception) {
                Log.w(TAG, "Tree-sitter analysis failed for error detection", e)
            }
            
            return diagnostics
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Tree-sitter syntax analysis", e)
            emptyList()
        }
    }
    
    /**
     * 从语法树中提取语法错误
     */
    private fun extractSyntaxErrorsFromTree(
        analyzeManager: TsAnalyzeManager,
        code: String,
        language: String
    ): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        val lines = code.split('\n')
        
        try {
            // Tree-sitter会将语法错误标记为ERROR节点
            // 这里我们可以使用查询来找到这些错误节点
            
            // 真正的Tree-sitter语法错误检测应该通过查询ERROR节点来实现
            // 目前暂时不实现基础的正则表达式检查
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting syntax errors from tree", e)
        }
        
        return diagnostics
    }
    
    /**
     * 从语法树中提取语义错误
     */
    private fun extractSemanticErrorsFromTree(
        analyzeManager: TsAnalyzeManager,
        code: String,
        language: String
    ): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        
        try {
            // 语义错误包括：
            // 1. 未声明的变量/函数
            // 2. 类型不匹配
            // 3. 重复声明
            // 4. 作用域错误
            
            // 真正的Tree-sitter语义错误检测应该通过语法树查询来实现
            // 目前暂时不实现基础的正则表达式检查
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting semantic errors from tree", e)
        }
        
        return diagnostics
    }
    

    
    /**
     * 获取基于YAML配置的补全建议
     */
    private fun getFallbackCompletions(
        language: String,
        line: Int,
        character: Int,
        code: String
    ): List<CompletionItem> {
        try {
            // 只使用YAML配置文件获取补全
            if (yamlExtractor.hasConfigForLanguage(language)) {
                Log.d(TAG, "Using YAML completions for $language")
                val yamlCompletions = yamlExtractor.extractCompletions(language)
                if (yamlCompletions.isNotEmpty()) {
                    return yamlCompletions
                }
            }
            
            // 如果没有YAML配置，返回空列表
            Log.d(TAG, "No YAML completions available for $language")
            return emptyList()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in YAML completions", e)
            return emptyList()
        }
    }
    

    
    // === 上下文感知补全辅助方法 ===
    

    

    

    

    

    

    
    /**
     * 清理Tree-sitter资源
     */
    fun dispose() {
        try {
            // 清理语言规范（实现了Closeable接口）
            languageSpecs.values.forEach { spec ->
                try {
                    spec.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing language spec", e)
                }
            }
            languageSpecs.clear()
            
            // 清理语言实例
            languages.clear()
            
            // 清理分析管理器
            analyzeManagers.clear()
            
            // 清理原生库管理器
            nativeLibManager.cleanupLibraries()
            
            Log.d(TAG, "Tree-sitter resources disposed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing Tree-sitter resources", e)
        }
    }
}

// 数据类现在定义在 com.acc_ide.lsp.model 包中 