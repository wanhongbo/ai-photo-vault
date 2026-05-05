package com.xpx.vault.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.domain.model.AiQualityRecord
import com.xpx.vault.domain.repo.AiAnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 清理页 UI 状态：模糊/重复 + 扫描进度。
 */
data class AiCleanupUiState(
    val blurry: List<AiQualityRecord> = emptyList(),
    val duplicates: List<AiQualityRecord> = emptyList(),
    val scanning: Boolean = false,
    val scanTotal: Int = 0,
    val scanDone: Int = 0,
) {
    val totalCount: Int get() = blurry.size + duplicates.size
    val isEmpty: Boolean get() = blurry.isEmpty() && duplicates.isEmpty() && !scanning
}

@HiltViewModel
class AiCleanupViewModel @Inject constructor(
    repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    val uiState: StateFlow<AiCleanupUiState> = combine(
        repository.observeBlurry(),
        repository.observeDuplicates(),
        scanUseCase.progress,
    ) { blurry, duplicates, progress ->
        AiCleanupUiState(
            blurry = blurry,
            duplicates = duplicates,
            scanning = progress.running,
            scanTotal = progress.total,
            scanDone = progress.done,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiCleanupUiState(),
    )

    fun startScan() {
        viewModelScope.launch { scanUseCase.run() }
    }
}
