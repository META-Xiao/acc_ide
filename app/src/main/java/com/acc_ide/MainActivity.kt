package com.acc_ide

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.acc_ide.adapter.FileListAdapter
import com.acc_ide.dialog.DeleteFileConfirmDialog
import com.acc_ide.dialog.RenameFileDialog
import com.acc_ide.model.CodeFile
import com.acc_ide.util.FileStorageManager
import com.acc_ide.util.LocaleHelper
import java.io.File
import java.util.Locale
import androidx.preference.PreferenceManager
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主活动类
 * 负责管理应用程序的主界面、侧边栏、文件操作等核心功能
 */
class MainActivity : AppCompatActivity() {
    // 界面组件
    private lateinit var drawerLayout: DrawerLayout
    lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
    private lateinit var fileListAdapter: FileListAdapter
    private lateinit var fileListRecyclerView: RecyclerView
    private var isDrawerToggleEnabled = true
    
    // 文件存储管理器
    private lateinit var fileStorageManager: FileStorageManager
    
    // 用于在内存中存储代码文件的Map
    val files = mutableMapOf<String, String>()
    var currentFileName: String = ""
    
    // 存储权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allGranted = false
                return@forEach
            }
        }
        
        if (allGranted) {
            // 权限已授予，初始化存储和加载文件
            initializeStorage()
        } else {
            // 权限被拒绝，显示提示
            showPermissionExplanationDialog()
        }
    }

    /**
     * 活动创建时的回调方法
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // 最开始就调用super.onCreate，避免在try/catch中多次调用
        super.onCreate(savedInstanceState)
        
        try {
        // 应用保存的主题设置
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val nightMode = prefs.getInt("app_night_mode", AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(nightMode)
            
        // 初始化文件存储管理器（即使没有权限也初始化，因为内部存储不需要特殊权限）
        fileStorageManager = FileStorageManager(this)
            
            // 打印外部存储目录路径，方便调试
            Log.i("MainActivity", "外部存储路径: ${getExternalFilesDir(null)?.absolutePath}")
            Log.i("MainActivity", "代码文件目录路径: ${fileStorageManager.getCodeFilesDir().absolutePath}")
            
            // 清理过期的删除标记
            cleanupExpiredDeletionMarkers()
            
            // 验证文件系统完整性，在应用启动时清理可能的问题
            verifyFileSystemIntegrity()
            
            // 检查文件存储权限
            checkStoragePermissions()
        
        // 加载已保存的语言设置
        applyLanguage()
        
            // 设置主界面布局
        setContentView(R.layout.activity_main)

            // 设置工具栏
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

            // 设置抽屉布局
        drawerLayout = findViewById(R.id.drawer_layout)

            // 安全地设置RecyclerView和适配器
            try {
        // 设置RecyclerView
        fileListRecyclerView = findViewById(R.id.file_list_recyclerview)
                if (fileListRecyclerView != null) {
        fileListRecyclerView.layoutManager = LinearLayoutManager(this)
        fileListAdapter = FileListAdapter(
            onFileClickListener = { fileName -> 
                openFile(fileName)
            },
            onRenameClickListener = { fileName ->
                showRenameFileDialog(fileName)
            },
            onDeleteClickListener = { fileName ->
                showDeleteFileDialog(fileName)
            }
        )
        fileListRecyclerView.adapter = fileListAdapter
                    Log.d("MainActivity", "成功初始化RecyclerView和FileListAdapter")
                } else {
                    Log.e("MainActivity", "找不到RecyclerView控件")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "初始化RecyclerView时出错", e)
            }
        
        // 设置新建文件按钮
        val newFileButton = findViewById<ImageButton>(R.id.new_file_button)
        newFileButton.setOnClickListener {
            showNewFileDialog()
        }
        
        // 设置设置按钮
        val settingsItem = findViewById<LinearLayout>(R.id.settings_item)
        settingsItem.setOnClickListener {
            // 打开设置界面
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, SettingsFragment())
                .addToBackStack(null)
                .commit()
            
            // 关闭侧边栏
            drawerLayout.closeDrawer(GravityCompat.START)
            
            // 锁定抽屉，防止在设置页面滑动打开
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        }

            // 设置ActionBarDrawerToggle
        setupNavigationDrawer(toolbar)
        
        // 监听Fragment变化，确保设置页面显示返回按钮
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                // 当任何Fragment恢复时，更新导航图标
                updateNavigationIcon()
                
                // 如果是设置页面，设置标题
                if (f is SettingsFragment) {
                    supportActionBar?.title = getString(R.string.settings)
                }
            }
        }, true)
        
        // 检查是否从语言切换回来
        val savedState = intent.getBundleExtra("savedState")
        if (savedState != null) {
            // 恢复之前保存的状态
            val currentFragment = savedState.getString("currentFragment")
            val needsBackButton = savedState.getBoolean("needsBackButton", false)
            currentFileName = savedState.getString("currentFileName") ?: ""
            
            Log.d("MainActivity", "恢复状态: currentFragment=$currentFragment, needsBackButton=$needsBackButton")
            
            // 如果需要显示返回按钮，立即设置
            if (needsBackButton) {
                // 锁定抽屉
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                isDrawerToggleEnabled = false
                
                // 强制显示返回按钮
                forceShowBackButton()
                
                // 设置标题
                supportActionBar?.title = getString(R.string.settings)
                
                    // 打开设置页面
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.content_frame, SettingsFragment())
                            .commit()
                        } else {
                    // 处理其他类型的Fragment恢复
                    if (!currentFileName.isEmpty()) {
                        // 如果有当前文件，打开它
                        loadFileList()
                        openFile(currentFileName)
                            } else {
                        // 否则加载欢迎页面
                        showWelcomeFragment()
                    }
                            }
        } else {
                // 初始状态，加载文件列表并显示欢迎页面
                loadFileList()
                showWelcomeFragment()
            }
        } catch (e: Exception) {
            // 捕获任何未处理的异常，确保应用不会崩溃
            Log.e("MainActivity", "onCreate发生未处理的异常", e)
            
            // 显示一个错误对话框
            AlertDialog.Builder(this)
                .setTitle(R.string.error_title)
                .setMessage(getString(R.string.error_message, e.message))
                .setPositiveButton(R.string.ok_button, null)
                .show()
        }
    }
    
    /**
     * 清理过期的删除标记
     * 在应用启动时运行，清理那些已不存在文件的删除标记
     */
    private fun cleanupExpiredDeletionMarkers() {
        try {
            Log.d("MainActivity", "开始清理过期的删除标记...")
            
            // 获取已删除文件的记录
            val prefs = getSharedPreferences("acc_ide_prefs", Context.MODE_PRIVATE)
            val deletedFiles = prefs.getStringSet("deleted_files", emptySet()) ?: emptySet()
            
            if (deletedFiles.isEmpty()) {
                Log.d("MainActivity", "没有删除标记，无需清理")
                return
            }
            
            // 获取实际文件列表
            val filesDir = fileStorageManager.getCodeFilesDir()
            val actualFileNames = filesDir.listFiles()
                ?.filter { !it.name.endsWith(".meta") }
                ?.map { it.name }
                ?.toSet() ?: emptySet()
                
            // 找出需要从删除标记中移除的项目（因为对应的文件已经不存在了）
            val toRemoveFromDeleted = deletedFiles.filter { !actualFileNames.contains(it) }.toSet()
            
            // 如果有需要清理的记录，更新SharedPreferences
            if (toRemoveFromDeleted.isNotEmpty()) {
                val newDeletedFiles = HashSet(deletedFiles)
                newDeletedFiles.removeAll(toRemoveFromDeleted)
                
                prefs.edit()
                    .putStringSet("deleted_files", newDeletedFiles)
                    .apply()
                    
                Log.d("MainActivity", "已清理 ${toRemoveFromDeleted.size} 个过期的删除标记")
            } else {
                Log.d("MainActivity", "没有过期的删除标记需要清理")
            }
            
            // 补充清理：检查是否有已删除标记但文件仍然存在的情况
            // 这种情况表明文件删除失败，但被标记为已删除
            val falseDeleted = deletedFiles.filter { actualFileNames.contains(it) }.toSet()
            
            if (falseDeleted.isNotEmpty()) {
                Log.w("MainActivity", "发现 ${falseDeleted.size} 个标记为已删除但实际存在的文件，将移除删除标记")
                
                val newDeletedFiles = HashSet(deletedFiles)
                newDeletedFiles.removeAll(falseDeleted)
                
                prefs.edit()
                    .putStringSet("deleted_files", newDeletedFiles)
                    .apply()
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "清理过期删除标记时出错", e)
        }
    }
    
    /**
     * 验证文件系统完整性，清理可能存在的问题
     */
    private fun verifyFileSystemIntegrity() {
        try {
            Log.d("MainActivity", "正在验证文件系统完整性...")
            
            // 清空内存中的文件列表，确保从干净状态开始
            files.clear()
            
            // 加载文件前，先进行一次刷新以确保文件系统信息是最新的
            fileStorageManager.refreshFilesFromDisk()
            
            // 检查被删除文件的记录
            val prefs = getSharedPreferences("acc_ide_prefs", Context.MODE_PRIVATE)
            val deletedFiles = prefs.getStringSet("deleted_files", emptySet()) ?: emptySet()
            
            // 清理可能已经不存在的文件的删除记录
            val filesDir = fileStorageManager.getCodeFilesDir()
            val actualFileNames = filesDir.listFiles()
                ?.filter { !it.name.endsWith(".meta") }
                ?.map { it.name }
                ?.toSet() ?: emptySet()
                
            // 找出需要从已删除列表中移除的文件（因为它们已经不存在了）
            val toRemoveFromDeleted = deletedFiles.filter { !actualFileNames.contains(it) }.toSet()
            
            // 如果有需要清理的记录，更新SharedPreferences
            if (toRemoveFromDeleted.isNotEmpty()) {
                val newDeletedFiles = HashSet(deletedFiles)
                newDeletedFiles.removeAll(toRemoveFromDeleted)
                
                prefs.edit()
                    .putStringSet("deleted_files", newDeletedFiles)
                    .apply()
                    
                Log.d("MainActivity", "已清理 ${toRemoveFromDeleted.size} 个不存在文件的删除记录")
            }
            
            // 从存储加载所有文件，这个过程会自动清理孤立文件
            val storedFiles = fileStorageManager.getAllFiles()
            Log.d("MainActivity", "文件系统验证完成，找到 ${storedFiles.size} 个有效文件")
            
            // 记录未删除的文件总数，用于验证
            var validFileCount = 0
            
            // 将文件加载到内存 - 只加载未被删除的文件
            for (file in storedFiles) {
                // 只加载未被标记为删除的文件
                if (!deletedFiles.contains(file.name)) {
                    files[file.name] = file.content
                    validFileCount++
                } else {
                    Log.d("MainActivity", "跳过已删除的文件: ${file.name}")
                }
            }
            
            Log.d("MainActivity", "加载了 $validFileCount 个有效文件，跳过 ${storedFiles.size - validFileCount} 个已删除文件")
        } catch (e: Exception) {
            Log.e("MainActivity", "文件系统验证失败", e)
        }
    }
    
    /**
     * 检查存储权限
     */
    private fun checkStoragePermissions() {
        if (!hasStoragePermissions()) {
            requestStoragePermissions()
        } else {
            // 已有权限，初始化存储
            initializeStorage()
        }
    }
    
    /**
     * 检查是否拥有存储权限
     */
    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) 使用更精细的媒体权限
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) 使用存储访问框架，不需要特殊权限
            true
        } else {
            // Android 10及以下版本需要存储权限
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 请求存储权限
     */
    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            )
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Android 10及以下版本
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        } else {
            // Android 11+ (API 30+) 使用存储访问框架，不需要特殊权限
            initializeStorage()
        }
    }
    
    /**
     * 显示权限解释对话框
     */
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_permission_title)
            .setMessage(R.string.storage_permission_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requestStoragePermissions()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }
    
    /**
     * 初始化存储
     */
    private fun initializeStorage() {
        // 确保fileStorageManager已初始化
        if (::fileStorageManager.isInitialized) {
            try {
                // 清理可能损坏的文件系统状态
                verifyFileSystemIntegrity()
                
                // 从存储加载所有有效的文件
        loadFilesFromStorage()
                
                // 更新UI (如果已初始化)
                if (::fileListAdapter.isInitialized) {
                    updateFileList()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "初始化存储时出错", e)
                Toast.makeText(this, "初始化存储时出错: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("MainActivity", "fileStorageManager未初始化，跳过加载文件")
        }
    }
    
    /**
     * 从存储加载所有文件
     */
    private fun loadFilesFromStorage() {
        try {
            Log.d("MainActivity", "开始从存储加载文件...")
            
        // 清空内存中的文件列表
        files.clear()
            
            // 先强制刷新文件系统
            try {
                fileStorageManager.refreshFilesFromDisk()
            } catch (e: Exception) {
                Log.e("MainActivity", "刷新文件系统时出错", e)
            }
            
            // 获取已删除文件的记录
            val prefs = getSharedPreferences("acc_ide_prefs", Context.MODE_PRIVATE)
            val deletedFiles = prefs.getStringSet("deleted_files", emptySet()) ?: emptySet()
            
            // 检查被删除文件列表是否存在已删除的文件
            val filesDir = fileStorageManager.getCodeFilesDir()
            val actualFileNames = filesDir.listFiles()
                ?.filter { !it.name.endsWith(".meta") }
                ?.map { it.name }
                ?.toSet() ?: emptySet()
                
            // 找出需要从已删除列表中移除的文件（因为它们已经不存在了）
            val toRemoveFromDeleted = deletedFiles.filter { !actualFileNames.contains(it) }.toSet()
            
            // 如果有需要清理的记录，更新SharedPreferences
            if (toRemoveFromDeleted.isNotEmpty()) {
                val newDeletedFiles = HashSet(deletedFiles)
                newDeletedFiles.removeAll(toRemoveFromDeleted)
                
                prefs.edit()
                    .putStringSet("deleted_files", newDeletedFiles)
                    .apply()
                    
                Log.d("MainActivity", "已清理 ${toRemoveFromDeleted.size} 个不存在文件的删除记录")
            }
        
        // 从存储加载所有文件
        val storedFiles = fileStorageManager.getAllFiles()
            Log.d("MainActivity", "存储中找到 ${storedFiles.size} 个文件")
        
        if (storedFiles.isNotEmpty()) {
                // 计数器，用于记录有效文件数
                var validFileCount = 0
                
            // 将文件加载到内存
            for (file in storedFiles) {
                    // 跳过已标记为删除的文件
                    if (deletedFiles.contains(file.name)) {
                        Log.d("MainActivity", "跳过已删除的文件: ${file.name}")
                        continue
                    }
                    
                    // 额外的有效性检查
                    if (file.name.isNotBlank()) {
                files[file.name] = file.content
                        validFileCount++
                        Log.d("MainActivity", "已加载文件: ${file.name}, 内容长度: ${file.content.length}")
                    } else {
                        Log.w("MainActivity", "跳过无效文件: ${file.name}")
                    }
            }
            
                Log.d("MainActivity", "从存储加载了 ${storedFiles.size} 个文件，有效文件: $validFileCount 个")
        } else {
            Log.d("MainActivity", "存储中没有文件")
            }
            
            // 更新文件列表UI (只有在适配器已初始化的情况下)
            if (::fileListAdapter.isInitialized) {
                updateFileList()
            } else {
                Log.d("MainActivity", "fileListAdapter尚未初始化，跳过更新UI")
            }
            
            // 输出当前文件列表状态
            Log.d("MainActivity", "当前文件列表: ${files.keys.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e("MainActivity", "加载文件时出错", e)
        }
    }
    
    // 应用恢复时刷新文件
    override fun onResume() {
        super.onResume()
        try {
            // 如果fileStorageManager已初始化，则刷新文件列表
            if (::fileStorageManager.isInitialized) {
                // 只刷新文件信息，不完全重载
                fileStorageManager.refreshFilesFromDisk()
                
                // 更新UI
                if (::fileListAdapter.isInitialized) {
                    updateFileList()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "恢复应用时刷新文件失败", e)
        }
    }
    
    // 设置导航抽屉
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
        isDrawerToggleEnabled = true
    }
    
    // 禁用导航抽屉开关
    fun disableDrawerToggle() {
        // 禁用抽屉滑动
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        isDrawerToggleEnabled = false
        
        // 完全禁用ActionBarDrawerToggle
        actionBarDrawerToggle.isDrawerIndicatorEnabled = false
        actionBarDrawerToggle.setToolbarNavigationClickListener {
            onBackPressed()
        }
        
        // 直接设置返回箭头
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        
        Log.d("MainActivity", "已禁用导航抽屉并设置返回按钮")
    }
    
    // 重置导航抽屉
    fun resetNavigationDrawer() {
        updateNavigationIcon()
    }
    
    // 强制返回按钮显示（用于设置页面等）
    fun forceShowBackButton() {
        // 完全禁用ActionBarDrawerToggle
        actionBarDrawerToggle.isDrawerIndicatorEnabled = false
        actionBarDrawerToggle.setToolbarNavigationClickListener {
            onBackPressed()
        }
        
        // 直接设置返回箭头
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        Log.d("MainActivity", "已强制显示返回按钮")
    }
    
    // 处理返回按钮点击
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // 先让ActionBarDrawerToggle处理点击事件
        if (actionBarDrawerToggle.onOptionsItemSelected(item)) {
            return true
        }
        
        // 处理其他菜单项
        when (item.itemId) {
            android.R.id.home -> {
                // 如果抽屉不可用（例如在设置页面），则按下返回键
                if (!isDrawerToggleEnabled) {
                onBackPressed()
                return true
            }
                return false
        }
            else -> return super.onOptionsItemSelected(item)
        }
    }
    
    // 处理系统返回按钮
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 如果抽屉打开，先关闭抽屉
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        
        // 检查是否有IO面板打开，如果有，关闭它
        val ioPanel = supportFragmentManager.findFragmentByTag("io_panel")
        if (ioPanel != null) {
            supportFragmentManager.beginTransaction()
                .remove(ioPanel)
                .commit()
            supportFragmentManager.popBackStack()
            
            // 重置ActionBar和菜单状态
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)
            return
        }
        
        // 如果当前是设置页面，手动处理返回逻辑
        val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
        if (currentFragment is SettingsFragment) {
            // 恢复主页面
            if (files.isEmpty()) {
                // 如果没有文件，显示欢迎界面
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, WelcomeFragment())
                    .commit()
                currentFileName = ""
                supportActionBar?.title = getString(R.string.welcome)
            } else {
                // 如果有文件，显示第一个文件或当前文件
                if (currentFileName.isEmpty() || !files.containsKey(currentFileName)) {
                currentFileName = files.keys.first()
                }
                showEditorWithFile(currentFileName, getFileLanguage(currentFileName))
            }
            
            // 解锁抽屉
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            
            // 恢复菜单图标
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)
            return
        }
        
        // 如果有回退栈，弹出一个页面
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            // 延迟更新导航图标，确保Fragment转换完成
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)
            return
        }
        
        // 默认行为
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // 应用语言设置
    private fun applyLanguage() {
        val savedLanguage = LocaleHelper.getLanguage(this)
        // 使用已保存的语言，如果没有则使用系统默认语言
        if (savedLanguage.isEmpty()) {
            // 如果没有保存的语言设置，使用系统默认语言
            LocaleHelper.setLocale(this, Locale.getDefault().language)
        } else {
            // 否则使用保存的语言设置
            LocaleHelper.setLocale(this, savedLanguage)
        }
    }

    // 重写 attachBaseContext 方法以应用自定义的语言设置
    override fun attachBaseContext(newBase: Context) {
        val savedLanguage = LocaleHelper.getLanguage(newBase)
        val context = if (savedLanguage.isEmpty()) {
            // 如果没有保存的语言，使用系统默认语言
            super.attachBaseContext(newBase)
        } else {
            // 否则使用保存的语言
            super.attachBaseContext(LocaleHelper.setLocale(newBase, savedLanguage))
        }
    }

    /**
     * 更新文件列表
     */
    private fun updateFileList() {
        if (::fileListAdapter.isInitialized && ::fileListRecyclerView.isInitialized) {
            // 更新RecyclerView
            try {
                // 检查是否有未显示的文件
                checkForMissingFiles()
                
                // 获取排序后的文件列表
                val sortedFiles = files.keys.toList().sorted()
                
                // 更新适配器数据
                fileListAdapter.updateFileList(sortedFiles, currentFileName)
                
                Log.d("MainActivity", "已更新文件列表: ${sortedFiles.size} 个文件")
                
                // 更新空状态视图 - 避免使用ID直接查找，使用LayoutInflater动态创建
                if (sortedFiles.isEmpty()) {
                    // 如果没有文件，显示空文件提示
                    if (fileListRecyclerView.visibility == View.VISIBLE) {
                        fileListRecyclerView.visibility = View.GONE
                        
                        // 查找空视图的父布局
                        val navView = findViewById<View>(R.id.nav_view)
                        if (navView is ViewGroup) {
                            // 尝试找到已存在的空视图
                            val emptyViewId = resources.getIdentifier("empty_files_view", "id", packageName)
                            val existingEmptyView = if (emptyViewId != 0) findViewById<View>(emptyViewId) else null
                            
                            if (existingEmptyView != null) {
                                existingEmptyView.visibility = View.VISIBLE
                            }
                        }
                    }
                } else {
                    // 如果有文件，显示文件列表
                    if (fileListRecyclerView.visibility != View.VISIBLE) {
                        fileListRecyclerView.visibility = View.VISIBLE
                        
                        // 隐藏空视图
                        val emptyViewId = resources.getIdentifier("empty_files_view", "id", packageName)
                        if (emptyViewId != 0) {
                            val emptyView = findViewById<View>(emptyViewId)
                            emptyView?.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "更新文件列表失败", e)
            }
        } else {
            Log.d("MainActivity", "fileListAdapter或fileListRecyclerView未初始化，跳过更新UI")
        }
    }
    
    /**
     * 检查是否有未显示的文件，确保文件树和文件系统同步
     */
    private fun checkForMissingFiles() {
        try {
            val existingFiles = HashSet(files.keys)
            
            // 从文件系统中加载文件
            val storedFiles = fileStorageManager.getAllFiles()
            
            // 检查已删除文件的记录
            val prefs = getSharedPreferences("acc_ide_prefs", Context.MODE_PRIVATE)
            val deletedFiles = prefs.getStringSet("deleted_files", emptySet()) ?: emptySet()
            
            // 找出系统中存在但内存中不存在的文件
            for (file in storedFiles) {
                // 跳过已标记为删除的文件
                if (deletedFiles.contains(file.name)) {
                    Log.d("MainActivity", "checkForMissingFiles: 跳过已删除的文件: ${file.name}")
                    continue
                }
                
                if (!existingFiles.contains(file.name)) {
                    Log.d("MainActivity", "发现未加载的文件: ${file.name}")
                    files[file.name] = file.content
                }
            }
            
            // 找出内存中存在但系统中不存在的文件（不主动删除文件）
            val storedFileNames = storedFiles.map { it.name }.toSet()
            val invalidFiles = mutableListOf<String>()
            
            for (fileName in existingFiles) {
                // 如果内存中的文件在文件系统中不存在，且不在已删除列表中
                if (!storedFileNames.contains(fileName) && !deletedFiles.contains(fileName)) {
                    Log.d("MainActivity", "发现内存中的孤立文件: $fileName")
                    invalidFiles.add(fileName)
                }
            }
            
            // 从内存中移除不存在的文件
            for (fileName in invalidFiles) {
                files.remove(fileName)
                Log.d("MainActivity", "从内存中移除不存在的文件: $fileName")
            }
            
            if (files.isEmpty() && currentFileName.isNotEmpty()) {
                // 如果没有文件了，但当前还有选中的文件，清空选择并显示欢迎界面
                currentFileName = ""
                supportFragmentManager.beginTransaction()
                    .replace(R.id.content_frame, WelcomeFragment())
                    .commit()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "检查未显示文件时出错", e)
        }
    }
    
    /**
     * 显示新建文件对话框
     */
    private fun showNewFileDialog() {
        NewFileDialogFragment().show(supportFragmentManager, "new_file_dialog")
    }
    
    /**
     * 显示重命名文件对话框
     */
    private fun showRenameFileDialog(fileName: String) {
        RenameFileDialog().setUp(fileName) { oldName, newName ->
            // 检查新文件名是否已存在
            if (files.containsKey(newName)) {
                Toast.makeText(this, getString(R.string.file_exists, newName), Toast.LENGTH_SHORT).show()
            } else {
                // 获取原始内容
                val content = files[oldName] ?: ""
                
                // 重命名存储中的文件
                val language = getFileLanguage(oldName)
                fileStorageManager.renameFile(oldName, newName)
                
                // 更新内存中的文件
                files.remove(oldName)
                files[newName] = content
                
                // 如果当前正在编辑的是被重命名的文件，更新当前文件名
                if (currentFileName == oldName) {
                    currentFileName = newName
                    supportActionBar?.title = newName
                    
                    // 重新加载编辑器
                    showEditorWithFile(newName, language)
                }
                
                // 更新文件列表
                updateFileList()
                Toast.makeText(this, getString(R.string.file_renamed, oldName, newName), Toast.LENGTH_SHORT).show()
            }
        }.show(supportFragmentManager, "RenameFileDialog")
    }
    
    /**
     * 显示删除文件确认对话框
     */
    private fun showDeleteFileDialog(fileName: String) {
        DeleteFileConfirmDialog().setUp(fileName) { fileToDelete ->
            deleteFileFromList(fileToDelete)
        }.show(supportFragmentManager, "DeleteFileConfirmDialog")
    }

    /**
     * 打开文件
     */
    private fun openFile(fileName: String) {
        try {
        if (currentFileName != fileName) {
            currentFileName = fileName
            showEditorWithFile(fileName, getFileLanguage(fileName))
            
                // 更新RecyclerView中的选中状态 (只有在适配器已初始化的情况下)
                if (::fileListAdapter.isInitialized) {
            fileListAdapter.setSelectedFile(fileName)
                }
        }
        
        // 关闭侧边栏
            if (::drawerLayout.isInitialized) {
        drawerLayout.closeDrawer(GravityCompat.START)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "打开文件时出错: $fileName", e)
            Toast.makeText(this, "打开文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Function to create a new file with the selected language
    fun createNewFile(language: String) {
        try {
            val fileExtension = when (language) {
                "cpp" -> ".cpp"
                "python" -> ".py"
                "java" -> ".java"
                else -> ".txt"
            }
            
            val initialContent = when (language) {
                "cpp" -> "// Write your code here\nint main() {\n\n}"
                "python" -> "# Write your Python code here"
                "java" -> "// Write your Java code here\npublic class Main {\n  public static void main(String[] args) {\n\n  }\n}"
                else -> "// New file content"
            }
            
            // 清理状态：如果在welcome页面创建文件，确保文件列表是干净的
            if (currentFileName.isEmpty() && supportFragmentManager.findFragmentById(R.id.content_frame) is WelcomeFragment) {
                if (files.isNotEmpty()) {
                    Log.d("MainActivity", "检测到异常状态：在欢迎页面但文件列表不为空，正在清理...")
                    files.clear()
                }
            }
            
            // 计算文件索引并确保唯一性
            var newFileIndex = 1
            var newFileName = "NewFile_$newFileIndex$fileExtension"
            
            // 确保文件名唯一
            while (files.containsKey(newFileName)) {
                newFileIndex++
                newFileName = "NewFile_$newFileIndex$fileExtension"
            }
            
            // 创建代码文件对象
            val codeFile = CodeFile(
                name = newFileName,
                content = initialContent,
                language = language
            )
            
            // 保存到存储
            fileStorageManager.saveFile(codeFile)
            
            // 添加文件到内存集合
            files[newFileName] = initialContent
            currentFileName = newFileName
            
            // 更新文件列表
            updateFileList()
            
            // 显示编辑器
            showEditorWithFile(newFileName, language)
            
            // 提示创建成功
            Toast.makeText(this, "文件 $newFileName 创建成功", Toast.LENGTH_SHORT).show()
            
            // 调试日志
            Log.d("MainActivity", "创建新文件: $newFileName, 当前文件数: ${files.size}, 文件列表: ${files.keys}")
        } catch (e: Exception) {
            // 处理异常
            Log.e("MainActivity", "创建文件失败: ${e.message}", e)
            Toast.makeText(this, "创建文件失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveCurrentFile() {
        if (currentFileName.isNotEmpty() && files.containsKey(currentFileName)) {
            val content = files[currentFileName] ?: ""
            val language = getFileLanguage(currentFileName)
            
            // 创建代码文件对象
            val codeFile = CodeFile(
                name = currentFileName,
                content = content,
                language = language
            )
            
            // 保存到存储
            fileStorageManager.saveFile(codeFile)
            
            Log.d("MainActivity", "已保存当前文件: $currentFileName")
        }
    }
    
    // 在Activity暂停时保存所有文件
    override fun onPause() {
        super.onPause()
        saveCurrentFile()
    }
    
    // 编辑器内容已更新时保存文件
    fun updateFileContent(fileName: String, content: String) {
        if (files.containsKey(fileName)) {
            // 更新内存中的内容
            files[fileName] = content
            
            // 创建代码文件对象
            val codeFile = CodeFile(
                name = fileName,
                content = content,
                language = getFileLanguage(fileName)
            )
            
            // 保存到存储
            val success = fileStorageManager.saveFile(codeFile)
            
            // 确保文件不在删除标记列表中
            try {
                val prefs = getSharedPreferences("acc_ide_prefs", Context.MODE_PRIVATE)
                val deletedFiles = prefs.getStringSet("deleted_files", emptySet()) ?: emptySet()
                
                // 如果文件在删除标记列表中，将其移除
                if (deletedFiles.contains(fileName)) {
                    val newDeletedFiles = HashSet(deletedFiles)
                    newDeletedFiles.remove(fileName)
                    
                    prefs.edit()
                        .putStringSet("deleted_files", newDeletedFiles)
                        .apply()
                        
                    Log.d("MainActivity", "已从删除标记中移除文件: $fileName")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "清理删除标记时出错", e)
            }
            
            Log.d("MainActivity", "已更新文件内容: $fileName, 保存状态: $success")
        }
    }
    
    private fun showEditorWithFile(fileName: String, language: String) {
        try {
            // Update the toolbar title
            supportActionBar?.title = fileName
            
            // Show the editor fragment
            val editorFragment = EditorFragment.newInstance(language, fileName)
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, editorFragment)
                .commit()
                
            // 确保导航抽屉图标在编辑器页面正确显示
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)
                
            Log.d("MainActivity", "显示编辑器: 文件=${fileName}, 语言=${language}")
        } catch (e: Exception) {
            // 处理异常
            Log.e("MainActivity", "显示编辑器失败: ${e.message}", e)
            Toast.makeText(this, "显示编辑器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getFileLanguage(fileName: String): String {
        return when {
            fileName.endsWith(".cpp") -> "cpp"
            fileName.endsWith(".py") -> "python"
            fileName.endsWith(".java") -> "java"
            else -> "text"
        }
    }
    
    // 用于其他Fragment调用来切换语言
    fun changeLanguage(languageCode: String) {
        // 应用新语言设置
        val context = LocaleHelper.setLocale(this, languageCode)
        
        // 刷新界面资源
        resources.updateConfiguration(context.resources.configuration, context.resources.displayMetrics)
        
        // 重新创建整个Activity以确保所有UI元素都使用新语言
        recreateActivityForLanguageChange()
        
        // 通知用户语言已更改
        Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
    }
    
    // 重建Activity以应用语言变化
    private fun recreateActivityForLanguageChange() {
        // 保存当前状态
        val bundle = Bundle()
        
        // 保存当前Fragment类型
        val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
        when (currentFragment) {
            is SettingsFragment -> bundle.putString("currentFragment", "settings")
            is EditorFragment -> bundle.putString("currentFragment", "editor")
            is WelcomeFragment -> bundle.putString("currentFragment", "welcome")
        }
        
        // 保存其他需要的状态
        bundle.putString("currentFileName", currentFileName)
        bundle.putBoolean("needsBackButton", 
            currentFragment is SettingsFragment || 
            supportActionBar?.displayOptions?.and(androidx.appcompat.app.ActionBar.DISPLAY_HOME_AS_UP) != 0)
        
        // 在下一个Activity中恢复这些状态
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("savedState", bundle)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        // 立即启动新的Activity
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
    
    // 重写 onConfigurationChanged 以处理语言变化
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // 检查是否是夜间模式变化
        val nightModeFlags = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            // 处理深色模式特定逻辑
            Log.d("MainActivity", "应用切换到深色模式")
            // 刷新当前Fragment
            val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            if (currentFragment != null) {
                supportFragmentManager.beginTransaction()
                    .detach(currentFragment)
                    .attach(currentFragment)
                    .commit()
            }
        } else {
            // 处理浅色模式特定逻辑
            Log.d("MainActivity", "应用切换到浅色模式")
            // 刷新当前Fragment
            val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            if (currentFragment != null) {
                supportFragmentManager.beginTransaction()
                    .detach(currentFragment)
                    .attach(currentFragment)
                    .commit()
            }
        }
        
        // 更新资源和界面元素
        updateResourcesAfterLanguageChange()
    }
    
    // 更新界面资源和文本
    private fun updateResourcesAfterLanguageChange() {
        // 更新标题栏和可能受语言影响的其他UI元素
        supportActionBar?.title = if (currentFileName.isNotEmpty()) {
            currentFileName
        } else {
            getString(R.string.app_name)
        }
        
        // 刷新文件列表
        updateFileList()
        
        // 刷新当前Fragment的UI
        val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
        if (currentFragment is SettingsFragment) {
            supportActionBar?.title = getString(R.string.settings)
        }
        
        // 刷新导航抽屉标题
        val filesTextView = findViewById<android.widget.TextView>(R.id.files_text)
        filesTextView?.text = getString(R.string.files)
        
        val settingsTextView = findViewById<android.widget.TextView>(R.id.settings_text)
        settingsTextView?.text = getString(R.string.settings)
        
        val newFileButton = findViewById<ImageButton>(R.id.new_file_button)
        newFileButton.contentDescription = getString(R.string.new_file)
        
        // 更新导航图标
        updateNavigationIcon()
    }

    // 专门用于IO面板关闭后重置导航栏
    private fun resetNavigationDrawerAfterIOPanel() {
        // 延迟执行，确保Fragment状态已更新
        Handler(Looper.getMainLooper()).postDelayed({
            updateNavigationIcon()
        }, 100) // 延迟100毫秒执行，确保Fragment转换完成
    }

    /**
     * 更新导航栏状态
     * 根据当前Fragment类型设置导航栏图标：
     * - 设置页面: 显示返回键
     * - 其他所有页面: 显示菜单图标
     */
    fun updateNavigationIcon() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
        
        if (currentFragment is SettingsFragment) {
            // 设置页面: 显示返回键并锁定抽屉
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            actionBarDrawerToggle.isDrawerIndicatorEnabled = false
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            actionBarDrawerToggle.setToolbarNavigationClickListener {
                onBackPressed()
            }
            Log.d("MainActivity", "导航图标: 设置页面 - 返回键")
        } else {
            // 其他所有页面: 显示菜单图标并解锁抽屉
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            isDrawerToggleEnabled = true
            actionBarDrawerToggle.isDrawerIndicatorEnabled = true
            actionBarDrawerToggle.setToolbarNavigationClickListener(null)
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            actionBarDrawerToggle.syncState()
            Log.d("MainActivity", "导航图标: ${currentFragment?.javaClass?.simpleName ?: "未知"} - 菜单图标")
        }
    }

    /**
     * 从磁盘刷新文件列表
     * 用于手动刷新文件列表，特别是在文件删除后
     */
    private fun refreshFileListFromDisk() {
        try {
            Log.d("MainActivity", "开始从磁盘刷新文件列表...")
            
            // 强制刷新文件系统
            fileStorageManager.refreshFilesFromDisk()
            
            // 重新加载所有文件
            loadFilesFromStorage()
            
            // 更新UI
            updateFileList()
            
            // 显示刷新结果
            Toast.makeText(this, "文件列表已刷新，发现 ${files.size} 个文件", Toast.LENGTH_SHORT).show()
            Log.d("MainActivity", "文件列表刷新完成，当前文件数: ${files.size}, 文件列表: ${files.keys}")
        } catch (e: Exception) {
            Log.e("MainActivity", "刷新文件列表失败", e)
            Toast.makeText(this, "刷新文件列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    /**
     * 显示清除所有文件的确认对话框
     */
    private fun showClearAllFilesDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_all_files_title))
            .setMessage(getString(R.string.clear_all_files_message))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // 删除所有文件
                val success = fileStorageManager.deleteAllFiles()
                if (success) {
                    // 清空内存中的文件列表
                    files.clear()
                    
                    // 显示欢迎界面
                    currentFileName = ""
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.content_frame, WelcomeFragment())
                        .commit()
                    
                    // 更新标题
                    supportActionBar?.title = getString(R.string.app_name)
                    
                    // 更新文件列表
                    updateFileList()
                    
                    Toast.makeText(this, getString(R.string.clear_all_files_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.clear_all_files_failure), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 显示存储路径信息
     */
    private fun showStoragePath() {
        val externalPath = getExternalFilesDir(null)?.absolutePath ?: "未找到外部存储路径"
        val filesPath = fileStorageManager.getCodeFilesDir().absolutePath
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.storage_path_title))
            .setMessage(getString(R.string.storage_path_message, filesPath))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * 从文件列表中删除文件
     */
    private fun deleteFileFromList(fileName: String) {
        try {
            Log.d("MainActivity", "开始删除文件: $fileName")
            
            // 从文件存储中删除文件
            val success = fileStorageManager.deleteFile(fileName)
            
            if (success) {
                // 从内存中删除文件
                files.remove(fileName)
                
                // 更新RecyclerView
                updateFileList()
                
                // 将被删除的文件记录到SharedPreferences，确保在应用重启后也能记住被删除的文件
                val prefs = getSharedPreferences("acc_ide_prefs", Context.MODE_PRIVATE)
                val deletedFiles = prefs.getStringSet("deleted_files", HashSet<String>()) ?: HashSet()
                
                // 检查文件是否真的被删除了
                val fileStillExists = File(fileStorageManager.getCodeFilesDir(), fileName).exists()
                
                if (fileStillExists) {
                    Log.w("MainActivity", "文件删除失败，但UI已更新: $fileName")
                } else {
                    // 只有在文件真正被删除的情况下才记录删除状态
                    val newDeletedFiles = HashSet(deletedFiles)
                    newDeletedFiles.add(fileName)
                    
                    prefs.edit()
                        .putStringSet("deleted_files", newDeletedFiles)
                        .apply()
                    
                    Log.d("MainActivity", "已记录删除的文件: $fileName, 总共删除文件: ${newDeletedFiles.size}")
                }
                
                // 如果当前文件是被删除的文件，则显示其他文件或欢迎界面
                if (currentFileName == fileName) {
                    if (files.isEmpty()) {
                        // 显示欢迎界面
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.content_frame, WelcomeFragment())
                            .commit()
                        currentFileName = ""
                        supportActionBar?.title = getString(R.string.welcome)
                    } else {
                        // 显示第一个文件
                        currentFileName = files.keys.first()
                        showEditorWithFile(currentFileName, getFileLanguage(currentFileName))
                    }
                }
                
                Toast.makeText(this, getString(R.string.file_deleted, fileName), Toast.LENGTH_SHORT).show()
                
                // 日志输出当前状态
                Log.d("MainActivity", "删除文件后状态: 文件数量=${files.size}, 当前文件名=$currentFileName")
            } else {
                Log.e("MainActivity", "删除文件失败: $fileName")
                Toast.makeText(this, "删除文件失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "删除文件过程中发生异常", e)
            Toast.makeText(this, "删除文件过程中发生错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新编辑器字体大小
     * 
     * @param fontSize 新的字体大小
     */
    fun updateEditorFontSize(fontSize: Float) {
        try {
            // 查找当前活动的EditorFragment
            val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            if (currentFragment is EditorFragment) {
                // 更新编辑器字体大小
                currentFragment.updateFontSize(fontSize)
                android.util.Log.d("MainActivity", "已更新编辑器字体大小为 $fontSize")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "更新编辑器字体大小失败: ${e.message}")
        }
    }

    /**
     * 更新编辑器光标宽度
     * 
     * @param cursorWidth 新的光标宽度
     */
    fun updateEditorCursorWidth(cursorWidth: Float) {
        try {
            // 查找当前活动的EditorFragment
            val currentFragment = supportFragmentManager.findFragmentById(R.id.content_frame)
            if (currentFragment is EditorFragment) {
                // 更新编辑器光标宽度
                currentFragment.updateCursorWidth(cursorWidth)
                android.util.Log.d("MainActivity", "已更新编辑器光标宽度为 $cursorWidth")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "更新编辑器光标宽度失败: ${e.message}")
        }
    }

    /**
     * 加载文件列表，从存储中读取所有可用的代码文件
     */
    private fun loadFileList() {
        try {
            // 清空当前文件列表
            files.clear()
            
            // 获取所有代码文件
            val codeFiles = fileStorageManager.getAllCodeFiles()
            
            // 将文件添加到内存中
            for (codeFile in codeFiles) {
                files[codeFile.name] = codeFile.content
            }
            
            // 更新UI上的文件列表
            updateFileList()
            
            Log.d("MainActivity", "已加载${files.size}个文件")
        } catch (e: Exception) {
            Log.e("MainActivity", "加载文件列表失败: ${e.message}")
        }
    }

    /**
     * 显示欢迎页面
     */
    private fun showWelcomeFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, WelcomeFragment())
            .commit()
        supportActionBar?.title = getString(R.string.app_name)
    }
} 