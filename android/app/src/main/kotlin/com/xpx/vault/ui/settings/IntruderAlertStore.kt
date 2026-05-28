package com.xpx.vault.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.xpx.vault.AppLogger
import com.xpx.vault.data.crypto.VaultCipher
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val INTRUDER_PREFS = "intruder_alert_prefs"
private const val INTRUDER_ENABLED = "enabled"
private const val INTRUDER_MAX_RECORDS = 50
private const val TAG_INTRUDER = "IntruderAlert"

data class IntruderAlertRecord(
    val id: String,
    val createdAtMillis: Long,
    val attemptedPin: String,
    val capturePath: String?,
    val captureStatus: IntruderCaptureStatus,
)

enum class IntruderCaptureStatus {
    CAPTURED,
    PERMISSION_DENIED,
    UNAVAILABLE,
    FAILED,
}

object IntruderAlertStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(INTRUDER_PREFS, Context.MODE_PRIVATE)
            .getBoolean(INTRUDER_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(INTRUDER_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(INTRUDER_ENABLED, enabled)
            .apply()
    }

    fun recordFailedPinAttempt(context: Context, attemptedPin: String) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return
        scope.launchCatching {
            recordFailedPinAttemptBlocking(appContext, attemptedPin)
        }
    }

    suspend fun loadRecords(context: Context): List<IntruderAlertRecord> = withContext(Dispatchers.IO) {
        readRecords(context.applicationContext).sortedByDescending { it.createdAtMillis }
    }

    suspend fun clearAll(context: Context) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        alertsDir(appContext).deleteRecursively()
        indexFile(appContext).delete()
    }

    suspend fun deleteRecord(context: Context, recordId: String) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val next = readRecords(appContext).filterNot { record ->
            if (record.id == recordId) {
                record.capturePath?.let { runCatching { File(it).delete() } }
                true
            } else {
                false
            }
        }
        writeRecords(appContext, next)
    }

    fun loadCaptureBytes(context: Context, record: IntruderAlertRecord): ByteArray? {
        val path = record.capturePath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching { VaultCipher.get(context.applicationContext).decryptToByteArray(file) }.getOrNull()
    }

    private suspend fun recordFailedPinAttemptBlocking(context: Context, attemptedPin: String) {
        val id = UUID.randomUUID().toString()
        val capture = IntruderCaptureService.captureFrontCamera(context, id)
        val existing = readRecords(context)
        val record = IntruderAlertRecord(
            id = id,
            createdAtMillis = System.currentTimeMillis(),
            attemptedPin = attemptedPin,
            capturePath = capture.encryptedPath,
            captureStatus = capture.status,
        )
        val next = (listOf(record) + existing.sortedByDescending { it.createdAtMillis })
            .take(INTRUDER_MAX_RECORDS)
        val dropped = existing.map { it.id }.toSet() - next.map { it.id }.toSet()
        existing.filter { it.id in dropped }.forEach { old ->
            old.capturePath?.let { runCatching { File(it).delete() } }
        }
        writeRecords(context, next)
    }

    private fun readRecords(context: Context): List<IntruderAlertRecord> {
        val file = indexFile(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            val plain = VaultCipher.get(context).decryptToByteArray(file).decodeToString()
            val arr = JSONArray(plain)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        IntruderAlertRecord(
                            id = obj.getString("id"),
                            createdAtMillis = obj.getLong("createdAtMillis"),
                            attemptedPin = obj.getString("attemptedPin"),
                            capturePath = obj.optString("capturePath").ifBlank { null },
                            captureStatus = runCatching {
                                IntruderCaptureStatus.valueOf(obj.getString("captureStatus"))
                            }.getOrDefault(IntruderCaptureStatus.FAILED),
                        ),
                    )
                }
            }
        }.onFailure {
            AppLogger.w(TAG_INTRUDER, "read records failed: ${it.message}")
        }.getOrDefault(emptyList())
    }

    private fun writeRecords(context: Context, records: List<IntruderAlertRecord>) {
        val arr = JSONArray()
        records.forEach { record ->
            arr.put(
                JSONObject()
                    .put("id", record.id)
                    .put("createdAtMillis", record.createdAtMillis)
                    .put("attemptedPin", record.attemptedPin)
                    .put("capturePath", record.capturePath ?: "")
                    .put("captureStatus", record.captureStatus.name),
            )
        }
        val dir = alertsDir(context).also { it.mkdirs() }
        val temp = File(dir, "intruder_alerts_plain_${System.nanoTime()}.json")
        temp.writeText(arr.toString())
        temp.inputStream().use { input ->
            VaultCipher.get(context).encryptFile(input, indexFile(context))
        }
        temp.delete()
    }

    private fun indexFile(context: Context): File =
        File(alertsDir(context), "intruder_alerts_v1.json.enc")

    private fun alertsDir(context: Context): File =
        File(context.filesDir, "intruder_alerts")
}

private data class IntruderCaptureResult(
    val status: IntruderCaptureStatus,
    val encryptedPath: String?,
)

private object IntruderCaptureService {
    suspend fun captureFrontCamera(context: Context, recordId: String): IntruderCaptureResult {
        val appContext = context.applicationContext
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return IntruderCaptureResult(IntruderCaptureStatus.PERMISSION_DENIED, null)
        }
        val temp = File(appContext.cacheDir, "intruder_capture_$recordId.jpg")
        val encrypted = File(File(appContext.filesDir, "intruder_alerts"), "capture_$recordId.jpg.enc")
        return runCatching {
            takePhoto(appContext, temp)
            temp.inputStream().use { input -> VaultCipher.get(appContext).encryptFile(input, encrypted) }
            temp.delete()
            IntruderCaptureResult(IntruderCaptureStatus.CAPTURED, encrypted.absolutePath)
        }.onFailure {
            temp.delete()
            AppLogger.w(TAG_INTRUDER, "capture failed: ${it.message}")
        }.getOrElse {
            IntruderCaptureResult(IntruderCaptureStatus.FAILED, null)
        }
    }

    private suspend fun takePhoto(context: Context, output: File) {
        val cameraProvider = context.cameraProvider()
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        withContext(Dispatchers.Main) {
            cameraProvider.unbind(imageCapture)
            cameraProvider.bindToLifecycle(
                ProcessLifecycleOwner.get(),
                CameraSelector.DEFAULT_FRONT_CAMERA,
                imageCapture,
            )
        }
        try {
            imageCapture.takePictureSuspending(output, ContextCompat.getMainExecutor(context))
        } finally {
            withContext(Dispatchers.Main) {
                runCatching { cameraProvider.unbind(imageCapture) }
            }
        }
    }

    private suspend fun Context.cameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(this)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(this),
            )
        }

    private suspend fun ImageCapture.takePictureSuspending(output: File, executor: Executor) {
        output.parentFile?.mkdirs()
        val options = ImageCapture.OutputFileOptions.Builder(output).build()
        suspendCancellableCoroutine { continuation ->
            takePicture(
                options,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(Unit)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
    }
}

private fun CoroutineScope.launchCatching(block: suspend () -> Unit) {
    launch {
        runCatching { block() }
            .onFailure { AppLogger.w(TAG_INTRUDER, "record failed: ${it.message}") }
    }
}
