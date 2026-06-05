package com.xpx.vault.telemetry

import android.app.Application
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.xpx.vault.BuildConfig

/**
 * Firebase Analytics / Crashlytics adapter.
 *
 * The SDK is activated only when a valid google-services.json is present and
 * the Google Services Gradle plugin generated a default Firebase app.
 */
object FirebaseTelemetry {
    @Volatile
    private var analytics: FirebaseAnalytics? = null

    @Volatile
    private var crashlytics: FirebaseCrashlytics? = null

    fun install(application: Application) {
        if (FirebaseApp.getApps(application).isEmpty()) {
            return
        }
        analytics = FirebaseAnalytics.getInstance(application).apply {
            setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        }
        crashlytics = FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }
    }

    fun logEvent(name: String, params: Map<String, String>) {
        val instance = analytics ?: return
        instance.logEvent(sanitizeName(name), Bundle().apply {
            params.forEach { (key, value) ->
                putString(sanitizeName(key), sanitizeValue(value))
            }
        })
    }

    fun breadcrumb(tag: String, message: String) {
        crashlytics?.log("[${sanitizeName(tag)}] ${sanitizeValue(message)}")
    }

    private fun sanitizeName(value: String): String =
        value.filter { it.isLetterOrDigit() || it == '_' }
            .ifBlank { "event" }
            .take(MAX_NAME_LENGTH)

    private fun sanitizeValue(value: String): String = value.take(MAX_VALUE_LENGTH)

    private const val MAX_NAME_LENGTH = 40
    private const val MAX_VALUE_LENGTH = 100
}
