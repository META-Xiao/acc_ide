package com.acc_ide.ui.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.core.view.MenuProvider
import com.google.android.material.button.MaterialButton
import com.acc_ide.R
import com.acc_ide.ui.main.MainActivity
import com.acc_ide.ui.iopanel.IOPanelFragment
import com.acc_ide.ui.settings.SettingsFragment
import com.acc_ide.util.TextMateManager
import com.acc_ide.view.SymbolPanelView
import com.acc_ide.lsp.core.LspManager
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019
import io.github.rosemoe.sora.event.ContentChangeEvent
import android.util.TypedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * Code editor fragment - provides code editing functionality with syntax highlighting
 * 代码编辑器Fragment - 提供代码编辑功能和语法高亮
 */
class EditorFragment : Fragment() {
    lateinit var editor: CodeEditor
    private lateinit var language: String
    lateinit var fileName: String

    // Track current language scope name
    private var currentLanguageScopeName: String? = null
    private var isIOPanelOpen = false
    private var runMenuItem: MenuItem? = null
    private var undoMenuItem: MenuItem? = null
    private var redoMenuItem: MenuItem? = null
    private var hasUnsavedChanges = false
    private var isUpdatingFontSize = false // Prevent zoom listener and settings update from triggering each other

    // Symbol panel components
    private lateinit var symbolPanel: SymbolPanelView
    private var isSymbolPanelVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // We've set up the menu in onViewCreated instead of here
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_editor, container, false)
        editor = view.findViewById(R.id.editor_view)
        symbolPanel = view.findViewById(R.id.symbol_panel)

        // Get arguments
        arguments?.let {
            language = it.getString(ARG_LANGUAGE, "text")
            fileName = it.getString(ARG_FILENAME, "")
        }

        // Configure basic editor settings
        configureEditor()

        // Use coroutines to set content and language support asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mainActivity = activity as MainActivity
                val fileContent = mainActivity.files[fileName] ?: ""

                // Preload language support in background thread
                val fileExtension = when (language) {
                    "java" -> "java"
                    "cpp" -> "cpp"
                    "python" -> "py"
                    else -> "txt"
                }

                val languageMapping = com.acc_ide.util.TextMateManager.getLanguageMapping()
                val scopeName = languageMapping[fileExtension] ?: "text.plain"

                // Pre-create language instance and put into cache
                EditorFragment.getOrCreateLanguage(scopeName)
                android.util.Log.d("EditorFragment", "Preloaded language: $scopeName")

                // Switch to main thread only for UI operations
                withContext(Dispatchers.Main) {
                    // Set content first (fast operation)
                    editor.setText(fileContent)

                    // Setup language support (uses cached language)
                    setupLanguageSupport()

                    // Setup other components
                    setupContentChangeListener()
                    initSymbolPanel()

                    // Force refresh in next frame
                    editor.post {
                        editor.invalidate()
                        editor.postInvalidate()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Error loading content: ${e.message}")
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set title
        val mainActivity = activity as MainActivity
        mainActivity.supportActionBar?.title = fileName

        // Set menu provider - after view is created to ensure safe access to viewLifecycleOwner
        activity?.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear() // Clear old menu
                menuInflater.inflate(R.menu.editor_menu, menu)
                runMenuItem = menu.findItem(R.id.action_run)
                undoMenuItem = menu.findItem(R.id.action_undo)
                redoMenuItem = menu.findItem(R.id.action_redo)
                updateUndoRedoMenuState()
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_run -> {
                        if (isIOPanelOpen) {
                            closeIOPanel()
                        } else {
                            // Save before running code
                            saveContent()
                            openIOPanel()
                        }
                        true
                    }

                    R.id.action_undo -> {
                        performUndo()
                        true
                    }

                    R.id.action_redo -> {
                        performRedo()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner)

        // Initialize interface views
        initViews()

        // Configure language support
        configureLanguage()

        // Apply editor settings
        applySettingsFromPreferences()

        // Register theme change listener
        registerThemeChangeListener()

        // Configure auto completion component colors
        configureAutoCompletionColors()

        // Set auto completion component enabled state
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val autoCompletionEnabled =
            prefs.getBoolean(SettingsFragment.PREF_ENABLE_AUTO_COMPLETION, true)
        setAutoCompletionEnabled(autoCompletionEnabled)

        // Set LSP enabled state
        val lspEnabled = prefs.getBoolean(SettingsFragment.PREF_ENABLE_LSP, true)
        setLspEnabled(lspEnabled)

        // Set cursor width immediately without delay to prevent post-frame lag
        val cursorWidth = prefs.getFloat(
            SettingsFragment.PREF_CURSOR_WIDTH,
            SettingsFragment.DEFAULT_CURSOR_WIDTH
        )
        setCursorWidth(cursorWidth)
    }

    override fun onResume() {
        super.onResume()

        // Ensure title bar shows current file name
        (activity as? MainActivity)?.let { mainActivity ->
            mainActivity.supportActionBar?.title = fileName
        }

        // Apply settings only once
        applySettingsFromPreferences()

        android.util.Log.d("EditorFragment", "onResume completed")
    }

    /**
     * Read settings from SharedPreferences and apply them to editor and symbol panel
     * 从SharedPreferences读取设置并应用到编辑器和符号面板
     */
    private fun applySettingsFromPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val symbolPanelEnabled = prefs.getBoolean(SettingsFragment.PREF_ENABLE_SYMBOL_PANEL, true)

        // Apply to symbol panel
        setSymbolPanelVisibility(symbolPanelEnabled)
        isSymbolPanelVisible = symbolPanelEnabled

        // Apply cursor width settings
        val cursorWidth = prefs.getFloat(
            SettingsFragment.PREF_CURSOR_WIDTH,
            SettingsFragment.DEFAULT_CURSOR_WIDTH
        )
        updateCursorWidth(cursorWidth)
    }

    /**
     * Initialize symbol panel
     * 初始化符号面板
     */
    private fun initSymbolPanel() {
        try {
            // Set editor instance to symbol panel
            symbolPanel.setEditor(editor)

            // Set initial visibility
            setSymbolPanelVisibility(isSymbolPanelVisible)

        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Failed to initialize symbol panel: ${e.message}")
        }
    }

    /**
     * Set symbol panel visibility
     * 设置符号面板的可见性
     */
    private fun setSymbolPanelVisibility(visible: Boolean) {
        symbolPanel.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Initialize interface views
     * 初始化界面视图
     */
    private fun initViews() {
        try {
            // Initialize other UI elements of editor view (like toolbar, buttons, etc.)
            // Add editor-related view initialization code here

            editor.setOnLongClickListener {
                // Show context menu or perform other operations
                true
            }

            android.util.Log.d("EditorFragment", "View initialization completed")
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Error initializing views: ${e.message}")
        }
    }

    /**
     * Configure language support based on file type
     * 根据文件类型配置语言支持
     */
    private fun configureLanguage() {
        try {
            // Ensure editor is initialized
            if (::editor.isInitialized) {
                // Skip reconfiguration if file type hasn't changed
                val fileExtension = when (language) {
                    "java" -> "java"
                    "kotlin" -> "kt"
                    "python" -> "py"
                    "cpp" -> "cpp"
                    else -> "txt"
                }
                val languageMapping = com.acc_ide.util.TextMateManager.getLanguageMapping()
                val scopeName = languageMapping[fileExtension] ?: "text.plain"

                // Skip duplicate language setup if language hasn't changed
                if (currentLanguageScopeName == scopeName) {
                    android.util.Log.d("EditorFragment", "Language unchanged, skipping reconfiguration: $scopeName")
                    return
                }

                // Set language
                setupLanguageSupport()

                android.util.Log.d("EditorFragment", "Editor configured for $language language")
            }
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Error configuring language support: ${e.message}")
        }
    }

    private fun setupContentChangeListener() {
        // Listen for text changes to mark unsaved state
        try {
            editor.subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                hasUnsavedChanges = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveContent() {
        val content = editor.text.toString()
        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            // Update file content in memory
            it.files[fileName] = content

            // Also update storage
            it.updateFileContent(fileName, content)

            // Reset unsaved state
            hasUnsavedChanges = false
        }
    }

    private fun openIOPanel() {
        // Check if IO panel is already open
        val existingPanel = parentFragmentManager.findFragmentByTag("io_panel")
        if (existingPanel != null) {
            // If panel is already open, remove it first
            parentFragmentManager.beginTransaction()
                .remove(existingPanel)
                .commit()
        }

        // Create and show IO panel
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

        // Update state and icon
        isIOPanelOpen = true
        updateRunMenuIcon()
    }

    private fun closeIOPanel() {
        // Find IO panel
        val ioPanel = parentFragmentManager.findFragmentByTag("io_panel")
        if (ioPanel != null) {
            // Remove IO panel
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_left,
                    R.anim.slide_out_right
                )
                .remove(ioPanel)
                .commit()

            // Pop back stack
            parentFragmentManager.popBackStack()

            // Ensure Activity restores correct navigation bar state
            (activity as? MainActivity)?.let { mainActivity ->
                // Delay execution to ensure Fragment transition is complete
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // Directly call MainActivity's navigation bar update method
                    mainActivity.updateNavigationIcon()
                }, 100)
            }
        }

        // Update state and icon
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
        // Save content only when there are unsaved changes
        if (hasUnsavedChanges) {
            saveContent()
        }
    }

    /**
     * Configure editor properties - set editor appearance, behavior and functionality
     * 配置编辑器属性 - 设置编辑器的外观、行为和功能
     */
    private fun configureEditor() {
        // Set editor properties
        editor.apply {
            isWordwrap = true  // Enable word wrap
            tabWidth = 4  // Set tab width to 4 spaces
            isLineNumberEnabled = true  // Show line numbers

            // Set custom font
            try {
                val fontPath = "fonts/AgaveNerdFontMono-Regular.ttf"
                val typeface = android.graphics.Typeface.createFromAsset(context.assets, fontPath)
                setTypefaceText(typeface)
                android.util.Log.d("EditorFragment", "Applied custom font: $fontPath")
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Failed to set custom font: ${e.message}")
                e.printStackTrace()
            }
            setupLanguageSupport()// Enable syntax highlighting
            isUndoEnabled = true  // Enable undo/redo functionality
            subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                // Mark unsaved changes
                hasUnsavedChanges = true
                // Update undo/redo button state
                updateUndoRedoMenuState()
            }

            // Get saved font size from app preferences
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            val fontSize = sharedPrefs.getFloat(
                SettingsFragment.PREF_FONT_SIZE,
                SettingsFragment.DEFAULT_FONT_SIZE
            )

            // Set editor font size
            setTextSize(fontSize)

            // Set cursor width - read from settings
            val cursorWidth = sharedPrefs.getFloat(
                SettingsFragment.PREF_CURSOR_WIDTH,
                SettingsFragment.DEFAULT_CURSOR_WIDTH
            )
            setCursorWidth(cursorWidth)

            // Enable scaling functionality, allow users to adjust font size through gestures
            isScalable = true

            // Set beautification options: show whitespace characters
            nonPrintablePaintingFlags = CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
                    CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION

            // Enable block line indicators - visually distinguish code blocks
            isBlockLineEnabled = true

            // Auto-completion panel style is handled by TextMateManager
        }
        
        // Configure custom scrollbar style
        configureScrollbars()
    }

    /**
     * Configure custom scrollbar style and appearance
     * 配置自定义滚动条样式和外观
     */
    private fun configureScrollbars() {
        try {
            // Enable both vertical and horizontal scrollbars
            editor.isVerticalScrollBarEnabled = true
            editor.isHorizontalScrollBarEnabled = true
            
            // Check if dark theme is active
            val isDarkTheme = (resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            if (isDarkTheme) {
                // Apply dark theme scrollbar drawables
                val thumbDrawable = androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), 
                    R.drawable.scrollbar_thumb_selector_dark
                )
                val trackDrawable = androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), 
                    R.drawable.scrollbar_track_dark
                )
                
                // Set vertical scrollbar style
                editor.setVerticalScrollbarThumbDrawable(thumbDrawable)
                editor.setVerticalScrollbarTrackDrawable(trackDrawable)
                
                // Set horizontal scrollbar style  
                editor.setHorizontalScrollbarThumbDrawable(thumbDrawable)
                editor.setHorizontalScrollbarTrackDrawable(trackDrawable)
                
                android.util.Log.d("EditorFragment", "Applied dark theme scrollbar style")
            } else {
                // Apply light theme scrollbar drawables
                val thumbDrawable = androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), 
                    R.drawable.scrollbar_thumb_selector_light
                )
                val trackDrawable = androidx.core.content.ContextCompat.getDrawable(
                    requireContext(), 
                    R.drawable.scrollbar_track_light
                )
                
                // Set vertical scrollbar style
                editor.setVerticalScrollbarThumbDrawable(thumbDrawable)
                editor.setVerticalScrollbarTrackDrawable(trackDrawable)
                
                // Set horizontal scrollbar style  
                editor.setHorizontalScrollbarThumbDrawable(thumbDrawable)
                editor.setHorizontalScrollbarTrackDrawable(trackDrawable)
                
                android.util.Log.d("EditorFragment", "Applied light theme scrollbar style")
            }
            
            // Configure scrollbar colors through ColorScheme
            val colorScheme = editor.colorScheme
                if (isDarkTheme) {
                    // Set scrollbar colors for dark theme
                    colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_THUMB, 0x66FFFFFF.toInt())
                    colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, 0x99FFFFFF.toInt())
                    colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_TRACK, 0x1A000000.toInt())
                } else {
                    // Set scrollbar colors for light theme
                    colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_THUMB, 0x66000000.toInt())
                    colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, 0x99000000.toInt())
                    colorScheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SCROLL_BAR_TRACK, 0x1AFFFFFF.toInt())
            }
            
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Failed to configure scrollbars: ${e.message}")
        }
    }

    /**
     * Register theme change listener to reapply auto-completion panel style when theme changes
     * 注册主题变更监听器，在主题变更时重新应用自动完成面板样式
     */
    private fun registerThemeChangeListener() {
        try {
            // Get current Activity's Configuration object
            val activity = requireActivity()

            // Create Configuration listener
            val callback = object : android.content.ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                    try {
                        // Check if it's UI mode change (dark/light mode switch)
                        if (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
                            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        ) {

                            // Apply TextMate color scheme
                            editor.colorScheme =
                                io.github.rosemoe.sora.langs.textmate.TextMateColorScheme.create(
                                    io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry.getInstance()
                                )

                            // Configure auto-completion component colors
                            configureAutoCompletionColors()
                            
                            // Reconfigure scrollbars for theme change
                            configureScrollbars()

                            // Force refresh editor
                            editor.invalidate()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("EditorFragment", "Failed to handle theme change: ${e.message}")
                    }
                }

                override fun onLowMemory() {
                    // No need to handle
                }
            }

            // Register listener
            activity.registerComponentCallbacks(callback)

            // Unregister when Fragment is destroyed
            view?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    // No need to handle
                }

                override fun onViewDetachedFromWindow(v: View) {
                    activity.unregisterComponentCallbacks(callback)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Failed to register theme change listener: ${e.message}")
        }
    }

    /**
     * Setup language support with TextMate syntax highlighting
     * 设置语言支持 - 配置TextMate语言支持和语法高亮
     */
    internal fun setupLanguageSupport(specifiedLanguage: String? = null) {
        try {
            // 检查是否已经设置了LSP语言支持
            val currentLanguage = editor.editorLanguage
            if (currentLanguage is io.github.rosemoe.sora.lsp.editor.LspLanguage) {
                android.util.Log.d("EditorFragment", "LSP language already set, skipping TextMate setup to avoid conflicts")
                
                // 只更新颜色方案和自动补全颜色，不覆盖语言设置
                if (editor.colorScheme !is io.github.rosemoe.sora.langs.textmate.TextMateColorScheme) {
                    editor.colorScheme = getOrCreateColorScheme()
                }
                configureAutoCompletionColors()
                editor.postInvalidate()
                return
            }
            
            val fileExtension = when (specifiedLanguage ?: language) {
                "java" -> "java"
                "cpp" -> "cpp"
                "python" -> "py"
                else -> "txt"
            }

            val languageMapping = com.acc_ide.util.TextMateManager.getLanguageMapping()
            val scopeName = languageMapping[fileExtension] ?: "text.plain"

            // Skip configuration if language hasn't changed and no specific language is specified
            if (currentLanguageScopeName == scopeName && specifiedLanguage == null) {
                android.util.Log.d("EditorFragment", "Skip duplicate language setup: $scopeName")
                return
            }

            // Update current language scope
            currentLanguageScopeName = scopeName

            // Use cache to get TextMateLanguage instance (this should be fast due to preloading)
            val textMateLanguage = EditorFragment.getOrCreateLanguage(scopeName)

            // Apply TextMate color scheme only if needed
            if (editor.colorScheme !is io.github.rosemoe.sora.langs.textmate.TextMateColorScheme) {
                editor.colorScheme = getOrCreateColorScheme()
            }

            // Set language (only if not LSP)
            editor.setEditorLanguage(textMateLanguage)

            // Configure auto-completion component colors
            configureAutoCompletionColors()

            // Force refresh editor to ensure syntax highlighting is applied immediately
            editor.postInvalidate()

            android.util.Log.d(
                "EditorFragment",
                "Set up ${specifiedLanguage ?: this.language} language support (TextMate: $scopeName)"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Fall back to empty language parser when error occurs to ensure editor remains usable
            editor.setEditorLanguage(io.github.rosemoe.sora.lang.EmptyLanguage())
            android.util.Log.e(
                "EditorFragment",
                "Error setting up language support: ${e.message}, fell back to empty language parser"
            )
        }
    }

    /**
     * Update editor font size
     * 更新编辑器字体大小
     * @param fontSize Font size to set
     */
    fun updateFontSize(fontSize: Float) {
        if (::editor.isInitialized && !isUpdatingFontSize) {
            isUpdatingFontSize = true

            try {
                // Set editor font size
                editor.setTextSize(fontSize)

                // Notify MainActivity to save settings
                (activity as? MainActivity)?.let { mainActivity ->
                    val prefs = PreferenceManager.getDefaultSharedPreferences(mainActivity)
                    prefs.edit().putFloat(SettingsFragment.PREF_FONT_SIZE, fontSize).apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Failed to update font size: ${e.message}")
            }

            // Prevent infinite loop
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                isUpdatingFontSize = false
            }, 100)
        }
    }

    /**
     * Set cursor width using effective method
     * 设置光标宽度
     * @param widthInDp Cursor width in dp
     */
    private fun setCursorWidth(widthInDp: Float) {
        try {
            android.util.Log.d("EditorFragment", "Attempting to set cursor width to $widthInDp dp")

            // Try using setCursorWidth method
            try {
                val setCursorWidthMethod =
                    CodeEditor::class.java.getDeclaredMethod("setCursorWidth", Float::class.java)
                setCursorWidthMethod.isAccessible = true
                setCursorWidthMethod.invoke(editor, widthInDp)
                android.util.Log.d("EditorFragment", "Method 2: Successfully set cursor width via method")
            } catch (e: Exception) {
                // If method 2 fails, try method 6
                try {
                    val methods = CodeEditor::class.java.declaredMethods
                    for (method in methods) {
                        if (method.name.contains("cursor", ignoreCase = true) &&
                            method.name.contains("width", ignoreCase = true) &&
                            method.parameterTypes.size == 1 &&
                            (method.parameterTypes[0] == Float::class.java ||
                                    method.parameterTypes[0] == Int::class.java)
                        ) {
                            method.isAccessible = true
                            val widthParam = if (method.parameterTypes[0] == Float::class.java) {
                                widthInDp
                            } else {
                                widthInDp.toInt()
                            }
                            method.invoke(editor, widthParam)
                            android.util.Log.d(
                                "EditorFragment",
                                "Method 6: Successfully set cursor width via method ${method.name}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("EditorFragment", "Failed to set cursor width: ${e.message}")
                }
            }

            // Force refresh editor to ensure changes take effect
            editor.postInvalidate()

        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Failed to set cursor width: ${e.message}")
        }
    }

    /**
     * Update editor cursor width
     * 更新编辑器光标宽度
     * @param widthInDp Cursor width to set (in dp)
     */
    fun updateCursorWidth(widthInDp: Float) {
        if (::editor.isInitialized) {
            try {
                // Set cursor width
                setCursorWidth(widthInDp)

                // Save settings to SharedPreferences
                (activity as? MainActivity)?.let { mainActivity ->
                    val prefs = PreferenceManager.getDefaultSharedPreferences(mainActivity)
                    prefs.edit().putFloat(SettingsFragment.PREF_CURSOR_WIDTH, widthInDp).apply()
                }

                android.util.Log.d("EditorFragment", "Cursor width updated to $widthInDp dp")
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Failed to update cursor width: ${e.message}")
            }
        }
    }

    /**
     * Set auto-completion component enabled state
     * 设置自动补全组件的启用状态
     * @param enabled Whether to enable auto-completion component
     */
    fun setAutoCompletionEnabled(enabled: Boolean) {
        if (::editor.isInitialized) {
            try {
                // Get auto-completion component
                val autoCompletion =
                    editor.getComponent(io.github.rosemoe.sora.widget.component.EditorAutoCompletion::class.java)

                // Set component enabled state
                autoCompletion.isEnabled = enabled

                android.util.Log.d("EditorFragment", "Auto-completion component state set: $enabled")
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Failed to set auto-completion component state: ${e.message}")
            }
        }
    }

    /**
     * Set Language Server Protocol (LSP) enabled state
     * 设置语言服务器协议(LSP)的启用状态
     * @param enabled Whether to enable LSP features
     */
    fun setLspEnabled(enabled: Boolean) {
        if (::editor.isInitialized) {
            try {
                val lspManager = LspManager.getInstance(requireContext())
                
                if (enabled) {
                    // 启用LSP支持
                    if (lspManager.isLanguageSupported(language)) {
                        val success = lspManager.enableLspForEditor(editor, language)
                        if (success) {
                            android.util.Log.d("EditorFragment", "LSP enabled successfully for $language")
                        } else {
                            android.util.Log.w("EditorFragment", "Failed to enable LSP for $language")
                        }
                    } else {
                        android.util.Log.w("EditorFragment", "Language $language not supported for LSP")
                    }
                } else {
                    // 禁用LSP支持
                    lspManager.disableLspForEditor(editor, language)
                    android.util.Log.d("EditorFragment", "LSP disabled for $language")
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Failed to set LSP state: ${e.message}")
            }
        }
    }

    /**
     * Perform undo operation
     * 执行撤销操作
     */
    private fun performUndo() {
        try {
            if (::editor.isInitialized && editor.canUndo()) {
                editor.undo()
                updateUndoRedoMenuState()
            } else {
                Toast.makeText(context, "Cannot undo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Undo operation failed: ${e.message}")
            Toast.makeText(context, "Undo operation failed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Perform redo operation
     * 执行重做操作
     */
    private fun performRedo() {
        try {
            if (::editor.isInitialized && editor.canRedo()) {
                editor.redo()
                updateUndoRedoMenuState()
            } else {
                Toast.makeText(context, "Cannot redo", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Redo operation failed: ${e.message}")
            Toast.makeText(context, "Redo operation failed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Update undo/redo menu item state
     * 更新撤销/重做菜单项状态
     */
    private fun updateUndoRedoMenuState() {
        undoMenuItem?.isEnabled = editor.canUndo()
        redoMenuItem?.isEnabled = editor.canRedo()

        // Optional: adjust icon transparency based on enabled state
        undoMenuItem?.icon?.alpha = if (editor.canUndo()) 255 else 128
        redoMenuItem?.icon?.alpha = if (editor.canRedo()) 255 else 128
    }

    /**
     * Get appropriate language type based on file name
     * 根据文件名获取适合的语言类型
     * @param fileName File name
     * @return Corresponding language type (java, cpp, python or text)
     */
    fun getLanguageForFile(fileName: String): String {
        return when {
            fileName.endsWith(".java") -> "java"
            fileName.endsWith(".cpp") || fileName.endsWith(".c") || fileName.endsWith(".h") || fileName.endsWith(
                ".hpp"
            ) -> "cpp"

            fileName.endsWith(".py") -> "python"
            else -> "text"
        }
    }

    /**
     * Ensure language configuration is correctly applied
     * 确保语言配置被正确应用
     */
    fun ensureLanguageApplied() {
        if (::editor.isInitialized) {
            try {
                // Check if language is currently applied
                val hasLanguageSet = editor.editorLanguage !is io.github.rosemoe.sora.lang.EmptyLanguage

                // If no language is set or using EmptyLanguage, re-apply language support
                if (!hasLanguageSet) {
                    android.util.Log.d("EditorFragment", "Detected editor has no language support, re-applying")
                    setupLanguageSupport()
                } else {
                    android.util.Log.d("EditorFragment", "Editor already has language support, no need to re-apply")
                }
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Error ensuring language application: ${e.message}")
                // Try to re-apply language when error occurs
                setupLanguageSupport()
            }
        }
    }

    /**
     * Force refresh editor theme and syntax highlighting
     * 强制刷新编辑器主题和语法高亮
     */
    fun refreshEditorTheme() {
        if (::editor.isInitialized) {
            try {
                // Clear color scheme cache when theme changes
                clearColorSchemeCache()

                // Ensure language configuration is correctly applied
                ensureLanguageApplied()

                // Update color scheme with cached version
                editor.colorScheme = getOrCreateColorScheme()

                // Configure auto-completion component colors
                configureAutoCompletionColors()
                
                // Reconfigure scrollbars for theme refresh
                configureScrollbars()

                // Force redraw editor
                editor.invalidate()
                editor.postInvalidate()

                android.util.Log.d("EditorFragment", "Editor theme refresh completed")
            } catch (e: Exception) {
                android.util.Log.e("EditorFragment", "Failed to refresh editor theme: ${e.message}")
            }
        }
    }

    /**
     * Configure auto-completion component colors based on current theme mode
     * 根据当前主题模式配置自动补全组件的颜色
     */
    private fun configureAutoCompletionColors() {
        try {
            // Detect current theme mode
            val isDarkMode = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            // Get editor color scheme
            val scheme = editor.colorScheme

            if (isDarkMode) {
                // Dark mode colors
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_BACKGROUND,
                    0xFF2C2C2C.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_CORNER,
                    0xFF3A3D41.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY,
                    0xFFEEEEEE.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY,
                    0xFFAAAAAA.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_ITEM_CURRENT,
                    0xFF3A3D41.toInt()
                )
            } else {
                // Light mode colors
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_BACKGROUND,
                    0xFFFEF7FF.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_CORNER,
                    0xFFE6D7F2.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY,
                    0xFF000000.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY,
                    0xFF666666.toInt()
                )
                scheme.setColor(
                    io.github.rosemoe.sora.widget.schemes.EditorColorScheme.COMPLETION_WND_ITEM_CURRENT,
                    0xFFE6D7F2.toInt()
                )
            }

            android.util.Log.d("EditorFragment", "Configured auto-completion component colors, dark mode: $isDarkMode")
        } catch (e: Exception) {
            android.util.Log.e("EditorFragment", "Failed to configure auto-completion component colors: ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        private const val ARG_LANGUAGE = "language"
        private const val ARG_FILENAME = "filename"

        // Cache for TextMateLanguage instances to improve performance
        private val languageCache = mutableMapOf<String, io.github.rosemoe.sora.langs.textmate.TextMateLanguage>()
        
        // Cache for TextMateColorScheme to avoid recreating it
        private var cachedColorScheme: io.github.rosemoe.sora.langs.textmate.TextMateColorScheme? = null

        // Get or create color scheme instance
        private fun getOrCreateColorScheme(): io.github.rosemoe.sora.langs.textmate.TextMateColorScheme {
            if (cachedColorScheme == null) {
                cachedColorScheme = io.github.rosemoe.sora.langs.textmate.TextMateColorScheme.create(
                    io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry.getInstance()
                )
                android.util.Log.d("EditorFragment", "Created new TextMateColorScheme instance")
            }
            return cachedColorScheme!!
        }

        // Clear color scheme cache when theme changes
        fun clearColorSchemeCache() {
            cachedColorScheme = null
            android.util.Log.d("EditorFragment", "Cleared TextMateColorScheme cache")
        }

        // Get or create language instance
        fun getOrCreateLanguage(scopeName: String): io.github.rosemoe.sora.langs.textmate.TextMateLanguage {
            return languageCache.getOrPut(scopeName) {
                android.util.Log.d("EditorFragment", "Creating new TextMateLanguage instance: $scopeName")
                io.github.rosemoe.sora.langs.textmate.TextMateLanguage.create(scopeName, true)
            }
        }

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