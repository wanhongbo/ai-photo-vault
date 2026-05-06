package com.xpx.vault.ui.ai

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpx.vault.ai.AiLocalScanUseCase
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.ai.util.PhotoIdentity
import com.xpx.vault.domain.model.AiTag
import com.xpx.vault.domain.repo.AiAnalysisRepository
import com.xpx.vault.ui.vault.VaultStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiClassifyCategoryCount(
    val category: ClassifyCategory,
    val count: Int,
    /** 该分类下的前 N 张照片的路径，用于缩略图预览。 */
    val previewPaths: List<String> = emptyList(),
)

data class AiClassifyUiState(
    val selected: ClassifyCategory? = null,
    val tags: List<AiTag> = emptyList(),
    val scanning: Boolean = false,
    /** photoId → 对应 Vault 图片绝对路径的映射，供 UI 缩略图渲染。 */
    val pathByPhotoId: Map<Long, String> = emptyMap(),
    /** 各分类的 tag 数量列表。 */
    val categoryCounts: List<AiClassifyCategoryCount> = emptyList(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AiClassifyViewModel @Inject constructor(
    private val app: Application,
    private val repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    private val selected = MutableStateFlow<ClassifyCategory?>(null)
    private val pathMap = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val categoryCounts = MutableStateFlow<List<AiClassifyCategoryCount>>(emptyList())

    init {
        // 先加载 pathMap，再查询分类数据，确保 previewPaths 能正确映射。
        viewModelScope.launch {
            refreshPathMap()
            refreshCategoryCounts()
        }
        // 进入分类页时也主动触发一次增量扫描：解锁后的全局触发是主途，这里是兵底。
        scanUseCase.requestScan()
    }

    val uiState: StateFlow<AiClassifyUiState> = combine(
        selected,
        selected.flatMapLatest { cat ->
            if (cat != null) repository.observeTagsByCategory(cat.name)
            else kotlinx.coroutines.flow.flowOf<List<AiTag>>(emptyList())
        },
        scanUseCase.progress,
        pathMap,
        categoryCounts,
    ) { cat, tags, progress, map, counts ->
        AiClassifyUiState(selected = cat, tags = tags, scanning = progress.running, pathByPhotoId = map, categoryCounts = counts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiClassifyUiState(),
    )

    fun select(category: ClassifyCategory) { selected.value = category }

    fun closeDetail() { selected.value = null }

    fun startScan() {
        viewModelScope.launch {
            // 手动 "扫描" 按钮 → 强制全量重扫，便于重新应用策略调整（例如新增了 Screenshot 启发式规则）。
            scanUseCase.run(force = true)
            // 扫描完成后刷新映射（可能有新图）和分类计数。
            refreshPathMap()
            refreshCategoryCounts()
        }
    }

    /** 遍历 Vault 所有图片，建立 photoId→path 映射，用于缩略图渲染反查。 */
    private suspend fun refreshPathMap() {
        val map = withContext(Dispatchers.IO) {
            VaultStore.listRecentPhotos(app, limit = Int.MAX_VALUE)
                .associate { PhotoIdentity.fromPath(it.path) to it.path }
        }
        pathMap.value = map
    }

    /** 查询各分类的 tag 数量。 */
    private suspend fun refreshCategoryCounts() {
        val counts = ClassifyCategory.values().map { cat ->
            withContext(Dispatchers.IO) {
                val count = repository.countPhotosByCategory(cat.name)
                // 取前 6 张照片作为预览缩略图
                val previewTags = repository.observeTagsByCategory(cat.name)
                    .first()
                    .take(6)
                val previewPaths = previewTags.mapNotNull { tag ->
                    pathMap.value[tag.photoId]
                }
                AiClassifyCategoryCount(cat, count, previewPaths)
            }
        }
        categoryCounts.value = counts
    }
}
