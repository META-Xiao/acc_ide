package com.acc_ide

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.acc_ide.view.SymbolPanelView
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019
import io.github.rosemoe.sora.event.ContentChangeEvent
import kotlin.jvm.functions.Function1
import android.util.TypedValue

class EditorFragment : Fragment() {
    private lateinit var editor: CodeEditor
    private lateinit var language: String
    private lateinit var fileName: String
    private var initialized = false
    private var isIOPanelOpen = false
    private var runMenuItem: MenuItem? = null
    private var symbolPanelMenuItem: MenuItem? = null
    private var hasUnsavedChanges = false
    private var isUpdatingFontSize = false // 防止缩放监听器和设置更新相互触发
    
    // Symbol panel components
    private lateinit var symbolPanel: SymbolPanelView
    private var isSymbolPanelVisible = true // 默认显示符号面板

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true) // 启用选项菜单
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_editor, container, false)
        editor = view.findViewById(R.id.editor_view)
        symbolPanel = view.findViewById(R.id.symbol_panel)
        
        // 获取参数
        arguments?.let {
            language = it.getString(ARG_LANGUAGE, "text")
            fileName = it.getString(ARG_FILENAME, "")
        }
        
        // 配置编辑器
        configureEditor()
        
        // 设置内容
        val mainActivity = activity as MainActivity
        val fileContent = mainActivity.files[fileName] ?: ""
        editor.setText(fileContent)
        
        // 设置语言支持
        setupLanguageSupport()

        // 设置文本变化监听器
        setupContentChangeListener()
        
        // 初始化符号面板
        initSymbolPanel()
        
        // 仅在库支持的情况下尝试配置自动完成面板
        editor.post {
            try {
                // 检查是否存在mCompletionWindow字段
                if (CodeEditor::class.java.declaredFields.any { it.name == "mCompletionWindow" }) {
                    // 强制重新创建自动完成面板
                    forceRecreateCompletionWindow()
                    
                    // 配置自动完成面板样式
                    configureCompletionPanelStyle()
                } else {
                    android.util.Log.i("EditorFragment", "当前库版本不支持直接访问自动完成面板")
                }
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "初始化自动完成面板配置失败: ${e.message}")
            }
        }
        
        initialized = true
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 初始化界面视图
        initViews(view)
        
        // 配置语言支持
        configureLanguage()
        
        // 仅在库支持的情况下配置自动完成面板样式
        try {
            if (CodeEditor::class.java.declaredFields.any { it.name == "mCompletionWindow" }) {
                // 先应用设置再配置样式，确保智能提示设置正确应用
                applySettingsFromPreferences()
                configureCompletionPanelStyle()
            }
        } catch (e: Exception) {
            android.util.Log.w("EditorFragment", "配置自动完成面板样式失败: ${e.message}")
        }
        
        // 注册主题变更监听器
        registerThemeChangeListener()
        
        // 确保设置正确应用
        applySettingsFromPreferences()
        
        // 延迟设置光标宽度，确保在编辑器完全初始化后应用
        editor.post {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val cursorWidth = prefs.getFloat(SettingsFragment.PREF_CURSOR_WIDTH, SettingsFragment.DEFAULT_CURSOR_WIDTH)
            setCursorWidth(cursorWidth)
        }
    }
    
    override fun onResume() {
        super.onResume()
        applySettingsFromPreferences()
        
        // 在恢复时重新设置光标宽度
        editor.post {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val cursorWidth = prefs.getFloat(SettingsFragment.PREF_CURSOR_WIDTH, SettingsFragment.DEFAULT_CURSOR_WIDTH)
            setCursorWidth(cursorWidth)
        }
    }

    /**
     * 从SharedPreferences读取设置并应用到编辑器和符号面板
     */
    private fun applySettingsFromPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val symbolPanelEnabled = prefs.getBoolean(SettingsFragment.PREF_ENABLE_SYMBOL_PANEL, true)
        
        // 应用到符号面板
        setSymbolPanelVisibility(symbolPanelEnabled)
        isSymbolPanelVisible = symbolPanelEnabled
        
        // 应用光标宽度设置
        val cursorWidth = prefs.getFloat(SettingsFragment.PREF_CURSOR_WIDTH, SettingsFragment.DEFAULT_CURSOR_WIDTH)
        updateCursorWidth(cursorWidth)
    }
    
    /**
     * 初始化符号面板
     */
    private fun initSymbolPanel() {
        try {
            // 设置编辑器实例给符号面板
            symbolPanel.setEditor(editor)
            
            // 设置初始可见性
            setSymbolPanelVisibility(isSymbolPanelVisible)
            
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "初始化符号面板失败: ${e.message}")
        }
    }
    
    /**
     * 设置符号面板的可见性
     */
    private fun setSymbolPanelVisibility(visible: Boolean) {
        symbolPanel.visibility = if (visible) View.VISIBLE else View.GONE
    }
    
    /**
     * 初始化界面视图
     * 此方法在Fragment视图创建后被调用，用于设置视图相关组件
     */
    private fun initViews(view: View) {
        try {
            // 初始化编辑器视图的其他UI元素（如工具栏、按钮等）
            // 在此处添加编辑器相关的视图初始化代码
            
            // 示例：设置编辑器的长按监听器
            editor.setOnLongClickListener {
                // 显示上下文菜单或者执行其他操作
                true
            }
            
            android.util.Log.d("EditorFragment", "视图初始化完成")
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "初始化视图时出错: ${e.message}")
        }
    }
    
    /**
     * 配置语言支持
     * 此方法用于根据文件类型配置编辑器的语言支持
     */
    private fun configureLanguage() {
        try {
            // 确保editor已经初始化
            if (::editor.isInitialized) {
                // 根据文件类型设置语言
                setupLanguageSupport()
                
                // 设置语言特定的编辑器选项
                when (language) {
                    "java", "kotlin" -> {
                        // Java/Kotlin特定设置
                        try {
                            // 使用反射设置自动缩进，因为不同版本的编辑器可能有不同的API
                            val method = CodeEditor::class.java.getDeclaredMethod("setAutoIndentEnabled", Boolean::class.java)
                            method.invoke(editor, true)
                        } catch (e: Exception) {
                            android.util.Log.w("EditorFragment", "无法设置自动缩进: ${e.message}")
                        }
                    }
                    "python" -> {
                        // Python特定设置
                        try {
                            val method = CodeEditor::class.java.getDeclaredMethod("setAutoIndentEnabled", Boolean::class.java)
                            method.invoke(editor, true)
                        } catch (e: Exception) {
                            android.util.Log.w("EditorFragment", "无法设置自动缩进: ${e.message}")
                        }
                    }
                    else -> {
                        // 默认设置
                        try {
                            val method = CodeEditor::class.java.getDeclaredMethod("setAutoIndentEnabled", Boolean::class.java)
                            method.invoke(editor, false)
                        } catch (e: Exception) {
                            android.util.Log.w("EditorFragment", "无法设置自动缩进: ${e.message}")
                        }
                    }
                }
                
                android.util.Log.d("EditorFragment", "已为${language}语言配置编辑器")
            }
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "配置语言支持时出错: ${e.message}")
        }
    }
    
    private fun setupContentChangeListener() {
        // 监听文本变化以标记未保存状态
        try {
            editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                hasUnsavedChanges = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun saveContent(showToast: Boolean) {
        val content = editor.text.toString()
        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            // 更新内存中的文件内容
            it.files[fileName] = content
            
            // 同时更新存储
            it.updateFileContent(fileName, content)
            
            // 重置未保存状态
            hasUnsavedChanges = false
            
            // 显示保存成功提示
            if (showToast) {
                Toast.makeText(context, getString(R.string.file_saved, fileName), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openIOPanel() {
        // 检查是否已经有IO面板打开
        val existingPanel = parentFragmentManager.findFragmentByTag("io_panel")
        if (existingPanel != null) {
            // 如果已经有面板打开，先移除它
            parentFragmentManager.beginTransaction()
                .remove(existingPanel)
                .commit()
        }
        
        // 创建并显示IO面板
        val ioPanel = IOPanelFragment.newInstance(fileName, language)
        
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .add(R.id.content_frame, ioPanel, "io_panel")
            .addToBackStack("io_panel")
            .commit()
            
        // 更新状态和图标
        isIOPanelOpen = true
        updateRunMenuIcon()
    }
    
    private fun closeIOPanel() {
        // 查找IO面板
        val ioPanel = parentFragmentManager.findFragmentByTag("io_panel")
        if (ioPanel != null) {
            // 移除IO面板
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .remove(ioPanel)
                .commit()
            
            // 弹出回退栈
            parentFragmentManager.popBackStack()
            
            // 确保Activity恢复正确的导航栏状态
            (activity as? MainActivity)?.let { mainActivity ->
                // 延迟执行，确保Fragment转换完成
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // 直接调用MainActivity的导航栏更新方法
                    mainActivity.updateNavigationIcon()
                }, 100)
            }
        }
        
        // 更新状态和图标
        isIOPanelOpen = false
        updateRunMenuIcon()
    }
    
    private fun updateRunMenuIcon() {
        runMenuItem?.let {
            if (isIOPanelOpen) {
                it.setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                it.setTitle(R.string.close)
            } else {
                it.setIcon(android.R.drawable.ic_media_play)
                it.setTitle(R.string.run)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 当Fragment暂停时，只有在有未保存更改时才保存内容
        if (hasUnsavedChanges) {
            saveContent(false)
        }
    }
    
    /**
     * 配置编辑器属性
     * 设置编辑器的外观、行为和功能
     */
    private fun configureEditor() {
        // 设置编辑器属性
        editor.apply {
            // 基本设置
            isWordwrap = true  // 启用自动换行
            
            // 缩进设置
            tabWidth = 4  // 设置制表符宽度为4个空格
            
            // UI设置
            isLineNumberEnabled = true  // 显示行号
            
            // 撤销/重做
            isUndoEnabled = true  // 启用撤销/重做功能
            
            // 从应用偏好设置获取保存的字体大小
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val fontSize = prefs.getFloat(SettingsFragment.PREF_FONT_SIZE, SettingsFragment.DEFAULT_FONT_SIZE)
            
            // 设置编辑器字体大小
            setTextSize(fontSize)
            
            // 设置光标宽度 - 从设置中读取
            val cursorWidth = prefs.getFloat(SettingsFragment.PREF_CURSOR_WIDTH, SettingsFragment.DEFAULT_CURSOR_WIDTH)
            setCursorWidth(cursorWidth)
            
            // 启用缩放功能，允许用户通过手势调整字体大小
            isScalable = true
            
            // 跟踪字体大小变化，以便保存用户的缩放调整
            var lastTextSize = getTextSize() // 使用getter方法获取当前字体大小
            
            // 通过布局变化监听器实现字体大小变化的检测
            // 这比直接监听缩放手势更可靠，因为它能捕获所有导致字体大小变化的操作
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                // 获取当前字体大小
                val currentTextSize = getTextSize()
                
                // 如果字体大小变化了，并且不是通过设置页面更新的，保存新的字体大小
                if (currentTextSize != lastTextSize && !isUpdatingFontSize) {
                    isUpdatingFontSize = true
                    android.util.Log.d("EditorFragment", "检测到字体大小变化: $currentTextSize")
                    
                    // 更新记录的上次字体大小
                    lastTextSize = currentTextSize
                    
                    // 将缩放后的字体大小保存到设置
                    val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    prefs.edit().putFloat(SettingsFragment.PREF_FONT_SIZE, currentTextSize).apply()
                    
                    // 延迟重置标志，防止无限循环
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isUpdatingFontSize = false
                    }, 100)
                }
            }
            
            // 设置自定义颜色方案
            colorScheme = getCustomColorScheme()
            
            // 设置美化选项：显示空白字符
            nonPrintablePaintingFlags = CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or 
                                       CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION
            
            // 启用代码块指示线 - 视觉上区分代码块
            isBlockLineEnabled = true
            
            // 配置自动完成功能 - 总是启用
            try {
                // 尝试使用isCompletionEnabled字段启用自动完成
                try {
                    val field = javaClass.getDeclaredField("isCompletionEnabled")
                    field.isAccessible = true
                    field.setBoolean(this, true)
                    android.util.Log.d("EditorFragment", "已启用自动完成")
            } catch (e: Exception) {
                    android.util.Log.d("EditorFragment", "通过字段启用自动完成失败: ${e.message}")
            }
            
                // 配置自动完成面板样式
            try {
                // 检测当前是否处于深色模式
                val isDarkMode = resources.configuration.uiMode and 
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                
                // 定义自动完成面板的颜色方案
                val bgColor = if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFFEF7FF.toInt()  // 背景色
                val textColor = if (isDarkMode) 0xFFEEEEEE.toInt() else 0xFF000000.toInt() // 文本色
                val selectedBgColor = if (isDarkMode) 0xFF3A3D41.toInt() else 0xFFE6D7F2.toInt() // 选中项背景色
                
                    // 尝试设置颜色 - 使用反射API
                    // 这里使用更通用的反射方法，适应各种可能的API变化
                    javaClass.methods.forEach { method ->
                        when {
                            method.name.contains("setCompletionWindow", ignoreCase = true) && 
                            method.name.contains("BackgroundColor", ignoreCase = true) -> {
                                try {
                                    if (method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.java) {
                                        method.invoke(this, bgColor)
                                    }
                    } catch (e: Exception) {
                                    // 忽略单个方法调用错误
                    }
                            }
                            method.name.contains("setCompletionWindow", ignoreCase = true) && 
                            method.name.contains("TextColor", ignoreCase = true) -> {
                    try {
                                    if (method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.java) {
                                        method.invoke(this, textColor)
                                    }
                    } catch (e: Exception) {
                                    // 忽略单个方法调用错误
                    }
                            }
                            method.name.contains("setCompletionWindow", ignoreCase = true) && 
                            method.name.contains("SelectedItemBackground", ignoreCase = true) -> {
                    try {
                                    if (method.parameterTypes.size == 1 && method.parameterTypes[0] == Int::class.java) {
                                        method.invoke(this, selectedBgColor)
                                    }
                    } catch (e: Exception) {
                                    // 忽略单个方法调用错误
                                }
                            }
                    }
                }
            } catch (e: Exception) {
                    android.util.Log.w("EditorFragment", "配置自动完成面板样式失败: ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "配置自动完成功能时出错: ${e.message}")
            }
        }
    }
    
    /**
     * 创建自定义颜色方案，确保在深色和浅色模式下背景颜色统一
     */
    private fun getCustomColorScheme(): EditorColorScheme {
        val isDarkMode = resources.configuration.uiMode and 
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // 基于当前模式选择基础方案
        val scheme = if (isDarkMode) SchemeDarcula() else SchemeVS2019()
        
        // 获取颜色资源
        val backgroundColor = if (isDarkMode) 
            ContextCompat.getColor(requireContext(), R.color.code_background_dark)
        else 
            ContextCompat.getColor(requireContext(), R.color.code_background_light)
            
        val textColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.code_text_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.code_text_light)
            
        // 获取语法高亮颜色
        val keywordColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.keyword_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.keyword_light)
            
        val commentColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.comment_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.comment_light)
            
        val stringColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.string_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.string_light)
            
        val numberColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.number_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.number_light)
            
        val operatorColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.operator_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.operator_light)
            
        val identifierColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.identifier_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.identifier_light)
            
        val typeNameColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.type_name_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.type_name_light)
            
        val functionNameColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.function_name_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.function_name_light)
            
        // 获取编辑器特殊元素颜色
        val lineNumberColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.line_number_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.line_number_light)
            
        val currentLineColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.current_line_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.current_line_light)
            
        val selectionColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.selection_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.selection_light)
            
        val blockLineColor = if (isDarkMode)
            ContextCompat.getColor(requireContext(), R.color.block_line_dark)
        else
            ContextCompat.getColor(requireContext(), R.color.block_line_light)
            
        // 应用自定义颜色
        scheme.apply {
            // 设置背景颜色
            setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
            
            // 尝试使用反射设置背景颜色，尝试多种可能的常量名
            val possibleBackgroundNames = listOf("BACKGROUND", "BACK_GROUND", "BG", "EDITOR_BACKGROUND")
            var backgroundSet = false
            for (name in possibleBackgroundNames) {
                try {
                    val field = EditorColorScheme::class.java.getDeclaredField(name)
                    field.isAccessible = true
                    val value = field.getInt(null)
                    setColor(value, backgroundColor)
                    backgroundSet = true
                    break
                } catch (e: Exception) {
                    // 继续尝试下一个可能的名称
                }
            }
            
            // 如果所有尝试都失败，至少确保整体背景色已设置
            if (!backgroundSet) {
                // WHOLE_BACKGROUND 是我们确定存在的，已经在上面设置了
                // 可以添加日志记录这个问题
                android.util.Log.w("EditorFragment", "无法找到背景色常量，只设置了WHOLE_BACKGROUND")
            }
            
            // 设置文本颜色
            setColor(EditorColorScheme.TEXT_NORMAL, textColor)
            
            // 设置行号颜色
            setColor(EditorColorScheme.LINE_NUMBER, lineNumberColor)
            
            // 尝试使用反射设置行号背景颜色
            val possibleLineNumberBgNames = listOf("LINE_NUMBER_BACKGROUND", "LINE_NUMBER_BG", "LINENUMBER_BACKGROUND")
            var lineNumberBgSet = false
            for (name in possibleLineNumberBgNames) {
                try {
                    val field = EditorColorScheme::class.java.getDeclaredField(name)
                    field.isAccessible = true
                    val value = field.getInt(null)
                    setColor(value, backgroundColor)
                    lineNumberBgSet = true
                    break
                } catch (e: Exception) {
                    // 继续尝试下一个可能的名称
                }
            }
            
            if (!lineNumberBgSet) {
                android.util.Log.w("EditorFragment", "无法找到行号背景色常量")
            }
            
            // 设置当前行高亮颜色
            setColor(EditorColorScheme.CURRENT_LINE, currentLineColor)
            
            // 设置选择区域颜色
            setColor(EditorColorScheme.SELECTION_INSERT, selectionColor)
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionColor)
            
            // 设置代码块指示线颜色
            setColor(EditorColorScheme.BLOCK_LINE, blockLineColor)
            
            // 设置光标颜色
            // 尝试使用反射设置光标颜色
            val possibleCursorNames = listOf("CURSOR", "SELECTION_HANDLE", "CARET")
            var cursorSet = false
            val cursorColor = if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            
            for (name in possibleCursorNames) {
                try {
                    val field = EditorColorScheme::class.java.getDeclaredField(name)
                    field.isAccessible = true
                    val value = field.getInt(null)
                    setColor(value, cursorColor)
                    cursorSet = true
                } catch (e: Exception) {
                    // 继续尝试下一个可能的名称
                }
            }
            
            if (!cursorSet) {
                android.util.Log.w("EditorFragment", "无法设置光标颜色")
            }
            
            // 设置语法高亮颜色
            setColor(EditorColorScheme.KEYWORD, keywordColor)
            setColor(EditorColorScheme.COMMENT, commentColor)
            setColor(EditorColorScheme.LITERAL, stringColor)
            setColor(EditorColorScheme.OPERATOR, operatorColor)
            setColor(EditorColorScheme.IDENTIFIER_NAME, identifierColor)
            setColor(EditorColorScheme.IDENTIFIER_VAR, identifierColor)
            
            // 使用正确的常量或者移除不存在的常量
            try {
                // 尝试设置数字颜色
                val numberField = EditorColorScheme::class.java.getDeclaredField("NUMBER")
                numberField.isAccessible = true
                val numberValue = numberField.getInt(null)
                setColor(numberValue, numberColor)
            } catch (e: Exception) {
                // 如果常量不存在，忽略错误
            }
            
            try {
                // 尝试设置类型名称颜色
                val typeNameField = EditorColorScheme::class.java.getDeclaredField("TYPE_NAME")
                typeNameField.isAccessible = true
                val typeNameValue = typeNameField.getInt(null)
                setColor(typeNameValue, typeNameColor)
            } catch (e: Exception) {
                // 如果常量不存在，忽略错误
            }
            
            try {
                // 尝试设置函数名称颜色
                val functionNameField = EditorColorScheme::class.java.getDeclaredField("FUNCTION_NAME")
                functionNameField.isAccessible = true
                val functionNameValue = functionNameField.getInt(null)
                setColor(functionNameValue, functionNameColor)
            } catch (e: Exception) {
                // 如果常量不存在，忽略错误
            }
            
            // 设置括号匹配颜色
            val possibleMatchedTextBgNames = listOf("MATCHED_TEXT_BACKGROUND", "MATCHED_BRACKETS", "MATCHED_BRACKET_BACKGROUND")
            var matchedBgSet = false
            val matchedBgColor = if (isDarkMode) 0x66444444 else 0x66DDDDDD
            
            for (name in possibleMatchedTextBgNames) {
                try {
                    val field = EditorColorScheme::class.java.getDeclaredField(name)
                    field.isAccessible = true
                    val value = field.getInt(null)
                    setColor(value, matchedBgColor)
                    matchedBgSet = true
                    break
                } catch (e: Exception) {
                    // 继续尝试下一个可能的名称
                }
            }
            
            if (!matchedBgSet) {
                android.util.Log.w("EditorFragment", "无法设置括号匹配背景色")
            }
            
            // 设置自动完成面板的颜色
            // 自动完成面板背景色
            val completionBgColor = if (isDarkMode) 
                0xFF2C2C2C.toInt() 
            else 
                0xFFFEF7FF.toInt() // 使用 #fef7ff 作为浅色模式下的背景色
            
            // 自动完成面板文本颜色
            val completionTextColor = if (isDarkMode) 
                0xFFEEEEEE.toInt() 
            else 
                0xFF000000.toInt() // 使用纯黑色作为浅色模式下的文本颜色
            
            // 自动完成面板图标颜色
            val completionIconColor = if (isDarkMode)
                0xFFBBBBBB.toInt()
            else
                0xFF674FA4.toInt() // 使用紫色作为图标颜色，与应用主题一致
            
            // 自动完成面板选中项背景色
            val completionSelectedColor = if (isDarkMode) 
                0xFF3A3D41.toInt() 
            else 
                0xFFE6D7F2.toInt() // 使用与背景色协调的浅紫色作为选中项背景
                
            // 自动完成面板边框颜色
            val completionBorderColor = if (isDarkMode)
                0xFF555555.toInt()
            else
                0xFFDDCCEE.toInt() // 使用浅紫色边框
                
            // 自动完成面板滚动条颜色
            val completionScrollbarColor = if (isDarkMode)
                0xFF555555.toInt()
            else
                0xFFCCBBDD.toInt() // 使用浅紫色滚动条
            
            // 尝试设置自动完成面板颜色
            val completionColorNames = mapOf(
                // 基本颜色
                "COMPLETION_WINDOW_BACKGROUND" to completionBgColor,
                "COMPLETION_WINDOW_TEXT" to completionTextColor,
                "COMPLETION_WINDOW_SELECTED" to completionSelectedColor,
                "AUTO_COMP_PANEL_BG" to completionBgColor,
                "AUTO_COMP_PANEL_TEXT" to completionTextColor,
                "AUTO_COMP_PANEL_SELECTED_TEXT" to if (isDarkMode) completionTextColor else 0xFF000000.toInt(), // 确保选中文本在浅色模式下为黑色
                "AUTO_COMP_PANEL_SELECTED_ITEM_BG" to completionSelectedColor,
                
                // 扩展颜色
                "AUTO_COMP_PANEL_CORNER" to completionBorderColor,
                "AUTO_COMP_PANEL_BORDER" to completionBorderColor,
                "AUTO_COMP_PANEL_SCROLLBAR_THUMB" to completionScrollbarColor,
                "AUTO_COMP_PANEL_SCROLLBAR_TRACK" to completionBgColor,
                
                // 图标颜色
                "AUTO_COMP_PANEL_ICON" to completionIconColor,
                "COMPLETION_WINDOW_ICON" to completionIconColor,
                
                // 其他可能的名称
                "COMPLETION_WND_BACKGROUND" to completionBgColor,
                "COMPLETION_WND_TEXT" to completionTextColor,
                "COMPLETION_WND_ITEM_CURRENT" to completionSelectedColor,
                "COMPLETION_WND_CORNER" to completionBorderColor,
                "COMPLETION_WND_BORDER" to completionBorderColor,
                
                // 更多可能的名称
                "COMPLETION_TEXT" to completionTextColor,
                "COMPLETION_SELECTED_TEXT" to if (isDarkMode) completionTextColor else 0xFF000000.toInt(),
                "COMPLETION_BACKGROUND" to completionBgColor,
                "COMPLETION_SELECTED_BACKGROUND" to completionSelectedColor,
                "COMPLETION_BORDER" to completionBorderColor
            )
            
            // 使用更直接的方法设置自动完成面板颜色
            for ((name, color) in completionColorNames) {
                try {
                    // 尝试直接使用常量名称设置颜色
                    val field = EditorColorScheme::class.java.getDeclaredField(name)
                    field.isAccessible = true
                    val value = field.getInt(null)
                    setColor(value, color)
                } catch (e: Exception) {
                    // 如果常量不存在，尝试通过常量值设置
                    try {
                        // 尝试通过常量值范围设置
                        for (i in 250..350) { // 假设自动完成面板相关常量在这个范围内
                            try {
                                val fieldName = EditorColorScheme::class.java.declaredFields.find { field ->
                                    (field.modifiers and java.lang.reflect.Modifier.STATIC) != 0 && 
                                    field.name.contains(name, ignoreCase = true)
                                }
                                if (fieldName != null) {
                                    fieldName.isAccessible = true
                                    val value = fieldName.getInt(null)
                                    setColor(value, color)
                                    break
                                }
                            } catch (e: Exception) {
                                // 忽略错误
                            }
                        }
                    } catch (e2: Exception) {
                        // 忽略错误
                    }
                }
            }
        }
        
        return scheme
    }
    
    /**
     * 设置语言支持
     * 根据文件类型为编辑器设置合适的语言解析器
     */
    private fun setupLanguageSupport() {
        try {
            // 根据文件类型设置相应的语言解析器
            val languageToUse = when (language) {
                "java" -> JavaLanguage() // Java语言解析器
                "cpp" -> JavaLanguage()  // 临时使用Java语言解析器代替C++
                "python" -> EmptyLanguage() // 目前使用空语言解析器，未来可添加Python支持
                else -> EmptyLanguage() // 默认使用空语言解析器
            }
            
            // 将选定的语言解析器应用到编辑器
            editor.setEditorLanguage(languageToUse)
            android.util.Log.d("EditorFragment", "已设置${language}语言支持")
        } catch (e: Exception) {
            e.printStackTrace()
            // 发生错误时退回到空语言解析器，确保编辑器仍可用
            editor.setEditorLanguage(EmptyLanguage())
            android.util.Log.e("EditorFragment", "设置语言支持时出错: ${e.message}，已退回到空语言解析器")
        }
    }

    /**
     * 配置自动完成面板样式
     * 这个方法在编辑器初始化后调用，确保自动完成面板样式正确应用
     */
    private fun configureCompletionPanelStyle() {
        // 延迟执行，确保编辑器已完全初始化
        editor.post {
            try {
                // 检测当前是否处于深色模式
                val isDarkMode = resources.configuration.uiMode and 
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                
                // 首先检查是否存在 mCompletionWindow 字段
                val completionWindowField = try {
                    // 通过反射获取自动完成面板字段
                    CodeEditor::class.java.getDeclaredField("mCompletionWindow")
                } catch (e: NoSuchFieldException) {
                    // 新版本可能使用不同的字段名或实现方式，直接返回
                    android.util.Log.i("EditorFragment", "新版本不存在 mCompletionWindow 字段，跳过样式配置")
                    return@post
                }
                
                // 设置字段可访问
                completionWindowField.isAccessible = true
                // 获取当前编辑器的自动完成面板实例
                val completionWindow = completionWindowField.get(editor)
                
                if (completionWindow != null) {
                    // 根据当前主题模式定义颜色值
                    // 深色模式使用更暗的背景和亮色文本，浅色模式反之
                    val bgColor = if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFFEF7FF.toInt() // 背景色
                    val textColor = if (isDarkMode) 0xFFEEEEEE.toInt() else 0xFF000000.toInt() // 文本色
                    val selectedBgColor = if (isDarkMode) 0xFF3A3D41.toInt() else 0xFFE6D7F2.toInt() // 选中项背景色
                    
                    // 获取自动完成面板的类，用于后续反射操作
                    val completionWindowClass = completionWindow.javaClass
                    
                    // 设置背景颜色 - 使用反射调用setBackgroundColor方法
                    try {
                        val setBackgroundColorMethod = completionWindowClass.getDeclaredMethod("setBackgroundColor", Int::class.java)
                        setBackgroundColorMethod.invoke(completionWindow, bgColor)
                    } catch (e: Exception) {
                        android.util.Log.w("EditorFragment", "无法设置自动完成面板背景色: ${e.message}")
                        
                        // 尝试其他可能的方法名，以兼容不同版本的库
                        try {
                            val method = completionWindowClass.getDeclaredMethod("setColors")
                            method.invoke(completionWindow)
                        } catch (e2: Exception) {
                            // 忽略错误，确保程序继续运行
                        }
                    }
                    
                    // 设置文本颜色 - 使用反射调用setTextColor方法
                    try {
                        val setTextColorMethod = completionWindowClass.getDeclaredMethod("setTextColor", Int::class.java)
                        setTextColorMethod.invoke(completionWindow, textColor)
                    } catch (e: Exception) {
                        android.util.Log.w("EditorFragment", "无法设置自动完成面板文本颜色: ${e.message}")
                    }
                    
                    // 设置选中项背景色 - 使用反射调用setSelectedItemBackground方法
                    try {
                        val setSelectedItemBackgroundMethod = completionWindowClass.getDeclaredMethod("setSelectedItemBackground", Int::class.java)
                        setSelectedItemBackgroundMethod.invoke(completionWindow, selectedBgColor)
                    } catch (e: Exception) {
                        android.util.Log.w("EditorFragment", "无法设置自动完成面板选中项背景色: ${e.message}")
                    }
                    
                    // 设置启用状态 - 始终启用
                    try {
                        val setEnabledMethod = completionWindowClass.getDeclaredMethod("setEnabled", Boolean::class.java)
                        setEnabledMethod.invoke(completionWindow, true)
                    } catch (e: Exception) {
                        android.util.Log.w("EditorFragment", "无法设置自动完成面板启用状态: ${e.message}")
                    }
                    
                    // 尝试获取列表视图并直接设置样式
                    try {
                        // 获取自动完成面板中的ListView组件
                        val listViewField = completionWindowClass.getDeclaredField("mListView")
                        listViewField.isAccessible = true
                        val listView = listViewField.get(completionWindow) as? android.widget.ListView
                        
                        listView?.let { list ->
                            // 直接设置ListView的背景色
                            list.setBackgroundColor(bgColor)
                            
                            // 尝试设置适配器中的文本颜色
                            val adapter = list.adapter
                            if (adapter != null) {
                                val adapterClass = adapter.javaClass
                                try {
                                    // 尝试通过反射设置适配器的文本颜色
                                    val setTextColorMethod = adapterClass.getDeclaredMethod("setTextColor", Int::class.java)
                                    setTextColorMethod.invoke(adapter, textColor)
                                } catch (e: Exception) {
                                    // 忽略错误，继续尝试其他方法
                                }
                                
                                // 如果直接设置失败，尝试替换适配器
                                try {
                                    // 获取适配器的所有项
                                    val count = adapter.count
                                    val items = ArrayList<Any>()
                                    for (i in 0 until count) {
                                        items.add(adapter.getItem(i))
                                    }
                                    
                                    // 创建一个新的自定义适配器，覆盖getView方法以应用文本颜色
                                    val newAdapter = object : android.widget.ArrayAdapter<Any>(
                                        requireContext(),
                                        android.R.layout.simple_list_item_1,
                                        items
                                    ) {
                                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                            val view = super.getView(position, convertView, parent)
                                            
                                            // 设置文本颜色
                                            if (view is android.widget.TextView) {
                                                view.setTextColor(textColor)
                                            }
                                            
                                            return view
                                        }
                                    }
                                    
                                    // 应用新适配器到列表视图
                                    list.adapter = newAdapter
                                } catch (e: Exception) {
                                    android.util.Log.w("EditorFragment", "无法替换适配器: ${e.message}")
                                }
                            }
                            
                            // 添加列表项点击监听器，确保选中后能重新应用样式
                            // 这是必要的，因为选择项后自动完成面板可能会重置样式
                            list.setOnItemClickListener { _, _, _, _ ->
                                // 延迟执行，确保在点击处理完成后应用样式
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    configureCompletionPanelStyle()
                                }, 100)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("EditorFragment", "无法直接设置列表视图样式: ${e.message}")
                    }
                    
                    // 尝试设置自动完成面板的样式类
                    try {
                        // 尝试获取样式类字段
                        val styleField = completionWindowClass.getDeclaredField("mStyle")
                        styleField.isAccessible = true
                        val style = styleField.get(completionWindow)
                        
                        if (style != null) {
                            val styleClass = style.javaClass
                            
                            // 尝试设置样式类中的文本颜色属性
                            try {
                                val textColorField = styleClass.getDeclaredField("textColor")
                                textColorField.isAccessible = true
                                textColorField.set(style, textColor)
                            } catch (e: Exception) {
                                // 忽略错误
                            }
                            
                            // 尝试设置样式类中的背景颜色属性
                            try {
                                val bgColorField = styleClass.getDeclaredField("backgroundColor")
                                bgColorField.isAccessible = true
                                bgColorField.set(style, bgColor)
                            } catch (e: Exception) {
                                // 忽略错误
                            }
                            
                            // 尝试应用更新后的样式
                            try {
                                val applyMethod = completionWindowClass.getDeclaredMethod("applyStyle")
                                applyMethod.invoke(completionWindow)
                            } catch (e: Exception) {
                                // 忽略错误
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略样式类设置的错误，不影响其他功能
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "配置自动完成面板样式失败: ${e.message}")
            }
        }
    }

    /**
     * 注册主题变更监听器，在主题变更时重新应用自动完成面板样式
     */
    private fun registerThemeChangeListener() {
        try {
            // 获取当前Activity的Configuration对象
            val activity = requireActivity()
            
            // 创建Configuration监听器
            val callback = object : android.content.ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                    try {
                        // 检查是否是UI模式变化（深色/浅色模式切换）
                        if (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != 
                            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                            
                            // 重新配置编辑器颜色方案
                            editor.colorScheme = getCustomColorScheme()
                            
                            // 仅在库支持的情况下尝试重新创建自动完成面板
                            try {
                                if (CodeEditor::class.java.declaredFields.any { it.name == "mCompletionWindow" }) {
                                    // 强制重新创建自动完成面板
                                    forceRecreateCompletionWindow()
                                    
                                    // 重新配置自动完成面板样式
                                    configureCompletionPanelStyle()
                                }
                            } catch (e: Exception) {
                                // 如果失败，不影响主题切换的其他部分
                                android.util.Log.w("EditorFragment", "自动完成面板更新失败: ${e.message}")
                            }
                            
                            // 强制刷新编辑器
                            editor.invalidate()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("EditorFragment", "处理主题变更失败: ${e.message}")
                    }
                }
                
                override fun onLowMemory() {
                    // 不需要处理
                }
            }
            
            // 注册监听器
            activity.registerComponentCallbacks(callback)
            
            // 在Fragment销毁时取消注册
            view?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    // 不需要处理
                }
                
                override fun onViewDetachedFromWindow(v: View) {
                    activity.unregisterComponentCallbacks(callback)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "注册主题变更监听器失败: ${e.message}")
        }
    }
    
    /**
     * 强制重新创建自动完成面板
     * 这个方法通过反射销毁并重新创建自动完成面板，确保其样式正确应用
     */
    private fun forceRecreateCompletionWindow() {
        try {
            // 首先检查是否存在 mCompletionWindow 字段
            val completionWindowField = try {
                CodeEditor::class.java.getDeclaredField("mCompletionWindow")
            } catch (e: NoSuchFieldException) {
                // 新版本可能使用不同的字段名或实现方式，直接返回
                android.util.Log.i("EditorFragment", "新版本不存在 mCompletionWindow 字段，跳过重建")
                return
            }
            
            completionWindowField.isAccessible = true
            
            // 获取当前自动完成面板
            val currentWindow = completionWindowField.get(editor)
            
            // 如果存在，先销毁
            if (currentWindow != null) {
                try {
                    // 尝试调用销毁方法
                    val destroyMethod = currentWindow.javaClass.getDeclaredMethod("dismiss")
                    destroyMethod.invoke(currentWindow)
                } catch (e: Exception) {
                    // 忽略错误
                }
                
                // 设置为null
                completionWindowField.set(editor, null)
            }
            
            // 创建新的自动完成面板
            try {
                // 查找创建自动完成面板的方法
                val createMethod = CodeEditor::class.java.getDeclaredMethod("createCompletionWindow")
                createMethod.isAccessible = true
                createMethod.invoke(editor)
            } catch (e: Exception) {
                // 如果没有直接的创建方法，尝试通过其他方式触发创建
                try {
                    // 触发编辑器重新初始化
                    val initMethod = CodeEditor::class.java.getDeclaredMethod("onSizeChanged", Int::class.java, Int::class.java, Int::class.java, Int::class.java)
                    initMethod.isAccessible = true
                    val width = editor.width
                    val height = editor.height
                    initMethod.invoke(editor, width, height, width, height)
                } catch (e2: Exception) {
                    android.util.Log.w("EditorFragment", "无法触发自动完成面板重新创建: ${e2.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "强制重新创建自动完成面板失败: ${e.message}")
        }
    }

    /**
     * 更新编辑器字体大小
     * 
     * @param fontSize 要设置的字体大小
     */
    fun updateFontSize(fontSize: Float) {
        if (::editor.isInitialized && !isUpdatingFontSize) {
            isUpdatingFontSize = true
            
            try {
                // 设置编辑器字体大小
                editor.setTextSize(fontSize)
                
                // 通知MainActivity保存设置
                (activity as? MainActivity)?.let { mainActivity ->
                    val prefs = PreferenceManager.getDefaultSharedPreferences(mainActivity)
                    prefs.edit().putFloat(SettingsFragment.PREF_FONT_SIZE, fontSize).apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "更新字体大小失败: ${e.message}")
            }
            
            // 防止无限循环
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                isUpdatingFontSize = false
            }, 100)
        }
    }

    /**
     * 获取编辑器当前字体大小
     * 
     * @return 当前字体大小（以sp为单位）
     */
    private fun getTextSize(): Float {
        return if (::editor.isInitialized) {
            try {
                // 尝试反射获取字体大小
                val field = CodeEditor::class.java.getDeclaredField("mTextSizePx")
                field.isAccessible = true
                val textSizePx = field.getFloat(editor)
                
                // 将像素转换为sp（尺寸无关像素）
                textSizePx / resources.displayMetrics.scaledDensity
            } catch (e: Exception) {
                // 反射失败，使用默认字体大小
                android.util.Log.w("EditorFragment", "获取字体大小失败: ${e.message}")
                SettingsFragment.DEFAULT_FONT_SIZE
            }
        } else {
            SettingsFragment.DEFAULT_FONT_SIZE
        }
    }

    /**
     * 设置光标宽度
     * 使用多种方法尝试设置光标宽度，确保至少一种方法生效
     * @param widthInDp 光标宽度（单位：dp）
     */
    private fun setCursorWidth(widthInDp: Float) {
        try {
            android.util.Log.d("EditorFragment", "尝试设置光标宽度为 $widthInDp dp")
            
            // 1. 尝试使用直接字段访问
            try {
                val cursorWidthField = CodeEditor::class.java.getDeclaredField("mCursorWidth")
                cursorWidthField.isAccessible = true
                val widthInPixels = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    widthInDp,
                    resources.displayMetrics
                )
                cursorWidthField.setFloat(editor, widthInPixels)
                android.util.Log.d("EditorFragment", "方法1：通过字段设置光标宽度成功")
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "方法1失败: ${e.message}")
            }
            
            // 2. 尝试使用setCursorWidth方法
            try {
                val setCursorWidthMethod = CodeEditor::class.java.getDeclaredMethod("setCursorWidth", Float::class.java)
                setCursorWidthMethod.isAccessible = true
                setCursorWidthMethod.invoke(editor, widthInDp)
                android.util.Log.d("EditorFragment", "方法2：通过方法设置光标宽度成功")
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "方法2失败: ${e.message}")
            }
            
            // 3. 尝试修改InsertHandle的宽度
            try {
                val insertHandleField = CodeEditor::class.java.getDeclaredField("mInsertHandle")
                insertHandleField.isAccessible = true
                val insertHandle = insertHandleField.get(editor)
                if (insertHandle != null) {
                    val insertHandleClass = insertHandle.javaClass
                    val widthField = insertHandleClass.getDeclaredField("width")
                    widthField.isAccessible = true
                    val widthInPixels = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        widthInDp,
                        resources.displayMetrics
                    )
                    widthField.setFloat(insertHandle, widthInPixels)
                    android.util.Log.d("EditorFragment", "方法3：通过修改InsertHandle宽度成功")
                }
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "方法3失败: ${e.message}")
            }
            
            // 4. 尝试修改渲染器中的光标宽度
            try {
                val rendererField = CodeEditor::class.java.getDeclaredField("mRenderer")
                rendererField.isAccessible = true
                val renderer = rendererField.get(editor)
                if (renderer != null) {
                    val rendererClass = renderer.javaClass
                    
                    // 尝试找到所有可能与光标宽度相关的字段
                    val possibleFields = listOf("cursorWidth", "mCursorWidth", "insertWidth", "insertHandleWidth")
                    
                    for (fieldName in possibleFields) {
                        try {
                            val field = rendererClass.getDeclaredField(fieldName)
                            field.isAccessible = true
                            val widthInPixels = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                widthInDp,
                                resources.displayMetrics
                            )
                            field.setFloat(renderer, widthInPixels)
                            android.util.Log.d("EditorFragment", "方法4：通过修改渲染器字段 $fieldName 成功")
                        } catch (e: Exception) {
                            // 忽略单个字段的错误
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "方法4失败: ${e.message}")
            }
            
            // 5. 尝试修改EditorPainter中的光标宽度
            try {
                val painterField = CodeEditor::class.java.getDeclaredField("mPainter")
                painterField.isAccessible = true
                val painter = painterField.get(editor)
                if (painter != null) {
                    val painterClass = painter.javaClass
                    val possibleFields = listOf("cursorWidth", "mCursorWidth", "insertWidth")
                    
                    for (fieldName in possibleFields) {
                        try {
                            val field = painterClass.getDeclaredField(fieldName)
                            field.isAccessible = true
                            val widthInPixels = TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                widthInDp,
                                resources.displayMetrics
                            )
                            field.setFloat(painter, widthInPixels)
                            android.util.Log.d("EditorFragment", "方法5：通过修改绘制器字段 $fieldName 成功")
                        } catch (e: Exception) {
                            // 忽略单个字段的错误
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "方法5失败: ${e.message}")
            }
            
            // 6. 尝试使用反射调用任何可能的设置光标宽度的方法
            try {
                val methods = CodeEditor::class.java.declaredMethods
                for (method in methods) {
                    if (method.name.contains("cursor", ignoreCase = true) && 
                        method.name.contains("width", ignoreCase = true) && 
                        method.parameterTypes.size == 1 && 
                        (method.parameterTypes[0] == Float::class.java || 
                         method.parameterTypes[0] == Int::class.java)) {
                        
                        method.isAccessible = true
                        val widthParam = if (method.parameterTypes[0] == Float::class.java) {
                            widthInDp
                        } else {
                            widthInDp.toInt()
                        }
                        method.invoke(editor, widthParam)
                        android.util.Log.d("EditorFragment", "方法6：通过方法 ${method.name} 设置光标宽度成功")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("EditorFragment", "方法6失败: ${e.message}")
            }
            
            // 7. 强制刷新编辑器，确保更改生效
            editor.postInvalidate()
            
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "设置光标宽度失败: ${e.message}")
        }
    }

    /**
     * 更新编辑器光标宽度
     * 
     * @param widthInDp 要设置的光标宽度（单位：dp）
     */
    fun updateCursorWidth(widthInDp: Float) {
        if (::editor.isInitialized) {
            try {
                // 设置光标宽度
                setCursorWidth(widthInDp)
                
                // 保存设置到SharedPreferences
                (activity as? MainActivity)?.let { mainActivity ->
                    val prefs = PreferenceManager.getDefaultSharedPreferences(mainActivity)
                    prefs.edit().putFloat(SettingsFragment.PREF_CURSOR_WIDTH, widthInDp).apply()
                }
                
                android.util.Log.d("EditorFragment", "光标宽度已更新为 $widthInDp dp")
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "更新光标宽度失败: ${e.message}")
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear() // 清除旧菜单
        inflater.inflate(R.menu.editor_menu, menu)
        runMenuItem = menu.findItem(R.id.action_run)
        symbolPanelMenuItem = menu.findItem(R.id.action_toggle_symbol_panel)
        updateSymbolPanelMenuIcon()
        super.onCreateOptionsMenu(menu, inflater)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_run -> {
                if (isIOPanelOpen) {
                    // 如果IO面板已打开，则关闭它
                    closeIOPanel()
                } else {
                    // 运行代码前先保存
                    saveContent(false)
                    
                    // 打开IO面板
                    openIOPanel()
                }
                true
            }
            R.id.action_toggle_symbol_panel -> {
                toggleSymbolPanel()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * 切换符号面板的可见性
     */
    private fun toggleSymbolPanel() {
        isSymbolPanelVisible = !isSymbolPanelVisible
        setSymbolPanelVisibility(isSymbolPanelVisible)
        updateSymbolPanelMenuIcon()
        
        // 显示提示
        val message = if (isSymbolPanelVisible) 
            getString(R.string.symbol_panel_shown) 
        else 
            getString(R.string.symbol_panel_hidden)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 更新符号面板菜单图标
     */
    private fun updateSymbolPanelMenuIcon() {
        symbolPanelMenuItem?.let {
            if (isSymbolPanelVisible) {
                it.setIcon(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                it.setIcon(android.R.drawable.ic_menu_sort_by_size)
            }
        }
    }

    companion object {
        private const val ARG_LANGUAGE = "language"
        private const val ARG_FILENAME = "filename"

        @JvmStatic
        fun newInstance(language: String, fileName: String) =
            EditorFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LANGUAGE, language)
                    putString(ARG_FILENAME, fileName)
                }
            }
    }
} 