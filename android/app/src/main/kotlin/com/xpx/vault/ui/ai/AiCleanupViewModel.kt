package com.xpx.vault.ui.ai

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.ai.util.PhotoIdentity
import com.xpx.vault.domain.model.AiQualityRecord
import com.xpx.vault.domain.repo.AiAnalysisRepository
import com.xpx.vault.ui.vault.VaultStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 清理页 UI 状态：模糊/重复 + 扫描进度 + photoId→path 映射。
 */
data class AiCleanupUiState(
    val blurry: List<AiQualityRecord> = emptyList(),
    val duplicates: List<AiQualityRecord> = emptyList(),
    val scanning: Boolean = false,
    val scanTotal: Int = 0,
    val scanDone: Int = 0,
    /** photoId → 对应 Vault 图片绝对路径的映射，供 UI 缩略图渲染与点击跳转使用。 */
    val pathByPhotoId: Map<Long, String> = emptyMap(),
) {
    /** Summary / Tab 上的计数保持原有 "模糊 + 重复" 相加的口径，不做去重。 */
    val totalCount: Int get() = blurry.size + duplicates.size

    /** 空态判定：模糊/重复均为空且不在扫描中。 */
    val isEmpty: Boolean get() = blurry.isEmpty() && duplicates.isEmpty() && !scanning
}

@HiltViewModel
class AiCleanupViewModel @Inject constructor(
    private val app: Application,
    repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    private val pathMap = MutableStateFlow<Map<Long, String>>(emptyMap())

    init {
        refreshPathMap()
    }

    val uiState: StateFlow<AiCleanupUiState> = combine(
        repository.observeBlurry(),
        repository.observeDuplicates(),
        scanUseCase.progress,
        pathMap,
    ) { blurry, duplicates, progress, map ->
        AiCleanupUiState(
            blurry = blurry,
            duplicates = duplicates,
            scanning = progress.running,
            scanTotal = progress.total,
            scanDone = progress.done,
            pathByPhotoId = map,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiCleanupUiState(),
    )

    fun startScan() {
        viewModelScope.launch {
            scanUseCase.run()
            refreshPathMap()
        }
    }

    /** 遍历 Vault 所有图片，建立 photoId→path 映射，用于缩略图渲染与点击跳转反查。 */
    private fun refreshPathMap() {
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                VaultStore.listRecentPhotos(app, limit = Int.MAX_VALUE)
                    .associate { PhotoIdentity.fromPath(it.path) to it.path }
            }
            pathMap.value = map
        }
    }
}
