package com.xpx.vault.ai.mlkit

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.xpx.vault.ai.algo.ClassifyCategoryMapper
import com.xpx.vault.ai.algo.SensitiveRegexMatcher
import com.xpx.vault.ai.core.AiAnalysisResult
import com.xpx.vault.ai.core.AiEngine
import com.xpx.vault.ai.core.AiSensitiveHit
import com.xpx.vault.ai.core.AiTag
import com.xpx.vault.ai.core.AnalyzerCapability
import com.xpx.vault.ai.core.ImageAnalyzer
import com.xpx.vault.ai.core.SensitiveKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ML Kit 聚合分析器：
 *  - Image Labeler 产出分类标签（经 [ClassifyCategoryMapper] 归一到 6 大类）；
 *  - Face Detection 检测清晰人脸 → [SensitiveKind.FACE_CLEAR]；
 *  - Text Recognition 做 OCR，结合 [SensitiveRegexMatcher] 命中 ID/银行卡/手机号；
 *  - Barcode Scanning 命中任意条码 → [SensitiveKind.QR_CODE]。
 *
 * 所有模型按需下载（play-services variant），首次调用 ML Kit 时由 GMS 在后台拉取。
 * 当 GMS 不可用时 [isReady] 返回 false，上层可跳过此分析器（保留 LOCAL_ALGO 兜底）。
 */
@Singleton
class MlKitAnalyzer @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ImageAnalyzer {

    override val engine: AiEngine = AiEngine.MLKIT

    override val capability: AnalyzerCapability = AnalyzerCapability(
        canClassify = true,
        canDetectSensitive = true,
        canDetectFace = true,
        canRecognizeText = true,
        canDecodeBarcode = true,
    )

    private val labeler: ImageLabeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }
    private val faceDetector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build(),
        )
    }
    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val barcodeScanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient()
    }

    override suspend fun isReady(): Boolean {
        val avail = runCatching {
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(appContext)
        }.getOrDefault(-1)
        return avail == ConnectionResult.SUCCESS
    }

    override suspend fun analyze(photoId: Long, bitmap: Bitmap): AiAnalysisResult {
        val input = InputImage.fromBitmap(bitmap, 0)

        val labels: List<ImageLabel> = awaitOrNull { labeler.process(input) }.orEmpty()
        val faces: List<Face> = awaitOrNull { faceDetector.process(input) }.orEmpty()
        val ocrText: Text? = awaitOrNull { textRecognizer.process(input) }
        val barcodes: List<Barcode> = awaitOrNull { barcodeScanner.process(input) }.orEmpty()

        // ---- tags ----
        val tags = labels.map { label ->
            AiTag(
                label = label.text,
                category = ClassifyCategoryMapper.map(label.text),
                confidence = label.confidence,
                source = AiEngine.MLKIT,
            )
        }

        // ---- sensitive ----
        val kinds = SensitiveRegexMatcher.match(
            ocrText = ocrText?.text.orEmpty(),
            barcodeHit = barcodes.isNotEmpty(),
        ).toMutableSet()

        // 清晰人脸：任意一张脸 bbox 短边 >= 128px（基于缩略图尺度）。
        val hasClearFace = faces.any { f ->
            val w = f.boundingBox.width()
            val h = f.boundingBox.height()
            minOf(w, h) >= 128
        }
        if (hasClearFace) kinds += SensitiveKind.FACE_CLEAR

        val sensitive = kinds.map { AiSensitiveHit(kind = it, confidence = 0.85f) }

        return AiAnalysisResult(
            photoId = photoId,
            engineVersion = ENGINE_VERSION,
            tags = tags,
            sensitive = sensitive,
        )
    }

    override fun close() {
        runCatching { labeler.close() }
        runCatching { faceDetector.close() }
        runCatching { textRecognizer.close() }
        runCatching { barcodeScanner.close() }
    }

    /**
     * 简易 Task → suspend 包装。失败 / 取消一律返回 null，让上层宽容处理。
     */
    private suspend fun <T> awaitOrNull(block: () -> Task<T>): T? = try {
        suspendCancellableCoroutine<T?> { cont ->
            val task = block()
            task.addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
            task.addOnFailureListener { if (cont.isActive) cont.resume(null) }
            task.addOnCanceledListener { if (cont.isActive) cont.resume(null) }
        }
    } catch (t: Throwable) {
        null
    }

    companion object {
        const val ENGINE_VERSION = "mlkit-v1"
    }
}
