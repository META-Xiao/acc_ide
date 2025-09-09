package com.acc_ide.ui.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.acc_ide.R
import com.acc_ide.data.repository.FileRepository
import com.acc_ide.ui.editor.EditorFragment
import com.acc_ide.ui.settings.SettingsFragment
// import com.acc_ide.ui.terminal // 已移除，使用termux库.TerminalActivity
import com.acc_ide.ui.welcome.WelcomeFragment
import com.acc_ide.util.*
import com.acc_ide.compiler.CompileRunManager
import com.acc_ide.compiler.Language
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main activity class - manages main interface, drawer, file operations and core functionality
 * 主活动类 - 负责管理应用程序的主界面、侧边栏、文件操作等核心功能
 */
class MainActivity : AppCompatActivity() {
    // UI components
    private lateinit var drawerLayout: DrawerLayout
    lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

    // Managers
    private lateinit var fileRepository: FileRepository
    private lateinit var permissionManager: PermissionManager
    private lateinit var uiManager: UIManager
    private lateinit var fragmentNavigationManager: FragmentNavigationManager
    private lateinit var themeManager: ThemeManager
    private lateinit var compileRunManager: CompileRunManager

    // Register file save result handler
    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            if (uri != null && fileToSave.isNotEmpty()) {
                handleSaveFile(fileToSave, uri)
                fileToSave = "" // Reset
            }
        }
    }

    // Store current file name to save
    private var fileToSave: String = ""

    // For backward compatibility, keep these properties
    val files: MutableMap<String, String>
        get() = fileRepository.files
    var currentFileName: String
        get() = fileRepository.currentFileName
        set(value) { fileRepository.currentFileName = value }

    /**
     * Activity initialization when created
     * Activity创建时的初始化
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            // Initialize Environment first
            Environment.initialize(this)
            
            // Initialize managers
            fileRepository = FileRepository(this)
            permissionManager = PermissionManager(this)
            themeManager = ThemeManager(this)
            compileRunManager = CompileRunManager(this)

            // Apply theme and language settings
            themeManager.applyThemeSettings()
            themeManager.applyCodeEditorTheme()
            themeManager.applyLanguage()

            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            // Set up toolbar
            val toolbar: Toolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)

            // Initialize drawer layout
            drawerLayout = findViewById(R.id.drawer_layout)

            // Set up ActionBarDrawerToggle
            setupNavigationDrawer(toolbar)

            // Initialize fragment navigation manager
            fragmentNavigationManager = FragmentNavigationManager(this, drawerLayout, actionBarDrawerToggle)

            // Initialize UI manager
            uiManager = UIManager(this, fileRepository)
            setupUICallbacks()
            uiManager.setupUI()

            // Set up settings button
            val settingsItem = findViewById<LinearLayout>(R.id.settings_item)
            settingsItem.setOnClickListener {
                fragmentNavigationManager.showSettingsFragment()
            }

            // Set up terminal button
            val terminalItem = findViewById<LinearLayout>(R.id.terminal_item)
            terminalItem.setOnClickListener {
                openTerminal()
            }

            // Listen to fragment changes
            setupFragmentLifecycleCallbacks()

            // Request permissions and initialize system
            requestStoragePermission()
            
            // Initialize compilation manager
            initializeCompileRunManager()

            // Handle state restoration or initialization
            handleStateRestoration(savedInstanceState)

        } catch (e: Exception) {
            // Catch any unhandled exceptions to ensure app doesn't crash
            Log.e("MainActivity", "Unhandled exception in onCreate", e)

            // Show error dialog
            AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(getString(R.string.error_message, e.message))
                .setPositiveButton(R.string.ok_button, null)
                .show()
        }
    }

    /**
     * Set up UI callbacks for file operations
     * 设置UI回调用于文件操作
     */
    private fun setupUICallbacks() {
        uiManager.onFileOpened = { fileName ->
            openFile(fileName)
        }
        
        uiManager.onFileCreated = { fileName, language ->
            fragmentNavigationManager.showEditorFragment(fileName, language)
            uiManager.setSelectedFile(fileName)
            fragmentNavigationManager.closeDrawer()
        }
        
        uiManager.onFileImported = { uri ->
            uiManager.handleImportedFile(uri)
        }
        
        uiManager.onFileSaved = { fileName ->
            handleFileSave(fileName)
        }
        
        uiManager.onFileSaveAs = { fileName ->
            saveFileToExternal(fileName)
        }
    }

    /**
     * Set up fragment lifecycle callbacks
     * 设置Fragment生命周期回调
     */
    private fun setupFragmentLifecycleCallbacks() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(object :
            FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                // Update navigation icon when any fragment is resumed
                fragmentNavigationManager.updateNavigationIcon()

                // Set title if it's settings page
                if (f is SettingsFragment) {
                    supportActionBar?.title = getString(R.string.settings)
                }
            }
        }, true)

        // Listen to back stack changes
        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            Log.d("MainActivity", "Back stack changed, current fragment: ${currentFragment?.javaClass?.simpleName}")

            // Update navigation icon and title
            fragmentNavigationManager.updateNavigationIcon()

            // If returning to editor page, ensure content and syntax highlighting refresh
            if (currentFragment is EditorFragment && currentFileName.isNotEmpty()) {
                refreshEditorAfterReturn(currentFragment)
            }
        }
    }

    /**
     * Refresh editor after returning from settings page
     * 从设置页面返回后刷新编辑器
     */
    private fun refreshEditorAfterReturn(editorFragment: EditorFragment) {
        // Use lifecycle-aware coroutine scope to avoid race conditions
        lifecycleScope.launch {
            try {
                Log.d("MainActivity", "Refreshing editor after returning from settings: $currentFileName")

                // Immediate refresh
                editorFragment.refreshEditorTheme()

                // Allow time for UI to settle before applying language support
                delay(200)
                
                // Re-apply TextMate syntax highlighting
                val language = editorFragment.getLanguageForFile(currentFileName)
                editorFragment.setupLanguageSupport(language)
                editorFragment.refreshEditorTheme()

                Log.d("MainActivity", "Editor refresh completed using language: $language")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to refresh editor after returning from settings: ${e.message}")
            }
        }
    }

    /**
     * Request storage permission
     * 请求存储权限
     */
    private fun requestStoragePermission() {
        permissionManager.requestStoragePermission(object : PermissionManager.StoragePermissionCallback {
            override fun onPermissionGranted() {
                initializeFileSystem()
            }

            override fun onPermissionDenied() {
                // Even if permission is denied, initialize file system for app-specific directory
                Toast.makeText(this@MainActivity, R.string.storage_permission_denied, Toast.LENGTH_LONG).show()
                initializeFileSystem()
            }
        })
    }

    /**
     * Initialize file system
     * 初始化文件系统
     */
    private fun initializeFileSystem() {
        fileRepository.initialize()
        fileRepository.loadFileList()
        Log.d("MainActivity", "File system initialization completed")
    }

    /**
     * Initialize compile run manager
     * 初始化编译运行管理器
     */
    private fun initializeCompileRunManager() {
        lifecycleScope.launch {
            try {
                val success = compileRunManager.initialize()
                if (success) {
                    Log.d("MainActivity", "CompileRunManager initialized successfully")
                } else {
                    Log.w("MainActivity", "Failed to initialize CompileRunManager")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing CompileRunManager", e)
            }
        }
    }
    
    /**
     * Handle state restoration or initialization
     * 处理状态恢复或初始化
     */
    private fun handleStateRestoration(savedInstanceState: Bundle?) {
        val savedState = intent.getBundleExtra("savedState")
        if (savedState != null) {
            handleSavedState(savedState)
        } else {
            handleInitialState()
        }
    }

    /**
     * Handle saved state restoration
     * 处理保存的状态
     */
    private fun handleSavedState(savedState: Bundle) {
        val currentFragment = savedState.getString("currentFragment")
        val needsBackButton = savedState.getBoolean("needsBackButton", false)
        currentFileName = savedState.getString("currentFileName") ?: ""

        Log.d("MainActivity", "Restoring state: currentFragment=$currentFragment, needsBackButton=$needsBackButton")

        if (needsBackButton) {
            fragmentNavigationManager.forceShowBackButton()
            supportActionBar?.title = getString(R.string.settings)
            fragmentNavigationManager.showSettingsFragment()
        } else {
            if (currentFileName.isNotEmpty()) {
                openFile(currentFileName)
            } else {
                fragmentNavigationManager.showWelcomeFragment()
            }
        }
    }

    /**
     * Handle initial state when app starts
     * 处理初始状态
     */
    private fun handleInitialState() {
        val openedFiles = fileRepository.getOpenedFiles()
        if (openedFiles.isNotEmpty()) {
            val lastOpenedFile = openedFiles.last()
            openFile(lastOpenedFile)
            Log.d("MainActivity", "Opening last file on startup: $lastOpenedFile")
        } else {
            fragmentNavigationManager.showWelcomeFragment()
            Log.d("MainActivity", "No opened files, showing welcome page")
        }
    }

    // Refresh files when app resumes
    override fun onResume() {
        super.onResume()
        try {
            // Use theme manager to handle theme refresh
            themeManager.onResume()

            // If file repository is initialized, refresh file list
            if (::fileRepository.isInitialized) {
                // Update UI
                uiManager.updateFileList()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to refresh files on app resume", e)
        }
    }

    // Set up navigation drawer
    private fun setupNavigationDrawer(toolbar: androidx.appcompat.widget.Toolbar) {
        actionBarDrawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()
    }

    // Disable drawer toggle - delegate to fragment navigation manager
    fun disableDrawerToggle() {
        fragmentNavigationManager.disableDrawerToggle()
    }

    // Reset navigation drawer - delegate to fragment navigation manager
    fun resetNavigationDrawer() {
        fragmentNavigationManager.resetNavigationDrawer()
    }

    // Force show back button - delegate to fragment navigation manager
    fun forceShowBackButton() {
        fragmentNavigationManager.forceShowBackButton()
    }

    // Handle back button click
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Let ActionBarDrawerToggle handle click event first
        if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }

        // Handle other menu items
        when (item.itemId) {
            android.R.id.home -> {
                // If drawer is unavailable (e.g. in settings page), press back key
                if (!fragmentNavigationManager.isDrawerToggleEnabled()) {
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
                return false
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    // Handle system back button
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Get current fragment
        val currentFragment = fragmentNavigationManager.getCurrentFragment()

        // If current is welcome page and has opened files, open last file
        if (currentFragment is WelcomeFragment && fileRepository.getOpenedFiles().isNotEmpty()) {
            val lastOpenedFile = fileRepository.getOpenedFiles().last()
            openFile(lastOpenedFile)
            return
        }

        // Let fragment navigation manager handle back
        if (fragmentNavigationManager.handleBackPressed()) {
            return
        }

        // If returning from settings page, handle logic
        if (currentFragment is SettingsFragment) {
            if (files.isEmpty()) {
                fragmentNavigationManager.showWelcomeFragment()
                currentFileName = ""
            } else {
                if (currentFileName.isEmpty() || !files.containsKey(currentFileName)) {
                    currentFileName = files.keys.first()
                }
                val language = getFileLanguage(currentFileName)
                fragmentNavigationManager.showEditorFragment(currentFileName, language)
            }
            return
        }

        // Default behavior
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // Apply custom language setting
    override fun attachBaseContext(newBase: Context) {
        val savedLanguage = LocaleHelper.getLanguage(newBase)
        if (savedLanguage.isEmpty()) {
            // If no saved language, use system default language
            super.attachBaseContext(newBase)
        } else {
            // Otherwise use saved language
            super.attachBaseContext(LocaleHelper.setLocale(newBase, savedLanguage))
        }
    }

    /**
     * Open file and display in editor
     * 打开文件
     */
    private fun openFile(fileName: String) {
        try {
            if (fileRepository.openFile(fileName)) {
                val language = getFileLanguage(fileName)
                fragmentNavigationManager.showEditorFragment(fileName, language)

                // Update selected state in UI
                uiManager.setSelectedFile(fileName)

                // Close drawer
                fragmentNavigationManager.closeDrawer()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error opening file: $fileName", e)
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Create new file with selected language
     * 使用选择的语言创建新文件
     */
    fun createNewFile(language: String) {
        // Use lifecycle-aware coroutine scope to prevent memory leaks
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Create file in background thread
                val newFileName = fileRepository.createNewFile(language)
                
                // Switch to main thread for UI updates
                withContext(Dispatchers.Main) {
                    if (newFileName != null) {
                        // Show editor
                        fragmentNavigationManager.showEditorFragment(newFileName, language)

                        // Update file list and selection
                        uiManager.updateFileList()
                        uiManager.setSelectedFile(newFileName)

                        // Close drawer
                        fragmentNavigationManager.closeDrawer()

                        // Show creation success
                        Toast.makeText(this@MainActivity, "File $newFileName created successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Handle error on main thread
                withContext(Dispatchers.Main) {
                    Log.e("MainActivity", "Failed to create file: ${e.message}", e)
                    Toast.makeText(this@MainActivity, "Failed to create file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveCurrentFile() {
        if (currentFileName.isNotEmpty() && files.containsKey(currentFileName)) {
            val content = files[currentFileName] ?: ""
            fileRepository.updateFileContent(currentFileName, content)
            Log.d("MainActivity", "Saved current file: $currentFileName")
        }
    }

    // Save all files when activity pauses
    override fun onPause() {
        super.onPause()
        saveCurrentFile()
    }

    // Save file when editor content is updated
    fun updateFileContent(fileName: String, content: String) {
        fileRepository.updateFileContent(fileName, content)
    }

    private fun getFileLanguage(fileName: String): String {
        return when {
            fileName.endsWith(".java") -> "java"
            fileName.endsWith(".kt") -> "kotlin"
            fileName.endsWith(".py") -> "python"
            fileName.endsWith(".cpp") || fileName.endsWith(".c") -> "cpp"
            fileName.endsWith(".js") -> "javascript"
            fileName.endsWith(".html") -> "html"
            fileName.endsWith(".css") -> "css"
            fileName.endsWith(".xml") -> "xml"
            fileName.endsWith(".json") -> "json"
            fileName.endsWith(".md") -> "markdown"
            fileName.endsWith(".txt") -> "text"
            else -> "text"
        }
    }

    // Change language called by other fragments
    fun changeLanguage(languageCode: String) {
        themeManager.changeLanguage(languageCode)
        // Notify user language has changed
        Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
    }

    // Override onConfigurationChanged to handle language changes
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeManager.handleConfigurationChanged(newConfig)
        themeManager.updateResourcesAfterLanguageChange()
    }

    // Update navigation icon - delegate to fragment navigation manager
    fun updateNavigationIcon() {
        fragmentNavigationManager.updateNavigationIcon()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * Handle file save operation
     * 处理文件保存
     */
    private fun handleFileSave(fileName: String) {
        if (fileRepository.handleFileSave(fileName)) {
            Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show()
        } else {
            // Need to open file picker
            saveFileToExternal(fileName)
        }
    }

    /**
     * Save file to external storage
     * 保存文件到外部存储
     */
    private fun saveFileToExternal(fileName: String) {
        try {
            val mimeType = when {
                fileName.endsWith(".cpp") -> "text/x-c++src"
                fileName.endsWith(".py") -> "text/x-python"
                fileName.endsWith(".java") -> "text/x-java"
                else -> "text/plain"
            }

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, fileName)
            }

            fileToSave = fileName
            saveFileLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save file: ${e.message}", e)
            Toast.makeText(this, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handle file save result
     * 处理文件保存结果
     */
    private fun handleSaveFile(fileName: String, uri: Uri) {
        try {
            if (fileRepository.saveFileToUri(fileName, uri)) {
                // Update file list UI
                uiManager.updateFileList()
                Toast.makeText(this, "File saved to external", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to save file to URI: ${e.message}", e)
            Toast.makeText(this, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Update editor font size
     * 更新编辑器的字体大小
     */
    fun updateEditorFontSize(size: Float) {
        val editorFragment =
            supportFragmentManager.fragments.firstOrNull { it is EditorFragment } as? EditorFragment
        editorFragment?.updateFontSize(size)
    }

    /**
     * Update editor cursor width
     * 更新编辑器的光标宽度
     */
    fun updateEditorCursorWidth(width: Float) {
        val editorFragment =
            supportFragmentManager.fragments.firstOrNull { it is EditorFragment } as? EditorFragment
        editorFragment?.updateCursorWidth(width)
    }

    /**
     * Update auto completion component state
     * 更新自动补全组件的状态
     */
    fun updateAutoCompletionState(enabled: Boolean) {
        try {
            supportFragmentManager.fragments.forEach { fragment ->
                if (fragment is EditorFragment) {
                    fragment.setAutoCompletionEnabled(enabled)
                }
            }
            Log.d("MainActivity", "Auto completion state updated: $enabled")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to update auto completion state: ${e.message}")
        }
    }

    fun updateLanguageSupport() {
        try {
            supportFragmentManager.fragments.forEach { fragment ->
                if (fragment is EditorFragment) {
                    fragment.setupLanguageSupport()
                }
            }
            Log.d("MainActivity", "Language support updated")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to update language support: ${e.message}")
        }
    }

    /**
     * Refresh editor syntax highlighting
     * 刷新编辑器的语法高亮
     */
    fun refreshEditorSyntaxHighlighting() {
        themeManager.refreshEditorSyntaxHighlighting()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults)
    }

    /**
     * Handle fragment back pressed request to avoid stack overflow error
     * 处理从Fragment返回的请求 - 为避免栈溢出错误，由Fragment调用
     */
    fun onFragmentBackPressed() {
        // Find currently displayed fragment
        val currentFragment = fragmentNavigationManager.getCurrentFragment()

        // If currently showing settings fragment, return to previous fragment or welcome page
        if (currentFragment is SettingsFragment) {
            // Try to show editor page (if available)
            if (files.isNotEmpty()) {
                val firstFileName = files.keys.first()
                openFile(firstFileName)
            } else {
                // If no files, show welcome page
                fragmentNavigationManager.showWelcomeFragment()
            }

            // Update UI
            invalidateOptionsMenu()
            resetNavigationDrawer()
        } else {
            // Other types of fragments, call system back behavior
            finishAfterTransition()
        }
    }

    /**
     * Open terminal activity
     * 打开终端Activity
     */
    private fun openTerminal() {
        try {
            // 使用TerminalUtils启动终端
            com.acc_ide.util.TerminalUtils.startTerminal(this)
            
            // Close drawer
            fragmentNavigationManager.closeDrawer()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open terminal: ${e.message}", e)
            Toast.makeText(this, "Failed to open terminal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Compile and run current file
     * 编译并运行当前文件
     */
    fun compileAndRunCurrentFile() {
        if (currentFileName.isEmpty() || !files.containsKey(currentFileName)) {
            Toast.makeText(this, "No file is currently open", Toast.LENGTH_SHORT).show()
            return
        }
        
        val content = files[currentFileName] ?: ""
        compileAndRun(currentFileName, content)
    }
    
    /**
     * Compile and run specified file
     * 编译并运行指定文件
     */
    fun compileAndRun(fileName: String, content: String) {
        lifecycleScope.launch {
            try {
                // Show progress or compilation dialog
                val language = detectLanguageFromFileName(fileName)
                if (language == null) {
                    Toast.makeText(this@MainActivity, "Unsupported file type: $fileName", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Find current editor fragment to show compilation output
                val editorFragment = supportFragmentManager.fragments
                    .firstOrNull { it is EditorFragment } as? EditorFragment
                
                if (editorFragment != null) {
                    // Show compilation in IOPanel if available
                    showCompilationOutput("Starting compilation of $fileName...")
                }
                
                compileRunManager.compileAndRun(
                    fileName = fileName,
                    content = content,
                    callback = object : CompileRunManager.CompileRunCallback {
                        override fun onCompileStart(fileName: String, language: Language) {
                            runOnUiThread {
                                showCompilationOutput("Compiling ${language.name} file: $fileName")
                            }
                        }
                        
                        override fun onOutput(output: String) {
                            runOnUiThread {
                                showCompilationOutput(output)
                            }
                        }
                        
                        override fun onError(error: String) {
                            runOnUiThread {
                                showCompilationOutput("ERROR: $error")
                            }
                        }
                        
                        override fun onCompileComplete(success: Boolean, message: String) {
                            runOnUiThread {
                                showCompilationOutput(if (success) "✓ $message" else "✗ $message")
                                Toast.makeText(
                                    this@MainActivity, 
                                    if (success) "Compilation successful" else "Compilation failed", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during compilation", e)
                Toast.makeText(this@MainActivity, "Compilation error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Compile only (without running)
     * 仅编译（不运行）
     */
    fun compileOnly(fileName: String, content: String) {
        lifecycleScope.launch {
            try {
                val language = detectLanguageFromFileName(fileName)
                if (language == null) {
                    Toast.makeText(this@MainActivity, "Unsupported file type: $fileName", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                showCompilationOutput("Starting compilation of $fileName...")
                
                compileRunManager.compileOnly(
                    fileName = fileName,
                    content = content,
                    callback = object : CompileRunManager.CompileRunCallback {
                        override fun onCompileStart(fileName: String, language: Language) {
                            runOnUiThread {
                                showCompilationOutput("Compiling ${language.name} file: $fileName")
                            }
                        }
                        
                        override fun onOutput(output: String) {
                            runOnUiThread {
                                showCompilationOutput(output)
                            }
                        }
                        
                        override fun onError(error: String) {
                            runOnUiThread {
                                showCompilationOutput("ERROR: $error")
                            }
                        }
                        
                        override fun onCompileComplete(success: Boolean, message: String) {
                            runOnUiThread {
                                showCompilationOutput(if (success) "✓ $message" else "✗ $message")
                                Toast.makeText(
                                    this@MainActivity, 
                                    if (success) "Compilation successful" else "Compilation failed", 
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during compilation", e)
                Toast.makeText(this@MainActivity, "Compilation error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Show compilation output in IOPanel or log
     * 在IOPanel或日志中显示编译输出
     */
    private fun showCompilationOutput(output: String) {
        try {
            Log.d("Compilation", output)
            
            // Try to find IOPanelFragment and show output there
            val fragments = supportFragmentManager.fragments
            for (fragment in fragments) {
                if (fragment.javaClass.simpleName.contains("IOPanel")) {
                    // If IOPanelFragment exists, try to show output there
                    fragment.javaClass.getMethod("appendOutput", String::class.java)
                        .invoke(fragment, output + "\n")
                    return
                }
            }
            
            // Fallback: show in toast for important messages
            if (output.contains("ERROR") || output.contains("✓") || output.contains("✗")) {
                Toast.makeText(this, output, Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.d("Compilation", output) // Fallback to log only
        }
    }
    
    /**
     * Detect language from file name
     * 从文件名检测语言类型
     */
    private fun detectLanguageFromFileName(fileName: String): Language? {
        return when {
            fileName.endsWith(".c") -> Language.C
            fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx") -> Language.CPP
            fileName.endsWith(".java") -> Language.JAVA
            fileName.endsWith(".py") -> Language.PYTHON
            else -> null
        }
    }
    
    /**
     * Check if compiler is installed for current file type
     * 检查当前文件类型的编译器是否已安装
     */
    fun isCompilerInstalled(fileName: String): Boolean {
        val language = detectLanguageFromFileName(fileName) ?: return false
        return compileRunManager.isCompilerInstalled(language)
    }
    
    /**
     * Get compiler info for current file type
     * 获取当前文件类型的编译器信息
     */
    fun getCompilerInfo(fileName: String): String {
        val language = detectLanguageFromFileName(fileName) ?: return "Unknown file type"
        return compileRunManager.getCompilerInfo(language)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Clean up compile run manager
            if (::compileRunManager.isInitialized) {
                compileRunManager.cleanup()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during cleanup", e)
        }
    }
} 