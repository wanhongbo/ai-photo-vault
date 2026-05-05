package com.xpx.vault.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.ai.AiScanProgress
import com.xpx.vault.domain.repo.AiAnalysisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * AI 首页汇总 ViewModel。
 *
 * 职责：
 *  - 聚合「待处理敏感数 / 模糊废片数 / 重复照片数」作为首页角标来源；
 *  - combine 扫描进度 + 忽略偏好，派生出顶部卡片的 [AiSuggestion] 状态；
 *  - 首次进入（从未扫过）自动触发一次增量扫描，并在每轮扫描结束时记录完成时间戳。
 */
@HiltViewModel
class AiHomeViewModel @Inject constructor(
    repo: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
    private val snoozePrefs: AiSuggestSnoozePrefs,
) : ViewModel() {

    val uiState: StateFlow<AiHomeUiState> = combine(
        repo.observePendingSensitiveCount(),
        repo.observeBlurry().map { it.size },
        repo.observeDuplicates().map { it.size },
        scanUseCase.progress,
        snoozePrefs.versionFlow,
    ) { pending, blurry, duplicate, progress, _ ->
        derive(pending, blurry, duplicate, progress)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiHomeUiState(),
    )

    init {
        // 从未扫过时自动启动扫描：Idle → Scanning 的过渡由真实进度驱动，用户无需手动点击。
        // requestScan 内部由 mutex 保护，即使 MainActivity 解锁后也触发过，这里重复调用也是幂等。
        if (!snoozePrefs.hasEverScanned()) {
            scanUseCase.requestScan()
        }
        // 监听扫描从 running 切回空闲，记录首次扫描完成时间戳，后续即可进入 AllClear 态。
        viewModelScope.launch {
            var prevRunning = false
            scanUseCase.progress.collect { p ->
                if (prevRunning && !p.running) {
                    snoozePrefs.markScanCompleted()
                }
                prevRunning = p.running
            }
        }
    }

    /** 用户点击 AllClear 卡片上的「重新扫描」按钮。 */
    fun onRescan() {
        scanUseCase.requestScan(force = true)
    }

    /** 用户点击「忽略 7 天」，下次派生时该类型会被跳过。 */
    fun onSnooze(kind: AiSuggestSnoozePrefs.Kind) {
        snoozePrefs.snooze(kind)
    }

    private fun derive(
        pendingSensitive: Int,
        blurry: Int,
        duplicate: Int,
        progress: AiScanProgress,
    ): AiHomeUiState {
        val cleanup = blurry + duplicate
        val hasEverScanned = snoozePrefs.hasEverScanned()
        val suggestion: AiSuggestion = when {
            progress.running ->
                AiSuggestion.Scanning(done = progress.done, total = progress.total)
            pendingSensitive > 0 && !snoozePrefs.isSnoozed(AiSuggestSnoozePrefs.Kind.SENSITIVE) ->
                AiSuggestion.Sensitive(count = pendingSensitive, cleanupCount = cleanup)
            cleanup > 0 && !snoozePrefs.isSnoozed(AiSuggestSnoozePrefs.Kind.CLEANUP) ->
                AiSuggestion.Cleanup(count = cleanup)
            hasEverScanned -> AiSuggestion.AllClear
            else -> AiSuggestion.Idle
        }
        return AiHomeUiState(
            suggestion = suggestion,
            pendingSensitive = pendingSensitive,
            blurryCount = blurry,
            duplicateCount = duplicate,
        )
    }
}

data class AiHomeUiState(
    val suggestion: AiSuggestion = AiSuggestion.Idle,
    val pendingSensitive: Int = 0,
    val blurryCount: Int = 0,
    val duplicateCount: Int = 0,
) {
    val totalCleanup: Int get() = blurryCount + duplicateCount
}
