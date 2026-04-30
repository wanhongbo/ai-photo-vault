package com.xpx.vault

import android.util.Log

/**
 * 统一日志入口：禁止输出照片内容、密钥、密文、可识别用户路径。
 * 一期不接入 Firebase；仅 Logcat / 后续 Play 控制台崩溃。
 */
object AppLogger {
    private const val GLOBAL_TAG = "PhotoVault"

    fun install() {
        // 预留：若后续需要集中开关、采样或本地计数，可在此扩展。
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(GLOBAL_TAG, format(tag, scrub(message)))
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val line = format(tag, scrub(message))
        if (throwable != null) {
            Log.e(GLOBAL_TAG, line, throwable)
        } else {
            Log.e(GLOBAL_TAG, line)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val line = format(tag, scrub(message))
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
