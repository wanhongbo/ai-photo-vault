package com.photovault.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageManager {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    const val LANG_EN = "en"
    const val LANG_ZH = "zh"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val language = prefs.getString(KEY_LANGUAGE, null) ?: LANG_EN.also {
            prefs.edit().putString(KEY_LANGUAGE, it).apply()
        }
        applyLanguage(language)
    }

    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANG_EN) ?: LANG_EN
    }

    fun setLanguage(context: Context, language: String) {
        val normalized = if (language.startsWith(LANG_ZH)) LANG_ZH else LANG_EN
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, normalized).apply()
        applyLanguage(normalized)
    }

    private fun applyLanguage(language: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }
}
