package com.xpx.vault.ai.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
                // 隐私脱敏是一次性交互，不跟扫描相比对延迟敯感：用 ACCURATE 模式求检测率。
                // 对自拍 / 微侧 / 低对比人脸比 FAST 全面过滤得低。
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                // minFaceSize 默认 0.1f（短边 10%），适当放宽到 0.08f，
                // 罩住远景小人脸、多人合照中的边缘人脸。
                .setMinFaceSize(0.08f)
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
        Log.d(TAG, "detect: bitmap=${bitmap.width}x${bitmap.height}")
        val faces: List<Face> = awaitOrNull { faceDetector.process(input) }.orEmpty()
        val text: Text? = awaitOrNull { textRecognizer.process(input) }
        val barcodes: List<Barcode> = awaitOrNull { barcodeScanner.process(input) }.orEmpty()
        Log.d(
            TAG,
            "detect done: faces=${faces.size} textBlocks=${text?.textBlocks?.size ?: 0} barcodes=${barcodes.size}",
        )

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
            task.addOnFailureListener {
                Log.w(TAG, "ml kit task failed", it)
                if (cont.isActive) cont.resume(null)
            }
            task.addOnCanceledListener { if (cont.isActive) cont.resume(null) }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "ml kit task exception", t)
        null
    }

    companion object {
        private const val TAG = "MlKitRedactionDetector"
    }
}
