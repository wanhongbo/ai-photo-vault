package com.xpx.vault.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.ai.AiScanProgress
import com.xpx.vault.ai.core.SensitiveKind
import com.xpx.vault.domain.model.AiSensitiveRecord
import com.xpx.vault.domain.repo.AiAnalysisRepository
import com.xpx.vault.ui.vault.VaultStore
import com.xpx.vault.ui.vault.isVaultImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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
    @ApplicationContext private val app: Context,
    private val repo: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
    private val snoozePrefs: AiSuggestSnoozePrefs,
) : ViewModel() {
    private val vaultStats = MutableStateFlow(AiVaultStats())

    val uiState: StateFlow<AiHomeUiState> = combine(
        repo.observePendingSensitive(),
        repo.observeBlurry().map { it.size },
        repo.observeDuplicates().map { it.size },
        scanUseCase.progress,
        combine(vaultStats, snoozePrefs.versionFlow) { stats, _ -> stats },
    ) { pendingSensitive, blurry, duplicate, progress, stats ->
        derive(pendingSensitive, blurry, duplicate, progress, stats)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiHomeUiState(),
    )

    init {
        refreshVaultStats()
        // 监听扫描从 running 切回空闲，记录首次扫描完成时间戳，后续即可进入 AllClear 态。
        viewModelScope.launch {
            var prevRunning = false
            scanUseCase.progress.collect { p ->
                if (prevRunning && !p.running) {
                    snoozePrefs.markScanCompleted()
                    refreshVaultStats()
                }
                prevRunning = p.running
            }
        }
    }

    /** 用户点击 AllClear 卡片上的「重新扫描」按钮。 */
    fun onRescan() {
        // 主动重扫 = 用户要求重新评估，顺手 revoke 之前的忽略，
        // 避免“重扫检出新重复却被旧 snooze 隔断、卡片误示一切良好”。
        snoozePrefs.clearAllSnoozes()
        scanUseCase.requestScan(force = true)
    }

    fun onStartScan() {
        snoozePrefs.clearAllSnoozes()
        refreshVaultStats()
        scanUseCase.requestScan()
    }

    fun onCancelScan() {
        scanUseCase.cancelScan()
    }

    /** 用户点击「忽略 7 天」，下次派生时该类型会被跳过。 */
    fun onSnooze(kind: AiSuggestSnoozePrefs.Kind) {
        snoozePrefs.snooze(kind)
    }

    private fun derive(
        pendingSensitive: List<AiSensitiveRecord>,
        blurry: Int,
        duplicate: Int,
        progress: AiScanProgress,
        stats: AiVaultStats,
    ): AiHomeUiState {
        val cleanup = blurry + duplicate
        val pendingSensitiveCount = pendingSensitive.map { it.photoId }.distinct().size
        val locationRiskCount = pendingSensitive
            .filter { it.kind == SensitiveKind.LOCATION_METADATA.name }
            .map { it.photoId }
            .distinct()
            .size
        val hasEverScanned = snoozePrefs.hasEverScanned()
        val suggestion: AiSuggestion = when {
            progress.running ->
                AiSuggestion.Scanning(done = progress.done, total = progress.total, cancelling = progress.cancelling)
            stats.totalCount == 0 ->
                AiSuggestion.Empty
            pendingSensitiveCount > 0 && !snoozePrefs.isSnoozed(AiSuggestSnoozePrefs.Kind.SENSITIVE) ->
                AiSuggestion.Sensitive(
                    count = pendingSensitiveCount,
                    locationRiskCount = locationRiskCount,
                    cleanupCount = cleanup,
                )
            cleanup > 0 && !snoozePrefs.isSnoozed(AiSuggestSnoozePrefs.Kind.CLEANUP) ->
                AiSuggestion.Cleanup(count = cleanup)
            hasEverScanned && stats.scannedCount >= stats.imageCount -> AiSuggestion.AllClear
            else -> AiSuggestion.Idle
        }
        return AiHomeUiState(
            suggestion = suggestion,
            totalCount = stats.totalCount,
            scannedCount = stats.scannedCount,
            imageCount = stats.imageCount,
            pendingSensitive = pendingSensitiveCount,
            locationRiskCount = locationRiskCount,
            blurryCount = blurry,
            duplicateCount = duplicate,
        )
    }

    private fun refreshVaultStats() {
        viewModelScope.launch {
            val snapshot = runCatching { VaultStore.loadSnapshot(app) }.getOrNull()
            val photos = runCatching { VaultStore.listRecentPhotos(app, limit = Int.MAX_VALUE) }.getOrDefault(emptyList())
            val imageCount = photos.count { isVaultImage(it.path) }
            val scannedCount = runCatching { repo.listAllScannedPhotoIds().size }.getOrDefault(0)
            vaultStats.value = AiVaultStats(
                totalCount = snapshot?.totalCount ?: photos.size,
                imageCount = imageCount,
                scannedCount = scannedCount.coerceAtMost(imageCount),
            )
        }
    }
}

private data class AiVaultStats(
    val totalCount: Int = 0,
    val imageCount: Int = 0,
    val scannedCount: Int = 0,
)

data class AiHomeUiState(
    val suggestion: AiSuggestion = AiSuggestion.Idle,
    val totalCount: Int = 0,
    val scannedCount: Int = 0,
    val imageCount: Int = 0,
    val pendingSensitive: Int = 0,
    val locationRiskCount: Int = 0,
    val blurryCount: Int = 0,
    val duplicateCount: Int = 0,
) {
    val totalCleanup: Int get() = blurryCount + duplicateCount
}
