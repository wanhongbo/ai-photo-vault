package com.xpx.vault.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.mlkit.MlKitRedactionDetector
import com.xpx.vault.ai.privacy.PrivacyRenderer
import com.xpx.vault.ai.privacy.RedactionRegion
import com.xpx.vault.ai.privacy.RedactionStyle
import com.xpx.vault.data.crypto.VaultCipher
import com.xpx.vault.ui.export.MediaExporter
import com.xpx.vault.ui.export.MediaShareHelper
import com.xpx.vault.ui.vault.VaultStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 隐私脱敏页 ViewModel。
 *
 * 状态机：Idle → Loading（解密原图 + ML Kit 检测）→ Ready（可切换样式 / 导出）→ Exporting → Exported。
 * 切换样式时只重渲染 preview，不重新跑检测。
 */
@HiltViewModel
class PrivacyRedactViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val detector: MlKitRedactionDetector,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyRedactUiState())
    val state: StateFlow<PrivacyRedactUiState> = _state.asStateFlow()

    private var originalBitmap: Bitmap? = null
    private var regions: List<RedactionRegion> = emptyList()

    fun load(path: String) {
        if (_state.value.loading || _state.value.ready) return
        _state.value = _state.value.copy(loading = true, errorMessage = null)
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { decryptOriginal(path) }
            if (bmp == null) {
                _state.value = _state.value.copy(loading = false, errorMessage = "decode_failed")
                return@launch
            }
            originalBitmap = bmp
            val ready = runCatching { detector.isReady() }.getOrDefault(false)
            val detected = if (ready) {
                runCatching { detector.detect(bmp) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            regions = detected
            _state.value = _state.value.copy(
                loading = false,
                ready = true,
                mlKitReady = ready,
                regionCount = detected.size,
                preview = renderPreview(bmp, detected, RedactionStyle.MOSAIC),
                style = RedactionStyle.MOSAIC,
                sourcePath = path,
            )
        }
    }

    fun selectStyle(style: RedactionStyle) {
        val src = originalBitmap ?: return
        if (_state.value.style == style) return
        viewModelScope.launch {
            val rendered = withContext(Dispatchers.Default) { renderPreview(src, regions, style) }
            _state.value = _state.value.copy(style = style, preview = rendered)
        }
    }

    fun exportRedacted(onResult: (Boolean, String) -> Unit) {
        val preview = _state.value.preview
        val path = _state.value.sourcePath
        if (preview == null || path == null) {
            onResult(false, "no_preview")
            return
        }
        if (_state.value.exporting) return
        _state.value = _state.value.copy(exporting = true)
        viewModelScope.launch {
            val baseName = "Redacted_" + File(path).nameWithoutExtension.takeLast(8) +
                "_" + System.currentTimeMillis()
            val outcome = MediaExporter.exportRedactedBitmap(appContext, preview, baseName)
            _state.value = _state.value.copy(exporting = false, exported = outcome is MediaExporter.ExportOutcome.Success)
            when (outcome) {
                is MediaExporter.ExportOutcome.Success -> onResult(true, outcome.displayName)
                is MediaExporter.ExportOutcome.Failure -> onResult(false, outcome.reason)
            }
        }
    }

    /**
     * 保存脱敏副本到安全相册（Vault）。
     *
     * 与 [exportRedacted] 不同：后者落系统 Pictures/AIPhotoVault/Redacted 明文；
     * 本方法把预览图压 JPEG 后用 VaultCipher 加密落盘到 vault_albums/Default/，
     * 以加密资产形式与原图共存于安全相册内。
     */
    fun saveToVault(onResult: (Boolean, String) -> Unit) {
        val preview = _state.value.preview
        val path = _state.value.sourcePath
        if (preview == null || path == null) {
            onResult(false, "no_preview")
            return
        }
        if (_state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val baseName = File(path).nameWithoutExtension.takeLast(12)
            val saved = withContext(Dispatchers.IO) {
                VaultStore.importRedactedBitmap(appContext, preview, baseName)
            }
            _state.value = _state.value.copy(saving = false, savedToVault = saved != null)
            onResult(saved != null, saved ?: "save_failed")
        }
    }

    /**
     * 分享脱敏后的预览图。
     *
     * 预览图以 JPEG 编码后写入 cacheDir/share_cache/，通过 FileProvider 临时授权给外部 App。
     * 不落 vault、不写系统相册，1h 内自动清理。
     */
    fun shareRedacted(chooserTitle: String, onResult: (Boolean, String) -> Unit) {
        val preview = _state.value.preview
        val path = _state.value.sourcePath
        if (preview == null || path == null) {
            onResult(false, "no_preview")
            return
        }
        if (_state.value.sharing) return
        _state.value = _state.value.copy(sharing = true)
        viewModelScope.launch {
            val baseName = "redacted_" + File(path).nameWithoutExtension.takeLast(8)
            val outcome = MediaShareHelper.shareBitmap(appContext, preview, baseName, chooserTitle)
            _state.value = _state.value.copy(sharing = false)
            when (outcome) {
                is MediaShareHelper.ShareOutcome.Success -> onResult(true, outcome.mimeType)
                is MediaShareHelper.ShareOutcome.Failure -> onResult(false, outcome.reason)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 注意：detector 是 @Singleton，进程内全局共享，这里绝对不能调 detector.close()：
        // 内部 faceDetector / textRecognizer / barcodeScanner 都是 by lazy 只初始化一次，
        // 一旦关闭再次使用会直接 Task 失败，导致“第二次进入共脱敏页不触发检测”的 bug。
        // Singleton 的 client 由进程回收时 GC尾，无需手动关闭。
        originalBitmap?.recycle()
        originalBitmap = null
    }

    private fun decryptOriginal(path: String): Bitmap? {
        return runCatching {
            val bytes = VaultCipher.get(appContext).decryptToByteArray(File(path))
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            // BitmapFactory 不会应用 EXIF Orientation，立屏拍摄的照片（Orientation=6/8）
            // 解出来是“侧躺”的 bitmap，ML Kit FaceDetector FAST 模式对旋转 90° 的脸容易漏检。
            // 这里读 EXIF 后实际旋转 Bitmap，保证下游 detect / render / display 都用正向图像。
            val degrees = readExifRotationDegrees(bytes)
            if (degrees == 0) raw else rotateBitmap(raw, degrees)
        }.getOrNull()
    }

    private fun readExifRotationDegrees(bytes: ByteArray): Int = runCatching {
        val exif = ExifInterface(java.io.ByteArrayInputStream(bytes))
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return try {
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            if (rotated !== src) src.recycle()
            rotated
        } catch (_: OutOfMemoryError) {
            src
        }
    }

    private fun renderPreview(
        src: Bitmap,
        regions: List<RedactionRegion>,
        style: RedactionStyle,
    ): Bitmap = PrivacyRenderer.render(src, regions, style)
}

data class PrivacyRedactUiState(
    val loading: Boolean = false,
    val ready: Boolean = false,
    val mlKitReady: Boolean = true,
    val regionCount: Int = 0,
    val preview: Bitmap? = null,
    val style: RedactionStyle = RedactionStyle.MOSAIC,
    val sourcePath: String? = null,
    val exporting: Boolean = false,
    val exported: Boolean = false,
    val saving: Boolean = false,
    val savedToVault: Boolean = false,
    val sharing: Boolean = false,
    val errorMessage: String? = null,
)
