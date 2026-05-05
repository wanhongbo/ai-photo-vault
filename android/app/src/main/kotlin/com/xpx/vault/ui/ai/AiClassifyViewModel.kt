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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiClassifyUiState(
    val selected: ClassifyCategory = ClassifyCategory.PORTRAIT,
    val tags: List<AiTag> = emptyList(),
    val scanning: Boolean = false,
    /** photoId → 对应 Vault 图片绝对路径的映射，供 UI 缩略图渲染。 */
    val pathByPhotoId: Map<Long, String> = emptyMap(),
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AiClassifyViewModel @Inject constructor(
    private val app: Application,
    private val repository: AiAnalysisRepository,
    private val scanUseCase: AiLocalScanUseCase,
) : ViewModel() {

    private val selected = MutableStateFlow(ClassifyCategory.PORTRAIT)
    private val pathMap = MutableStateFlow<Map<Long, String>>(emptyMap())

    init {
        refreshPathMap()
        // 进入分类页时也主动触发一次增量扫描：解锁后的全局触发是主途，这里是兵底。
        scanUseCase.requestScan()
    }

    val uiState: StateFlow<AiClassifyUiState> = combine(
        selected,
        selected.flatMapLatest { cat -> repository.observeTagsByCategory(cat.name) },
        scanUseCase.progress,
        pathMap,
    ) { cat, tags, progress, map ->
        AiClassifyUiState(selected = cat, tags = tags, scanning = progress.running, pathByPhotoId = map)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiClassifyUiState(),
    )

    fun select(category: ClassifyCategory) { selected.value = category }

    fun startScan() {
        viewModelScope.launch {
            // 手动 “扫描” 按钮 → 强制全量重扫，便于重新应用策略调整（例如新增了 Screenshot 启发式规则）。
            scanUseCase.run(force = true)
            // 扫描完成后刷新映射（可能有新图）。
            refreshPathMap()
        }
    }

    /** 遍历 Vault 所有图片，建立 photoId→path 映射，用于缩略图渲染反查。 */
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
