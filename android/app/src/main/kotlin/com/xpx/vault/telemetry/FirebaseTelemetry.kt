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
 * The SDK is only activated when a valid google-services.json is present and
 * the Google Services Gradle plugin generated the default Firebase app.
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
        val firebaseAnalytics = analytics ?: return
        firebaseAnalytics.logEvent(name, Bundle().apply {
            params.forEach { (key, value) ->
                putString(key.take(MAX_NAME_LENGTH), value.take(MAX_VALUE_LENGTH))
            }
        })
    }

    fun breadcrumb(tag: String, message: String) {
        crashlytics?.log("[${tag.take(MAX_NAME_LENGTH)}] ${message.take(MAX_VALUE_LENGTH)}")
    }

    private const val MAX_NAME_LENGTH = 40
    private const val MAX_VALUE_LENGTH = 100
}
