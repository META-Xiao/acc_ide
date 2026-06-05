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
import com.acc_ide.ui.welcome.WelcomeFragment
import com.acc_ide.util.*
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
            // Initialize managers
            fileRepository = FileRepository(this)
            permissionManager = PermissionManager(this)
            themeManager = ThemeManager(this)

            // Apply theme and language settings
            themeManager.applyThemeSettings()
            themeManager.applyCodeEditorTheme()
            themeManager.applyLanguage()

            super.onCreate(savedInstanceState)
            
            setContentView(R.layout.activity_main)
            
            // Enable system window insets handling
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
            
            // Set up toolbar
            val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
            
            // Handle IME insets for content frame
            val contentFrame = findViewById<android.widget.FrameLayout>(R.id.content_frame)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentFrame) { v, insets ->
                val imeHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                v.setPadding(0, 0, 0, imeHeight)
                insets
            }

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

            // Set up shell button
            val shellItem = findViewById<LinearLayout>(R.id.shell_item)
            shellItem.setOnClickListener {
                fragmentNavigationManager.showShellFragment()
            }

            // Listen to fragment changes
            setupFragmentLifecycleCallbacks()

            // Request permissions and initialize system
            requestStoragePermission()

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
} 