package com.xpx.vault.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.domain.repo.AiAnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * AI 首页汇总 ViewModel。
 *
 * 职责：
 *  - 聚合「待处理敏感数 / 模糊废片数 / 重复照片数」作为首页建议卡片/功能入口的角标来源；
 *  - 所有计数以 Room Flow 为真相，UI 订阅 [uiState] 即可；
 *  - 本期（PR1）仅消费数据，不触发分析；真实分析由 PR2/PR3 的 Worker 写入 DB，UI 自动更新。
 */
@HiltViewModel
class AiHomeViewModel @Inject constructor(
    repo: AiAnalysisRepository,
) : ViewModel() {

    val uiState: StateFlow<AiHomeUiState> = combine(
        repo.observePendingSensitiveCount(),
        repo.observeBlurry().map { it.size },
        repo.observeDuplicates().map { it.size },
    ) { pending, blurry, duplicates ->
        AiHomeUiState(
            pendingSensitive = pending,
            blurryCount = blurry,
            duplicateCount = duplicates,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiHomeUiState(),
    )
}

data class AiHomeUiState(
    val pendingSensitive: Int = 0,
    val blurryCount: Int = 0,
    val duplicateCount: Int = 0,
) {
    val totalCleanup: Int get() = blurryCount + duplicateCount
}
