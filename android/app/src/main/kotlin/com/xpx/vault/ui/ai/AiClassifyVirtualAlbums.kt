package com.xpx.vault.ui.ai

import android.content.Context
import androidx.annotation.StringRes
import com.xpx.vault.R
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.ai.util.PhotoIdentity
import com.xpx.vault.billing.AiAnalysisRepoProvider
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class AiClassifyVirtualAlbum(
    val category: ClassifyCategory,
    @StringRes val titleRes: Int,
    val coverPath: String?,
    val photoCount: Int,
)

suspend fun loadAiClassifyVirtualAlbums(context: Context): List<AiClassifyVirtualAlbum> =
    withContext(Dispatchers.IO) {
        val repository = AiAnalysisRepoProvider.get(context) ?: return@withContext emptyList()
        val pathByPhotoId = VaultStore.listRecentPhotos(context, limit = Int.MAX_VALUE)
            .associate { PhotoIdentity.fromPath(it.path) to it.path }

        ClassifyCategory.values().mapNotNull { category ->
            val count = repository.countPhotosByCategory(category.name)
            if (count <= 0) return@mapNotNull null

            val coverPath = repository.observeTagsByCategory(category.name)
                .first()
                .asSequence()
                .mapNotNull { tag -> pathByPhotoId[tag.photoId] }
                .firstOrNull()

            AiClassifyVirtualAlbum(
                category = category,
                titleRes = titleResForAiClassifyCategory(category),
                coverPath = coverPath,
                photoCount = count,
            )
        }
    }

@StringRes
fun titleResForAiClassifyCategory(category: ClassifyCategory): Int = when (category) {
    ClassifyCategory.SCREENSHOT -> R.string.ai_classify_screenshot
    ClassifyCategory.ID_CARD -> R.string.ai_classify_id_card
    ClassifyCategory.PORTRAIT -> R.string.ai_classify_portrait
    ClassifyCategory.LANDSCAPE -> R.string.ai_classify_scenery
    ClassifyCategory.FOOD -> R.string.ai_classify_food
    ClassifyCategory.DOCUMENT -> R.string.ai_classify_document
    ClassifyCategory.OTHER -> R.string.ai_classify_other
}
