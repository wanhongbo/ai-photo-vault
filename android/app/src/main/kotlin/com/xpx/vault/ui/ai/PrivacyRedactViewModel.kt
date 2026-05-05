package com.xpx.vault.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.mlkit.MlKitRedactionDetector
import com.xpx.vault.ai.privacy.PrivacyRenderer
import com.xpx.vault.ai.privacy.RedactionRegion
import com.xpx.vault.ai.privacy.RedactionStyle
import com.xpx.vault.data.crypto.VaultCipher
import com.xpx.vault.ui.export.MediaExporter
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

    override fun onCleared() {
        super.onCleared()
        runCatching { detector.close() }
        originalBitmap?.recycle()
        originalBitmap = null
    }

    private fun decryptOriginal(path: String): Bitmap? {
        return runCatching {
            val bytes = VaultCipher.get(appContext).decryptToByteArray(File(path))
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
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
    val errorMessage: String? = null,
)
