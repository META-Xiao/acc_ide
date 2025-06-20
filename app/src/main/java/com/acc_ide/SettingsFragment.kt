package com.acc_ide

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.acc_ide.util.LocaleHelper
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
    
    // 当前应用语言
    private var currentLanguage: String = ""
    
    // 防止设置页面重新创建时再次触发语言切换
    private var isInitialSelection = true
    
    // 设置的键名
    companion object {
        const val PREF_FONT_SIZE = "editor_font_size"
        const val DEFAULT_FONT_SIZE = 18f
        const val PREF_ENABLE_SYMBOL_PANEL = "enable_symbol_panel"
        const val PREF_CURSOR_WIDTH = "editor_cursor_width"
        const val DEFAULT_CURSOR_WIDTH = 8f
        const val MIN_CURSOR_WIDTH = 2f
        const val MAX_CURSOR_WIDTH = 14f
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        // 初始化UI组件
        themeSwitch = view.findViewById(R.id.theme_switch)
        themeLabel = view.findViewById(R.id.theme_label)
        languageSpinner = view.findViewById(R.id.language_spinner)
        fontSizeSlider = view.findViewById(R.id.font_size_slider)
        fontSizeValue = view.findViewById(R.id.font_size_value)
        cursorWidthSlider = view.findViewById(R.id.cursor_width_slider)
        cursorWidthValue = view.findViewById(R.id.cursor_width_value)
        switchSymbolPanel = view.findViewById(R.id.switch_symbol_panel)

        // 初始化SharedPreferences
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        // 设置主题标签文本
        updateThemeLabel()
        
        // 设置主题开关
        val nightMode = prefs.getInt("app_night_mode", AppCompatDelegate.MODE_NIGHT_YES)
        
        // 设置开关状态 - 开启表示深色模式，关闭表示浅色模式
        themeSwitch.isChecked = nightMode == AppCompatDelegate.MODE_NIGHT_YES
        
        // 根据当前主题设置开关颜色
        updateSwitchColors()

        // 设置主题开关监听
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val selectedMode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES  // 深色模式
            } else {
                AppCompatDelegate.MODE_NIGHT_NO   // 浅色模式
                }
            
                if (selectedMode != nightMode) {
                    // 保存设置
                    prefs.edit().putInt("app_night_mode", selectedMode).apply()
                    // 切换主题
                    AppCompatDelegate.setDefaultNightMode(selectedMode)
                    // 重启 Activity 以应用主题
                    activity?.recreate()
                }
        }

        // 设置语言下拉框
        val languages = arrayOf("English", "中文")
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languages)
        languageSpinner.adapter = languageAdapter
        
        // 获取当前语言设置
        currentLanguage = LocaleHelper.getLanguage(requireContext())
        
        // 根据当前语言设置选择语言下拉框的初始值
        val languagePosition = when(currentLanguage) {
            "zh" -> 1 // 中文
            else -> 0 // 默认英文
        }
        languageSpinner.setSelection(languagePosition)
        
        // 设置语言选择监听器
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLanguage = when(position) {
                    0 -> "en" // 英文
                    1 -> "zh" // 中文
                    else -> "en" // 默认英文
                }
                
                // 如果选择了不同的语言，且不是初始化阶段，则应用新语言
                if (selectedLanguage != currentLanguage && !isInitialSelection) {
                    currentLanguage = selectedLanguage
                    applyLanguageChange(selectedLanguage)
                }
                
                // 第一次选择后将isInitialSelection设为false
                isInitialSelection = false
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 不做任何事情
            }
        }

        // 从SharedPreferences加载字体大小设置
        val savedFontSize = prefs.getFloat(PREF_FONT_SIZE, DEFAULT_FONT_SIZE)
        
        // 设置字体大小滑动条
        fontSizeSlider.value = savedFontSize
        fontSizeValue.text = savedFontSize.toInt().toString()
        
        // 设置字体大小滑动条监听器
        fontSizeSlider.addOnChangeListener { _, value, fromUser ->
            // 更新文本显示
            val fontSize = value.toInt()
            fontSizeValue.text = fontSize.toString()
            
            // 保存字体大小设置
            if (fromUser) {
                prefs.edit().putFloat(PREF_FONT_SIZE, value).apply()
                
                // 通知编辑器页面更新字体大小
                (activity as? MainActivity)?.updateEditorFontSize(value)
            }
        }

        // 从SharedPreferences加载光标宽度设置
        val savedCursorWidth = prefs.getFloat(PREF_CURSOR_WIDTH, DEFAULT_CURSOR_WIDTH)
        
        // 设置光标宽度滑动条
        cursorWidthSlider.valueFrom = MIN_CURSOR_WIDTH
        cursorWidthSlider.valueTo = MAX_CURSOR_WIDTH
        cursorWidthSlider.stepSize = 1f
        cursorWidthSlider.value = savedCursorWidth
        cursorWidthValue.text = savedCursorWidth.toInt().toString()
        
        // 设置光标宽度滑动条监听器
        cursorWidthSlider.addOnChangeListener { _, value, fromUser ->
            // 更新文本显示
            val cursorWidth = value.toInt()
            cursorWidthValue.text = cursorWidth.toString()
            
            // 保存光标宽度设置
            if (fromUser) {
                prefs.edit().putFloat(PREF_CURSOR_WIDTH, value).apply()
                
                // 通知编辑器页面更新光标宽度
                (activity as? MainActivity)?.updateEditorCursorWidth(value)
            }
        }

        // 设置符号面板开关
        val symbolPanelEnabled = prefs.getBoolean(PREF_ENABLE_SYMBOL_PANEL, true)
        switchSymbolPanel.isChecked = symbolPanelEnabled
        setSwitchColor(switchSymbolPanel)
        switchSymbolPanel.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_ENABLE_SYMBOL_PANEL, isChecked).apply()
        }

        return view
    }
    
    // 更新主题标签文本
    private fun updateThemeLabel() {
        // 根据当前语言设置主题标签文本
        val currentLocale = Locale.getDefault().language
        themeLabel.text = if (currentLocale == "zh") {
            "深色模式"
        } else {
            "Dark Mode"
        }
    }
    
    // 根据当前主题更新开关颜色
    private fun updateSwitchColors() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == 
                Configuration.UI_MODE_NIGHT_YES
        
        if (isDarkMode) {
            // 深色模式下使用紫色 #674fa4
            val purpleColor = Color.parseColor("#674fa4")
            themeSwitch.thumbTintList = ColorStateList.valueOf(purpleColor)
            themeSwitch.trackTintList = ColorStateList.valueOf(adjustAlpha(purpleColor, 0.5f))
        } else {
            // 浅色模式下使用灰黑色
            val grayColor = Color.parseColor("#333333")
            themeSwitch.thumbTintList = ColorStateList.valueOf(grayColor)
            themeSwitch.trackTintList = ColorStateList.valueOf(adjustAlpha(grayColor, 0.5f))
        }
    }
    
    // 辅助函数：调整颜色的透明度
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 配置ActionBar和导航抽屉
        setupActionBar()
        
        // 处理系统返回按钮
        setupBackNavigation()
        
        // 设置延迟检查，确保返回按钮显示
        Handler(Looper.getMainLooper()).postDelayed({
            (activity as? MainActivity)?.forceShowBackButton()
            Log.d("SettingsFragment", "延迟检查：确保返回按钮显示")
        }, 200)
        
        Log.d("SettingsFragment", "onViewCreated: 设置页面视图创建完成")
    }
    
    private fun setupActionBar() {
        // 获取MainActivity
        val mainActivity = activity as? MainActivity
        if (mainActivity != null) {
            // 锁定抽屉并禁用抽屉图标
            mainActivity.disableDrawerToggle()
            
            // 设置标题
            mainActivity.supportActionBar?.title = getString(R.string.settings)
            
            // 强制显示返回按钮
            mainActivity.forceShowBackButton()
            
            Log.d("SettingsFragment", "设置页面ActionBar配置完成，返回按钮已设置")
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // 在onResume中再次强制显示返回按钮
        (activity as? MainActivity)?.forceShowBackButton()
        
        // 更新主题标签文本
        updateThemeLabel()
        
        Log.d("SettingsFragment", "onResume: 再次确认返回按钮设置")
    }
    
    private fun setupBackNavigation() {
        // 处理系统返回按钮
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("SettingsFragment", "系统返回按钮点击")
                navigateBack()
            }
        })
    }
    
    private fun navigateBack() {
        Log.d("SettingsFragment", "执行返回操作，当前回退栈数量: ${parentFragmentManager.backStackEntryCount}")
        
        // 弹出回退栈以返回前一个Fragment，如果没有回退栈，直接调用onBackPressed
        if (parentFragmentManager.backStackEntryCount > 0) {
            parentFragmentManager.popBackStack()
        } else {
            // 直接通知Activity处理返回
            (activity as? MainActivity)?.onBackPressed()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // 重置MainActivity的ActionBar和导航抽屉状态
        val mainActivity = activity as? MainActivity
        mainActivity?.resetNavigationDrawer()
        
        Log.d("SettingsFragment", "onDestroyView: 设置页面视图销毁")
    }
    
    /**
     * 应用语言变化
     */
    private fun applyLanguageChange(languageCode: String) {
        try {
            // 通知MainActivity切换语言
            val mainActivity = activity as? MainActivity
            mainActivity?.changeLanguage(languageCode)
        } catch (e: Exception) {
            // 处理异常
            Toast.makeText(requireContext(), getString(R.string.language_change_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    // 设置SwitchCompat颜色，开为#674fa4，关为#BDBDBD，track开为#674fa4，关为淡灰色，dark/light统一
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
            ContextCompat.getColor(requireContext(), R.color.switch_thumb_on), // checked时track也为紫色
            Color.parseColor("#FFE0E0E0") // 未选中为淡灰色
        )
        switch.thumbTintList = ColorStateList(thumbStates, thumbColors)
        switch.trackTintList = ColorStateList(trackStates, trackColors)
    }
} 