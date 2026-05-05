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
    /** 是否正在执行一键清理。 */
    val cleaning: Boolean = false,
    /** 上次清理的结果条数，用于 Toast 提示。-1 表示无。 */
    val lastCleanedCount: Int = -1,
) {
    /** Summary / Tab 上的计数保持原有 "模糊 + 重复" 相加的口径，不做去重。 */
    val totalCount: Int get() = blurry.size + duplicates.size

    /** 空态判定：模糊/重复均为空且不在扫描中。 */
    val isEmpty: Boolean get() = blurry.isEmpty() && duplicates.isEmpty() && !scanning

    /**
     * 冗余照片数：全部模糊 + 重复组中非最清晰张。
     * 用于 UI 按钮文案展示。
     */
    val redundantCount: Int get() {
        val blurryIds = blurry.map { it.photoId }.toSet()
        // observeDuplicates() 的 SQL 是 WHERE is_duplicate=1，本身就不包含代表张（代表张 is_duplicate=false），
        // 所以 duplicates 里每一条都是应清的“非代表”，直接全算入冗余。
        // （旧逻辑 groupBy + drop(1) 会每簇少算 1 张，导致按钮文案偏少 + 清不干净。）
        val dupRedundantIds = duplicates.map { it.photoId }.toSet()
        return (blurryIds + dupRedundantIds).size
    }
}

@HiltViewModel
class AiCleanupViewModel @Inject constructor(
    private val app: Application,
    private val repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    private val pathMap = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _cleaning = MutableStateFlow(false)
    private val _lastCleanedCount = MutableStateFlow(-1)

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
            cleaning = _cleaning.value,
            lastCleanedCount = _lastCleanedCount.value,
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

    /**
     * 一键清理冗余照片：
     *  - 全部模糊照片 → 移入垃圾桶
     *  - 每个重复组的非代表张全部 → 移入垃圾桶（代表张 is_duplicate=false，不在 duplicates 里）
         *  - 每删一张照片，同时原子清除其 AI 表的全部遗留（phash/quality/tag/sensitive），
         *    避免下次扫描时产生孤儿记录；
         *  - 清理完主动触发一次重扫，让残留簇重新聚类，AI Tab 卡片状态即时刷新。
     */
    fun cleanupRedundant() {
        viewModelScope.launch {
            _cleaning.value = true
            val state = uiState.value
            val blurryIds = state.blurry.map { it.photoId }.toSet()
            // duplicates Flow 已不包含代表张，直接全量列为待清。
            val dupRedundantIds = state.duplicates.map { it.photoId }.toSet()
            val toDelete = (blurryIds + dupRedundantIds)
            var count = 0
            withContext(Dispatchers.IO) {
                for (id in toDelete) {
                    val path = state.pathByPhotoId[id] ?: continue
                    val ok = VaultStore.deletePhoto(app, path)
                    if (ok) {
                        // 一次性清 4 表 AI 遗留，避免孤儿 phash 残留让下次聚类再次标错。
                        repository.purgePhoto(id)
                        count++
                    }
                }
            }
            _lastCleanedCount.value = count
            _cleaning.value = false
            refreshPathMap()
            // 清理后触发一次重扫：残留的簇可能因成员数降到 <2 而解散，markDuplicates 会清理旧标记，
            // 避免 AI Tab 在 duplicates Flow 更新时还挂着孤立的 is_duplicate=1 残跡。
            scanUseCase.requestScan(force = true)
        }
    }

    /** 消费一次 lastCleanedCount 后重置，避免重复 Toast。 */
    fun consumeCleanedCount() {
        _lastCleanedCount.value = -1
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
