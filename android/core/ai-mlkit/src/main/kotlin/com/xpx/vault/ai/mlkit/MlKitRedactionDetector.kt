package com.xpx.vault.ai.mlkit

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xpx.vault.ai.algo.SensitiveRegexMatcher
import com.xpx.vault.ai.privacy.RedactionKind
import com.xpx.vault.ai.privacy.RedactionRegion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 为隐私脱敏页提供像素级敏感区域。不写入 Room，只给 UI 渲染用。
 *
 * 检测范围：
 *  - 人脸：boundingBox 直接作为 FACE 区域。
 *  - 文本：每一个 [Text.Line] 如果文本匹配身份证/手机号/银行卡正则，取其 boundingBox。
 *  - 条码：每一个 Barcode 的 boundingBox。
 *
 * GMS 不可用时 [isReady] 返回 false，上层需降级（例如只允许手动框选，暂未实现）。
 */
@Singleton
class MlKitRedactionDetector @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build(),
        )
    }
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    suspend fun isReady(): Boolean {
        val avail = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        }.getOrDefault(-1)
        return avail == ConnectionResult.SUCCESS
    }

    suspend fun detect(bitmap: Bitmap): List<RedactionRegion> {
        val input = InputImage.fromBitmap(bitmap, 0)
        val faces: List<Face> = awaitOrNull { faceDetector.process(input) }.orEmpty()
        val text: Text? = awaitOrNull { textRecognizer.process(input) }
        val barcodes: List<Barcode> = awaitOrNull { barcodeScanner.process(input) }.orEmpty()

        val regions = mutableListOf<RedactionRegion>()

        faces.forEach { f ->
            val b = f.boundingBox
            regions += RedactionRegion(b.left, b.top, b.right, b.bottom, RedactionKind.FACE)
        }

        text?.textBlocks?.forEach { block ->
            block.lines.forEach { line ->
                val raw = line.text
                if (raw.isNotBlank() && shouldRedactTextLine(raw)) {
                    val b = line.boundingBox ?: return@forEach
                    regions += RedactionRegion(b.left, b.top, b.right, b.bottom, RedactionKind.TEXT)
                }
            }
        }

        barcodes.forEach { bc ->
            val b = bc.boundingBox ?: return@forEach
            regions += RedactionRegion(b.left, b.top, b.right, b.bottom, RedactionKind.BARCODE)
        }

        return regions
    }

    fun close() {
        runCatching { faceDetector.close() }
        runCatching { textRecognizer.close() }
        runCatching { barcodeScanner.close() }
    }

    private fun shouldRedactTextLine(line: String): Boolean {
        // 单行匹配：命中身份证/手机号/银行卡任一正则即脱敏。SensitiveRegexMatcher 的结果集非空即可。
        val hits = SensitiveRegexMatcher.match(ocrText = line, barcodeHit = false)
        return hits.isNotEmpty()
    }

    private suspend fun <T> awaitOrNull(block: () -> Task<T>): T? = try {
        suspendCancellableCoroutine<T?> { cont ->
            val task = block()
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            task.addOnFailureListener { if (cont.isActive) cont.resume(null) }
            task.addOnCanceledListener { if (cont.isActive) cont.resume(null) }
        }
    } catch (_: Throwable) {
        null
    }
}
