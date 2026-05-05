package com.xpx.vault.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.domain.model.AiTag
import com.xpx.vault.domain.repo.AiAnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiClassifyUiState(
    val selected: ClassifyCategory = ClassifyCategory.PORTRAIT,
    val tags: List<AiTag> = emptyList(),
    val scanning: Boolean = false,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AiClassifyViewModel @Inject constructor(
    private val repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    private val selected = MutableStateFlow(ClassifyCategory.PORTRAIT)

    val uiState: StateFlow<AiClassifyUiState> = combine(
        selected,
        selected.flatMapLatest { cat -> repository.observeTagsByCategory(cat.name) },
        scanUseCase.progress,
    ) { cat, tags, progress ->
        AiClassifyUiState(selected = cat, tags = tags, scanning = progress.running)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiClassifyUiState(),
    )

    fun select(category: ClassifyCategory) { selected.value = category }
    fun startScan() { viewModelScope.launch { scanUseCase.run() } }
}
