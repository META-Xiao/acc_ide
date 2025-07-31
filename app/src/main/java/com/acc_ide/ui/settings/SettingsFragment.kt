package com.acc_ide.ui.settings

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.acc_ide.R
import com.acc_ide.ui.main.MainActivity
import com.acc_ide.util.LocaleHelper
import com.acc_ide.util.TextMateManager
import com.google.android.material.slider.Slider
import androidx.appcompat.app.AppCompatDelegate
import android.content.SharedPreferences
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import java.util.Locale
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Settings fragment for app configuration and preferences
 * 设置Fragment - 用于应用配置和偏好设置
 */
class SettingsFragment : Fragment() {
    private lateinit var themeSwitch: SwitchCompat
    private lateinit var themeLabel: TextView
    private lateinit var languageSpinner: Spinner
    private lateinit var fontSizeSlider: Slider
    private lateinit var fontSizeValue: TextView
    private lateinit var cursorWidthSlider: Slider
    private lateinit var cursorWidthValue: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var switchSymbolPanel: SwitchCompat
    private lateinit var switchAutoCompletion: SwitchCompat
    private lateinit var switchLsp: SwitchCompat
    private lateinit var githubRepoEditText: TextInputEditText
    private lateinit var githubPatEditText: TextInputEditText
    private lateinit var testGithubButton: Button
    private lateinit var githubStatusText: TextView
    
    // Current app language
    private var currentLanguage: String = ""
    
    // Prevent language switching from being triggered again when settings page is recreated
    private var isInitialSelection = true
    
    // Settings preference keys
    companion object {
        const val PREF_FONT_SIZE = "editor_font_size"
        const val DEFAULT_FONT_SIZE = 18f
        const val PREF_ENABLE_SYMBOL_PANEL = "enable_symbol_panel"
        const val PREF_ENABLE_AUTO_COMPLETION = "enable_auto_completion"
        const val PREF_ENABLE_LSP = "enable_lsp"
        const val PREF_CURSOR_WIDTH = "editor_cursor_width"
        const val DEFAULT_CURSOR_WIDTH = 8f
        const val MIN_CURSOR_WIDTH = 2f
        const val MAX_CURSOR_WIDTH = 14f
        const val PREF_GITHUB_REPO_URL = "github_repo_url"
        // Warning: Storing PAT in plaintext SharedPreferences is insecure.
        // For a production app, use EncryptedSharedPreferences or the Android Keystore system.
        const val PREF_GITHUB_PAT = "github_pat"
        const val GITHUB_API_BASE = "https://api.github.com"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // Initialize UI components
        themeSwitch = view.findViewById(R.id.theme_switch)
        themeLabel = view.findViewById(R.id.theme_label)
        languageSpinner = view.findViewById(R.id.language_spinner)
        fontSizeSlider = view.findViewById(R.id.font_size_slider)
        fontSizeValue = view.findViewById(R.id.font_size_value)
        cursorWidthSlider = view.findViewById(R.id.cursor_width_slider)
        cursorWidthValue = view.findViewById(R.id.cursor_width_value)
        switchSymbolPanel = view.findViewById(R.id.switch_symbol_panel)
        switchLsp = view.findViewById(R.id.switch_lsp)
        githubRepoEditText = view.findViewById(R.id.github_repo_edit_text)
        githubPatEditText = view.findViewById(R.id.github_pat_edit_text)
        testGithubButton = view.findViewById(R.id.test_github_button)
        githubStatusText = view.findViewById(R.id.github_status_text)

        // Initialize SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // Set theme label text
        updateThemeLabel()
        
        // Set theme switch
        val nightMode = prefs.getInt("app_night_mode", AppCompatDelegate.MODE_NIGHT_YES)
        
        // Set switch state - on for dark mode, off for light mode
        themeSwitch.isChecked = nightMode == AppCompatDelegate.MODE_NIGHT_YES
        
        // Set switch colors based on current theme
        updateSwitchColors()

        // Set theme switch listener
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val selectedMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES  // Dark mode
            } else {
                AppCompatDelegate.MODE_NIGHT_NO   // Light mode
                }
            
                if (selectedMode != nightMode) {
                    // Save settings
                    prefs.edit().putInt("app_night_mode", selectedMode).apply()
                    
                    // Update TextMate theme based on dark or light mode
                        val textMateTheme = if (isChecked) "dark.json" else "light.json"
                    TextMateManager.setTheme(textMateTheme)
                    
                    // Switch theme
                    AppCompatDelegate.setDefaultNightMode(selectedMode)
                    // Restart Activity to apply theme
                    activity?.recreate()
                }
        }

        // Set language spinner
        val languages = arrayOf("English", "中文")
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languages)
        languageSpinner.adapter = languageAdapter
        
        // Get current language setting
        currentLanguage = LocaleHelper.getLanguage(requireContext())
        
        // Set initial language spinner selection based on current language
        val languagePosition = when(currentLanguage) {
            "zh" -> 1 // Chinese
            else -> 0 // Default English
        }
        languageSpinner.setSelection(languagePosition)
        
        // Set language selection listener
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = when(position) {
                    0 -> "en" // English
                    1 -> "zh" // Chinese
                    else -> "en" // Default English
                }
                
                // If different language selected and not in initialization phase, apply new language
                if (selectedLanguage != currentLanguage && !isInitialSelection) {
                    currentLanguage = selectedLanguage
                    applyLanguageChange(selectedLanguage)
                }
                
                // Set isInitialSelection to false after first selection
                isInitialSelection = false
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }

        // Load font size setting from SharedPreferences
        val savedFontSize = prefs.getFloat(PREF_FONT_SIZE, DEFAULT_FONT_SIZE)
        
        // Set font size slider
        fontSizeSlider.value = savedFontSize
        fontSizeValue.text = savedFontSize.toInt().toString()
        
        // Set font size slider listener
        fontSizeSlider.addOnChangeListener { _, value, fromUser ->
            // Update text display
            val fontSize = value.toInt()
            fontSizeValue.text = fontSize.toString()
            
            // Save font size setting
            if (fromUser) {
                prefs.edit().putFloat(PREF_FONT_SIZE, value).apply()
                
                // Notify editor page to update font size
                (activity as? MainActivity)?.updateEditorFontSize(value)
            }
        }

        // Load cursor width setting from SharedPreferences
        val savedCursorWidth = prefs.getFloat(PREF_CURSOR_WIDTH, DEFAULT_CURSOR_WIDTH)
        
        // Set cursor width slider
        cursorWidthSlider.valueFrom = MIN_CURSOR_WIDTH
        cursorWidthSlider.valueTo = MAX_CURSOR_WIDTH
        cursorWidthSlider.stepSize = 1f
        cursorWidthSlider.value = savedCursorWidth
        cursorWidthValue.text = savedCursorWidth.toInt().toString()
        
        // Set cursor width slider listener
        cursorWidthSlider.addOnChangeListener { _, value, fromUser ->
            // Update text display
            val cursorWidth = value.toInt()
            cursorWidthValue.text = cursorWidth.toString()
            
            // Save cursor width setting
            if (fromUser) {
                prefs.edit().putFloat(PREF_CURSOR_WIDTH, value).apply()
                
                // Notify editor page to update cursor width
                (activity as? MainActivity)?.updateEditorCursorWidth(value)
            }
        }

        // Set symbol panel switch
        val symbolPanelEnabled = prefs.getBoolean(PREF_ENABLE_SYMBOL_PANEL, true)
        switchSymbolPanel.isChecked = symbolPanelEnabled
        setSwitchColor(switchSymbolPanel)
        switchSymbolPanel.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_SYMBOL_PANEL, isChecked).apply()
        }
        
        // Set auto completion switch
        switchAutoCompletion = view.findViewById(R.id.switch_auto_completion)
        val autoCompletionEnabled = prefs.getBoolean(PREF_ENABLE_AUTO_COMPLETION, true)
        switchAutoCompletion.isChecked = autoCompletionEnabled
        setSwitchColor(switchAutoCompletion)
        switchAutoCompletion.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_AUTO_COMPLETION, isChecked).apply()
            // Notify all open editors to update auto completion state
            (activity as? MainActivity)?.updateAutoCompletionState(isChecked)
        }
        
        // Set LSP switch
        val lspEnabled = prefs.getBoolean(PREF_ENABLE_LSP, true)
        switchLsp.isChecked = lspEnabled
        setSwitchColor(switchLsp)
        switchLsp.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_LSP, isChecked).apply()
            // Show status message
            val message = if (isChecked) {
                getString(R.string.lsp_enabled)
            } else {
                getString(R.string.lsp_disabled)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            
            // Notify all open editors to update LSP state
            (activity as? MainActivity)?.updateLspState(isChecked)
        }

        // Load saved GitHub settings
        loadGitHubSettings()

        // Setup GitHub listeners
        setupGitHubListeners()

        return view
    }
    
    /**
     * Update theme label text based on current language
     * 根据当前语言更新主题标签文本
     */
    private fun updateThemeLabel() {
        // Set theme label text based on current language
        val currentLocale = Locale.getDefault().language
        themeLabel.text = if (currentLocale == "zh") {
            "深色模式"
        } else {
            "Dark Mode"
        }
    }
    
    /**
     * Update switch colors based on current theme mode
     * 根据当前主题更新开关颜色
     */
    private fun updateSwitchColors() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == 
                Configuration.UI_MODE_NIGHT_YES
        
        if (isDarkMode) {
            val purpleColor = Color.parseColor("#674fa4")
            themeSwitch.thumbTintList = ColorStateList.valueOf(purpleColor)
            themeSwitch.trackTintList = ColorStateList.valueOf(adjustAlpha(purpleColor))
        } else {
            val grayColor = Color.parseColor("#333333")
            themeSwitch.thumbTintList = ColorStateList.valueOf(grayColor)
            themeSwitch.trackTintList = ColorStateList.valueOf(adjustAlpha(grayColor))
        }
    }
    
    /**
     * Helper function to adjust color alpha (fixed at 50%)
     * 辅助函数：调整颜色的透明度（固定为50%）
     */
    private fun adjustAlpha(color: Int): Int {
        val alpha = (Color.alpha(color) * 0.5f).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Configure ActionBar and navigation drawer
        setupActionBar()
        
        // Handle system back button
        setupBackNavigation()
        
        // Set delayed check to ensure back button is displayed
        Handler(Looper.getMainLooper()).postDelayed({
            (activity as? MainActivity)?.forceShowBackButton()
            Log.d("SettingsFragment", "Delayed check: ensure back button is displayed")
        }, 200)
        
        Log.d("SettingsFragment", "onViewCreated: Settings page view creation completed")
    }
    
    /**
     * Setup ActionBar configuration for settings
     * 设置ActionBar配置
     */
    private fun setupActionBar() {
        // Get MainActivity
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            // Lock drawer and disable drawer icon
            mainActivity.disableDrawerToggle()
            
            // Set title
            mainActivity.supportActionBar?.title = getString(R.string.settings)
            
            // Force show back button
            mainActivity.forceShowBackButton()
            
            Log.d("SettingsFragment", "Settings page ActionBar configuration completed, back button set")
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Force show back button again in onResume
        (activity as? MainActivity)?.forceShowBackButton()
        
        // Update theme label text
        updateThemeLabel()
        
        Log.d("SettingsFragment", "onResume: Confirmed back button setting again")
    }
    
    /**
     * Setup back navigation handling
     * 设置返回导航处理
     */
    private fun setupBackNavigation() {
        // Handle system back button
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("SettingsFragment", "System back button clicked")
                navigateBack(this)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }
    
    /**
     * Navigate back from settings
     * 从设置页面导航返回
     */
    private fun navigateBack(callback: OnBackPressedCallback? = null) {
        Log.d("SettingsFragment", "Executing back operation, current back stack count: ${parentFragmentManager.backStackEntryCount}")
        
        // Pop back stack to return to previous fragment, if no back stack, directly call onBackPressed
        if (parentFragmentManager.backStackEntryCount > 0) {
            parentFragmentManager.popBackStack()
        } else {
            // Important: disable callback to avoid recursive calls
            callback?.remove()
            
            activity?.let { mainActivity ->
                if (mainActivity is MainActivity) {
                    mainActivity.onFragmentBackPressed()
                }
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Reset MainActivity's ActionBar and navigation drawer state
        val mainActivity = activity as? MainActivity
        mainActivity?.resetNavigationDrawer()
        
        Log.d("SettingsFragment", "onDestroyView: Settings page view destroyed")
    }
    
    /**
     * Apply language change and notify MainActivity
     * 应用语言变化
     */
    private fun applyLanguageChange(languageCode: String) {
        try {
            // Notify MainActivity to change language
            val mainActivity = activity as? MainActivity
            mainActivity?.changeLanguage(languageCode)
        } catch (e: Exception) {
            // Handle exception
            Toast.makeText(requireContext(), getString(R.string.language_change_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Set SwitchCompat color scheme - on: #674fa4, off: #BDBDBD, track on: #674fa4, off: light gray, unified for dark/light
     * 设置SwitchCompat颜色方案
     */
    private fun setSwitchColor(switch: SwitchCompat) {
        val thumbStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbColors = intArrayOf(
            ContextCompat.getColor(requireContext(), R.color.switch_thumb_on),
            ContextCompat.getColor(requireContext(), R.color.switch_thumb_off)
        )
        val trackStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val trackColors = intArrayOf(
            ContextCompat.getColor(requireContext(), R.color.switch_thumb_on), // Track also purple when checked
            Color.parseColor("#FFE0E0E0") // Light gray when unchecked
        )
        switch.thumbTintList = ColorStateList(thumbStates, thumbColors)
        switch.trackTintList = ColorStateList(trackStates, trackColors)
    }

    /**
     * Load saved GitHub settings from preferences
     * 从偏好设置加载保存的GitHub设置
     */
    private fun loadGitHubSettings() {
        val repoUrl = prefs.getString(PREF_GITHUB_REPO_URL, "")
        val pat = prefs.getString(PREF_GITHUB_PAT, "")
        githubRepoEditText.setText(repoUrl)
        githubPatEditText.setText(pat)
    }

    /**
     * Setup GitHub button listeners
     * 设置GitHub按钮监听器
     */
    private fun setupGitHubListeners() {
        testGithubButton.setOnClickListener {
            val repoUrl = githubRepoEditText.text.toString().trim()
            val pat = githubPatEditText.text.toString().trim()
            testGitHubConnection(repoUrl, pat)
        }
    }

    /**
     * Test GitHub connection with provided repository URL and PAT
     * 使用提供的仓库URL和PAT测试GitHub连接
     */
    private fun testGitHubConnection(repoUrl: String, pat: String) {
        if (repoUrl.isEmpty() || pat.isEmpty()) {
            githubStatusText.text = getString(R.string.github_repo_pat_empty)
            githubStatusText.setTextColor(Color.RED)
            return
        }

        val regex = "https://github.com/([^/]+)/([^/]+)".toRegex()
        val match = regex.find(repoUrl)

        if (match == null || match.groupValues.size < 3) {
            githubStatusText.text = getString(R.string.github_repo_invalid)
            githubStatusText.setTextColor(Color.RED)
            return
        }

        val owner = match.groupValues[1]
        val repo = match.groupValues[2].removeSuffix(".git")

        githubStatusText.text = getString(R.string.connection_testing)
        githubStatusText.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))

        // Use coroutine to perform network operation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create OkHttp client with timeout
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                    
                // Build request
                val request = Request.Builder()
                    .url("$GITHUB_API_BASE/repos/$owner/$repo")
                    .header("Authorization", "token $pat")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                // Execute request synchronously within the IO coroutine
                val response = client.newCall(request).execute()
                
                // Switch to Main thread to update UI
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        githubStatusText.text = getString(R.string.connection_successful)
                        githubStatusText.setTextColor(Color.parseColor("#388E3C")) // Green

                        // Save credentials on successful connection
                        prefs.edit()
                            .putString(PREF_GITHUB_REPO_URL, repoUrl)
                            .putString(PREF_GITHUB_PAT, pat)
                            .apply()

                        Toast.makeText(context, getString(R.string.github_settings_saved), Toast.LENGTH_SHORT).show()
                    } else {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        val message = try { 
                            JSONObject(errorBody).getString("message") 
                        } catch (e: Exception) { 
                            "${response.code} ${response.message}" 
                        }
                        githubStatusText.text = getString(R.string.connection_failed, message)
                        githubStatusText.setTextColor(Color.RED)
                    }
                    
                    // Close the response body to prevent resource leaks
                    response.body?.close()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    githubStatusText.text = getString(R.string.connection_failed, e.message)
                    githubStatusText.setTextColor(Color.RED)
                }
            }
        }
    }
} 