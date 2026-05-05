package com.xpx.vault.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.domain.model.AiSensitiveRecord
import com.xpx.vault.domain.repo.AiAnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiSensitiveUiState(
    val pending: List<AiSensitiveRecord> = emptyList(),
    val scanning: Boolean = false,
)

@HiltViewModel
class AiSensitiveReviewViewModel @Inject constructor(
    private val repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    val uiState: StateFlow<AiSensitiveUiState> = combine(
        repository.observePendingSensitive(),
        scanUseCase.progress,
    ) { pending, progress ->
        AiSensitiveUiState(pending = pending, scanning = progress.running)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiSensitiveUiState(),
    )

    fun startScan() { viewModelScope.launch { scanUseCase.run() } }

    fun markIgnored(id: Long) = viewModelScope.launch {
        repository.updateSensitiveStatus(id, status = "ignored")
    }

    fun markMoved(id: Long) = viewModelScope.launch {
        repository.updateSensitiveStatus(id, status = "moved")
    }
}
