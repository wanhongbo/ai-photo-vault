package com.xpx.vault.telemetry

import com.xpx.vault.AppLogger
import java.util.Locale

/**
 * Low-sensitivity product telemetry for lifecycle and business checkpoints.
 *
 * Never pass media paths, album names, filenames, PINs, keys, OCR text, or user-entered free text.
 */
object LumaTelemetry {
    private const val TAG = "Telemetry"

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        val cleaned = params.mapValues { (_, value) -> sanitizeValue(value) }
        AppLogger.d(TAG, "[$name] ${cleaned.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        FirebaseTelemetry.logEvent(name, cleaned)
    }

    fun breadcrumb(tag: String, message: String) {
        FirebaseTelemetry.breadcrumb(tag, sanitizeValue(message))
    }

    fun trackAppStart(versionName: String, versionCode: Int, isDebug: Boolean) {
        logEvent(
            "app_start",
            mapOf(
                "version_name" to versionName,
                "version_code" to versionCode.toString(),
                "build_type" to if (isDebug) "debug" else "release",
            ),
        )
    }

    fun trackLock(event: String, method: String, result: String) {
        logEvent("lock_$event", mapOf("method" to method, "result" to result))
    }

    fun trackVaultImport(source: String, mediaType: String, result: String) {
        logEvent("vault_import", mapOf("source" to source, "media_type" to mediaType, "result" to result))
    }

    fun trackVaultItem(action: String, result: String) {
        logEvent("vault_item", mapOf("action" to action, "result" to result))
    }

    fun trackCameraCapture(mediaType: String, result: String) {
        logEvent("camera_capture", mapOf("media_type" to mediaType, "result" to result))
    }

    fun trackBackup(trigger: String, kind: String, result: String, assetCount: Int = 0) {
        logEvent(
            "backup_create",
            mapOf(
                "trigger" to trigger.lowercase(Locale.US),
                "kind" to kind.lowercase(Locale.US),
                "result" to result,
                "asset_count" to assetCount.coerceAtLeast(0).toString(),
            ),
        )
    }

    fun trackRestore(source: String, result: String, restored: Int = 0, skipped: Int = 0, failed: Int = 0) {
        logEvent(
            "backup_restore",
            mapOf(
                "source" to source,
                "result" to result,
                "restored" to restored.coerceAtLeast(0).toString(),
                "skipped" to skipped.coerceAtLeast(0).toString(),
                "failed" to failed.coerceAtLeast(0).toString(),
            ),
        )
    }

    fun trackAiScan(result: String, total: Int, processed: Int) {
        logEvent(
            "ai_scan",
            mapOf(
                "result" to result,
                "total" to total.coerceAtLeast(0).toString(),
                "processed" to processed.coerceAtLeast(0).toString(),
            ),
        )
    }

    private fun sanitizeValue(value: String): String =
        value.replace('\n', ' ')
            .replace('\r', ' ')
            .take(MAX_VALUE_LENGTH)

    private const val MAX_VALUE_LENGTH = 100
}
