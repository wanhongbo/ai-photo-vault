package com.xpx.vault

import android.app.Application
import android.util.Log
import com.xpx.vault.telemetry.FirebaseTelemetry

/**
 * 统一日志入口：禁止输出照片内容、密钥、密文、可识别用户路径。
 * Crashlytics 仅接收已脱敏的 breadcrumb；不要在 message 中放照片内容、PIN、密钥或明文路径。
 */
object AppLogger {
    private const val GLOBAL_TAG = "Luma"

    fun install(application: Application? = null) {
        application?.let { FirebaseTelemetry.install(it) }
    }

    fun d(tag: String, message: String) {
        val scrubbed = scrub(message)
        if (BuildConfig.DEBUG) {
            Log.d(GLOBAL_TAG, format(tag, scrubbed))
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val line = format(tag, scrub(message))
        FirebaseTelemetry.breadcrumb(tag, line)
        if (throwable != null) {
            Log.e(GLOBAL_TAG, line, throwable)
        } else {
            Log.e(GLOBAL_TAG, line)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val line = format(tag, scrub(message))
        FirebaseTelemetry.breadcrumb(tag, line)
        if (throwable != null) {
            Log.w(GLOBAL_TAG, line, throwable)
        } else {
            Log.w(GLOBAL_TAG, line)
        }
    }

    private fun format(tag: String, body: String): String = "[$tag] $body"

    private fun scrub(raw: String): String {
        var s = raw
        if (s.length > MAX_LEN) {
            s = s.substring(0, MAX_LEN) + "…(truncated)"
        }
        return s
    }

    private const val MAX_LEN = 2000
}
