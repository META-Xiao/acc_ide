package com.acc_ide.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.acc_ide.R
import com.acc_ide.ui.editor.EditorFragment
import com.acc_ide.ui.settings.SettingsFragment
import com.acc_ide.ui.shell.ShellFragment
import com.acc_ide.ui.welcome.WelcomeFragment

/**
 * Fragment navigation manager for handling navigation between fragments and ActionBar state management
 * Fragment导航管理器 - 负责处理Fragment之间的导航、ActionBar状态管理等
 */
class FragmentNavigationManager(
    private val activity: AppCompatActivity,
    private val drawerLayout: DrawerLayout,
    private val actionBarDrawerToggle: ActionBarDrawerToggle
) {
    
    private var isDrawerToggleEnabled = true
    
    /**
     * Show editor fragment with specified file and language
     * 显示编辑器Fragment
     */
    fun showEditorFragment(fileName: String, language: String) {
        try {
            // Update action bar title
            activity.supportActionBar?.title = fileName

            // Show editor fragment
            val editorFragment = EditorFragment.newInstance(language, fileName)
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, editorFragment)
                .commit()

            // Ensure navigation drawer icon displays correctly on editor page
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)

            Log.d("FragmentNavigationManager", "Show editor: file=$fileName, language=$language")
        } catch (e: Exception) {
            Log.e("FragmentNavigationManager", "Failed to show editor: ${e.message}", e)
        }
    }
    
    /**
     * Show welcome fragment
     * 显示欢迎页面Fragment
     */
    fun showWelcomeFragment() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, WelcomeFragment())
            .commit()
        activity.supportActionBar?.title = activity.getString(R.string.welcome)
        Log.d("FragmentNavigationManager", "Show welcome fragment")
    }
    
    /**
     * Show settings fragment
     * 显示设置Fragment
     */
    fun showSettingsFragment() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, SettingsFragment())
            .addToBackStack(null)
            .commit()

        // Close drawer
        drawerLayout.closeDrawer(GravityCompat.START)

        // Lock drawer to prevent swipe open on settings page
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    /**
     * Show shell fragment with terminal UI
     * 显示终端Fragment — 行为与编辑器一致，保留汉堡菜单
     */
    fun showShellFragment() {
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, ShellFragment())
            .commit()

        activity.supportActionBar?.title = activity.getString(R.string.shell)
        drawerLayout.closeDrawer(GravityCompat.START)
    }
    
    /**
     * Update navigation icon based on current fragment type
     * 更新导航栏状态 - 根据当前Fragment类型设置导航栏图标
     */
    fun updateNavigationIcon() {
        val currentFragment = activity.supportFragmentManager.findFragmentById(R.id.content_frame)

        if (currentFragment is SettingsFragment) {
            // Settings page: show back button and lock drawer
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            actionBarDrawerToggle.isDrawerIndicatorEnabled = false
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            actionBarDrawerToggle.setToolbarNavigationClickListener {
                activity.onBackPressedDispatcher.onBackPressed()
            }
            // Set title to "Settings"
            activity.supportActionBar?.title = activity.getString(R.string.settings)
            Log.d("FragmentNavigationManager", "Navigation icon: Settings page - back button")
        } else if (currentFragment is EditorFragment) {
            // Editor page: show menu icon
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            isDrawerToggleEnabled = true
            actionBarDrawerToggle.isDrawerIndicatorEnabled = true
            actionBarDrawerToggle.setToolbarNavigationClickListener(null)
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
            actionBarDrawerToggle.syncState()

            Log.d("FragmentNavigationManager", "Navigation icon: EditorFragment - menu icon")
        } else {
            // All other pages: show menu icon and unlock drawer
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            isDrawerToggleEnabled = true
            actionBarDrawerToggle.isDrawerIndicatorEnabled = true
            actionBarDrawerToggle.setToolbarNavigationClickListener(null)
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
            actionBarDrawerToggle.syncState()

            // If welcome page, set title to "Welcome"
            if (currentFragment is WelcomeFragment) {
                activity.supportActionBar?.title = activity.getString(R.string.welcome)
            }
            if (currentFragment is ShellFragment) {
                activity.supportActionBar?.title = activity.getString(R.string.shell)
            }

            Log.d(
                "FragmentNavigationManager",
                "Navigation icon: ${currentFragment?.javaClass?.simpleName ?: "Unknown"} - menu icon"
            )
        }
    }
    
    /**
     * Disable drawer toggle functionality
     * 禁用导航抽屉开关
     */
    fun disableDrawerToggle() {
        // Disable drawer swipe
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        isDrawerToggleEnabled = false

        // Completely disable ActionBarDrawerToggle
        actionBarDrawerToggle.isDrawerIndicatorEnabled = false
        actionBarDrawerToggle.setToolbarNavigationClickListener {
            activity.onBackPressedDispatcher.onBackPressed()
        }

        // Directly set back arrow
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)

        Log.d("FragmentNavigationManager", "Disabled navigation drawer and set back button")
    }
    
    /**
     * Reset navigation drawer to default state
     * 重置导航抽屉
     */
    fun resetNavigationDrawer() {
        updateNavigationIcon()
    }
    
    /**
     * Force show back button for settings page etc.
     * 强制返回按钮显示（用于设置页面等）
     */
    fun forceShowBackButton() {
        // Completely disable ActionBarDrawerToggle
        actionBarDrawerToggle.isDrawerIndicatorEnabled = false
        actionBarDrawerToggle.setToolbarNavigationClickListener {
            activity.onBackPressedDispatcher.onBackPressed()
        }

        // Directly set back arrow
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        activity.supportActionBar?.setDisplayShowHomeEnabled(true)

        Log.d("FragmentNavigationManager", "Forced to show back button")
    }
    
    /**
     * Handle system back button press
     * 处理系统返回按钮
     */
    fun handleBackPressed(): Boolean {
        // If drawer is open, close drawer first
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return true
        }

        // Get current fragment
        val currentFragment = activity.supportFragmentManager.findFragmentById(R.id.content_frame)

        // Check if IO panel is open, if so, close it
        val ioPanel = activity.supportFragmentManager.findFragmentByTag("io_panel")
        if (ioPanel != null) {
            activity.supportFragmentManager.beginTransaction()
                .remove(ioPanel)
                .commit()
            activity.supportFragmentManager.popBackStack()

            // Reset ActionBar and menu state
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)
            return true
        }

        // If current is settings page, manually handle back logic
        if (currentFragment is SettingsFragment) {
            // Unlock drawer
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

            // Restore menu icon
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)

            return false // Let system handle back stack
        }

        // If has back stack, pop one page
        if (activity.supportFragmentManager.backStackEntryCount > 0) {
            activity.supportFragmentManager.popBackStack()
            // Delay update navigation icon to ensure fragment transition completes
            Handler(Looper.getMainLooper()).postDelayed({
                updateNavigationIcon()
            }, 100)
            return true
        }

        return false // Let Activity handle default back behavior
    }
    
    /**
     * Get current fragment
     * 获取当前Fragment
     */
    fun getCurrentFragment(): Fragment? {
        return activity.supportFragmentManager.findFragmentById(R.id.content_frame)
    }
    
    /**
     * Check if drawer toggle is enabled
     * 检查是否启用了抽屉切换
     */
    fun isDrawerToggleEnabled(): Boolean {
        return isDrawerToggleEnabled
    }
    
    /**
     * Close navigation drawer
     * 关闭导航抽屉
     */
    fun closeDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
    
    /**
     * Check if navigation drawer is open
     * 检查导航抽屉是否打开
     */
    fun isDrawerOpen(): Boolean {
        return drawerLayout.isDrawerOpen(GravityCompat.START)
    }
} 