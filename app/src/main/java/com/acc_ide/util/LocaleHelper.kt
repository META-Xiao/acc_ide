package com.acc_ide.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * Helper class to change the app language dynamically
 */
class LocaleHelper {
    companion object {
        private const val SELECTED_LANGUAGE = "selected_language"

        /**
         * 更新应用程序的语言
         */
        fun setLocale(context: Context, language: String): Context {
            // 保存语言设置到 SharedPreferences
            saveLanguage(context, language)

            // 如果语言代码为空，使用系统默认语言
            val languageToApply = if (language.isEmpty()) {
                Locale.getDefault().language
            } else {
                language
            }

            // 更新资源配置
            return updateResources(context, languageToApply)
        }

        /**
         * 获取保存的语言代码
         */
        fun getLanguage(context: Context): String {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getString(SELECTED_LANGUAGE, "") ?: ""
        }

        /**
         * 保存语言设置到 SharedPreferences
         */
        private fun saveLanguage(context: Context, language: String) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val editor = prefs.edit()
            editor.putString(SELECTED_LANGUAGE, language)
            editor.apply()
        }

        /**
         * 更新资源配置以应用新语言
         */
        private fun updateResources(context: Context, language: String): Context {
            val locale = Locale(language)
            Locale.setDefault(locale)

            val resources = context.resources
            val configuration = Configuration(resources.configuration)
            configuration.setLocale(locale)

            // 在API 17+上使用createConfigurationContext，但也更新原始上下文的资源
            // 这样有助于在处理configChanges时正确更新资源
            val updatedContext = context.createConfigurationContext(configuration)
            
            // 同时更新资源配置，对于在onConfigurationChanged中使用
            resources.updateConfiguration(configuration, resources.displayMetrics)
            
            return updatedContext
        }
    }
} 