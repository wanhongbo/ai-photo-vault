package com.xpx.vault.ui.ai

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.ai.util.PhotoIdentity
import com.xpx.vault.domain.model.AiSensitiveRecord
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

data class AiSensitiveUiState(
    val pending: List<AiSensitiveRecord> = emptyList(),
    val scanning: Boolean = false,
    /** photoId → 对应 Vault 图片绝对路径的映射，供宫格缩略图渲染与点击跳转使用。 */
    val pathByPhotoId: Map<Long, String> = emptyMap(),
)

@HiltViewModel
class AiSensitiveReviewViewModel @Inject constructor(
    private val app: Application,
    private val repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    private val pathMap = MutableStateFlow<Map<Long, String>>(emptyMap())

    init {
        refreshPathMap()
    }

    val uiState: StateFlow<AiSensitiveUiState> = combine(
        repository.observePendingSensitive(),
        scanUseCase.progress,
        pathMap,
    ) { pending, progress, map ->
        AiSensitiveUiState(
            pending = pending,
            scanning = progress.running,
            pathByPhotoId = map,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiSensitiveUiState(),
    )

    fun startScan() {
        viewModelScope.launch {
            scanUseCase.run()
            refreshPathMap()
        }
    }

    fun markIgnored(id: Long) = viewModelScope.launch {
        repository.updateSensitiveStatus(id, status = "ignored")
    }

    fun markMoved(id: Long) = viewModelScope.launch {
        repository.updateSensitiveStatus(id, status = "moved")
    }

    /** 遍历 Vault 所有图片建立 photoId→path 映射，与 AiCleanupViewModel 同构。 */
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
