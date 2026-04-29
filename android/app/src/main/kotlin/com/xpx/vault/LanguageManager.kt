package com.xpx.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    const val LANG_EN = "en"
    const val LANG_ZH = "zh"

    fun initialize(context: Context) {
        // 读取持久化的语言；若不存在则按系统 Locale 写入，保证后续可预测。
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        val resolved = saved?.let { normalize(it) } ?: normalize(systemLanguageTag(context)).also {
            prefs.edit().putString(KEY_LANGUAGE, it).apply()
        }
        // 同步到 AppCompat 和 Android 13+ 系统 LocaleManager，让系统“应用语言”面板保持一致。
        applyLanguage(resolved)
    }

    fun getCurrentLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, null)
        return saved?.let { normalize(it) } ?: normalize(systemLanguageTag(context))
    }

    /** 仅读取持久化的语言。供 Activity.attachBaseContext 使用（此时不能再去查 systemLanguage，避免递归）。 */
    fun readStoredLanguage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, null)?.let { normalize(it) }
    }

    fun setLanguage(context: Context, language: String) {
        val normalized = normalize(language)
        val current = getCurrentLanguage(context)
        saveLanguage(context, normalized)
        applyLanguage(normalized)
        if (current == normalized) return
        // 不管哪个 API 级别都主动重建 Activity，触发 attachBaseContext 重新包装 Locale。
        findActivity(context)?.recreate()
    }

    /**
     * 在 Activity.attachBaseContext 中调用，将持久化的语言应用到该 Activity 的 Context 上。
     * 对所有 API 级别有效，不依赖 AppCompatActivity/AppCompatDelegate。
     */
    fun wrapContext(base: Context): Context {
        val lang = readStoredLanguage(base) ?: return base
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return base.createConfigurationContext(config)
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
        val locale = context.resources.configuration.let { cfg ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cfg.locales[0] else @Suppress("DEPRECATION") cfg.locale
        } ?: Locale.getDefault()
        return locale.language
    }

    private fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
