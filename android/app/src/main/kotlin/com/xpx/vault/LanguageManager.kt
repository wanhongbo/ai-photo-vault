package com.xpx.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    const val LANG_EN = "en"
    const val LANG_ZH = "zh"

    fun initialize(context: Context) {
        // 1) Prefer the locale already applied by the system (persisted by AppCompat
        //    autoStoreLocales on Android 13+, or by our prefs on older versions).
        val applied = AppCompatDelegate.getApplicationLocales()
        if (!applied.isEmpty) {
            val tag = applied[0]?.language.orEmpty()
            val normalized = normalize(tag)
            saveLanguage(context, normalized)
            return
        }

        // 2) Fallback to prefs saved by previous versions of the app.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        if (saved != null) {
            applyLanguage(normalize(saved))
            return
        }

        // 3) First launch: follow the current system locale instead of forcing English.
        val systemTag = systemLanguageTag(context)
        val normalized = normalize(systemTag)
        saveLanguage(context, normalized)
        applyLanguage(normalized)
    }

    fun getCurrentLanguage(context: Context): String {
        val applied = AppCompatDelegate.getApplicationLocales()
        if (!applied.isEmpty) {
            return normalize(applied[0]?.language.orEmpty())
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        return saved?.let { normalize(it) } ?: normalize(systemLanguageTag(context))
    }

    fun setLanguage(context: Context, language: String) {
        val normalized = normalize(language)
        val current = getCurrentLanguage(context)
        saveLanguage(context, normalized)
        applyLanguage(normalized)
        if (current == normalized) return
        // MainActivity 继承自 ComponentActivity，AppCompat 的 backport 不会自动重建；
        // Android 13+ 由系统 LocaleManager 触发 recreate，这里只需兜底 API < 33 的情况。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            findActivity(context)?.recreate()
        }
    }

    private fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun applyLanguage(language: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    private fun saveLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    private fun normalize(tag: String): String {
        return if (tag.startsWith(LANG_ZH, ignoreCase = true)) LANG_ZH else LANG_EN
    }

    private fun systemLanguageTag(context: Context): String {
        val locales = context.resources.configuration.let { cfg ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cfg.locales[0] else @Suppress("DEPRECATION") cfg.locale
        } ?: Locale.getDefault()
        return locales.language
    }
}
