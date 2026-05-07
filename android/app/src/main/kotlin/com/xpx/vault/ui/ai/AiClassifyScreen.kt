package com.xpx.vault.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.DrawableRes
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xpx.vault.R
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize

/**
 * 智能分类页：按 6 大类浏览 AI 识别到的照片。
 *
 * 新版：垂直分类卡片列表（类似保险箱页的相册列表样式），点击卡片进入对应分类的宫格详情。
 */
@Composable
fun AiClassifyScreen(
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit = {},
    viewModel: AiClassifyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = "智能分类", onBack = onBack)
        ClassifySummary(scanning = state.scanning, onScan = viewModel::startScan)

        if (state.categoryCounts.isEmpty() && !state.scanning) {
            ClassifyEmptyState(scanning = state.scanning)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.categoryCounts, key = { it.category.name }) { item ->
                    if (item.count > 0) {
                        ClassifyCategorySection(
                            category = item.category,
                            count = item.count,
                            previewPaths = item.previewPaths,
                            onOpenPhoto = { path -> onOpenPhoto(path) },
                            onViewMore = { viewModel.select(item.category) },
                        )
                    }
                }
            }
        }
    }

    // 当选中某个分类时，显示宫格详情（覆盖在分类列表之上）
    if (state.selected != null && state.tags.isNotEmpty()) {
        ClassifyCategoryDetail(
            category = state.selected!!,
            tags = state.tags,
            pathByPhotoId = state.pathByPhotoId,
            onOpenPhoto = onOpenPhoto,
            onBack = viewModel::closeDetail,
        )
    }
}

@Composable
private fun ClassifySummary(scanning: Boolean, onScan: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UiColors.Ai.iconBgWhite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_layers),
                contentDescription = null,
                tint = UiColors.Ai.dedupBar,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "\u6309\u5185\u5bb9\u7c7b\u522b\u6d4f\u89c8",
                color = Color(0xFFF0F4FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "AI \u4e3a\u4f60\u7684\u4fdd\u9669\u7bb1\u7167\u7247\u81ea\u52a8\u6253\u6807",
                color = Color(0xFF8A8A90),
                fontSize = 12.sp,
            )
        }
        val interaction = rememberFeedbackInteractionSource()
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(UiColors.Ai.execBtnBg)
                .pressFeedback(interaction)
                .throttledClickable(interactionSource = interaction, indication = null, onClick = onScan)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_zap),
                contentDescription = null,
                tint = UiColors.Ai.execBtnText,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (scanning) "\u626b\u63cf\u4e2d" else "\u626b\u63cf",
                color = UiColors.Ai.execBtnText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ClassifyCategorySection(
    category: ClassifyCategory,
    count: Int,
    previewPaths: List<String>,
    onOpenPhoto: (String) -> Unit,
    onViewMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // 标题行
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = labelFor(category),
                color = UiColors.Home.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "查看更多",
                color = UiColors.Home.navItemActive,
                fontSize = 14.sp,
                modifier = Modifier.throttledClickable(onClick = onViewMore),
            )
        }
        // 缩略图宫格：≤3 张最多 1 行、最多 3 列；>3 张最多 2 行、最多 3 列（最多 6 张预览）
        val maxPreview = if (count <= 3) minOf(count, 3) else minOf(count, 6)
        val displayPaths = previewPaths.take(maxPreview)
        val columns = if (count <= 3) displayPaths.size.coerceIn(1, 3) else 3
        val gridHeight = if (count <= 3) {
            UiSize.homeThumbSize
        } else {
            UiSize.homeThumbSize * 2 + UiSize.homeGridGap
        }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columns),
            modifier = Modifier
                .height(gridHeight)
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
            verticalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
        ) {
            items(displayPaths, key = { it }) { path ->
                PhotoThumb(path = path, onClick = { onOpenPhoto(path) })
            }
        }
    }
}

@Composable
private fun PhotoThumb(
    path: String,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Box(
        modifier = Modifier
            .size(UiSize.homeThumbSize)
            .clip(RoundedCornerShape(UiRadius.homeThumb))
            .background(UiColors.Home.emptyIconBg)
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        VaultProgressiveImage(
            path = path,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            thumbnailMaxPx = 320,
            showVideoIndicator = true,
        )
    }
}

@Composable
private fun ClassifyCategoryDetail(
    category: ClassifyCategory,
    tags: List<com.xpx.vault.domain.model.AiTag>,
    pathByPhotoId: Map<Long, String>,
    onOpenPhoto: (String) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 半透明背景
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC000000))
                .throttledClickable(onClick = onBack),
        )
        // 宫格内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(UiColors.Home.bgBottom)
                .safeDrawingPadding(),
        ) {
            AppTopBar(title = labelFor(category), onBack = onBack)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(tags, key = { it.id }) { tag ->
                    val path = pathByPhotoId[tag.photoId]
                    ClassifyGridCell(
                        tag = tag,
                        path = path,
                        onClick = if (path != null) ({ onOpenPhoto(path) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassifyGridCell(
    tag: com.xpx.vault.domain.model.AiTag,
    path: String?,
    onClick: (() -> Unit)? = null,
) {
    val interaction = rememberFeedbackInteractionSource()
    val clickModifier = if (onClick != null) {
        Modifier
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Ai.featureCardBg)
            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(12.dp))
            .then(clickModifier),
    ) {
        if (path != null) {
            VaultProgressiveImage(
                path = path,
                modifier = Modifier.fillMaxSize(),
                thumbnailMaxPx = 360,
            )
        } else {
            // 映射缺失（已删除或未同步）：用占位显示信息。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                Text(
                    text = tag.label,
                    color = Color(0xFFF0F4FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "photoId=${tag.photoId}",
                    color = Color(0xFF8A8A90),
                    fontSize = 11.sp,
                )
            }
        }
        // 底部半透明渐层 + label 叠加
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = "${tag.label}  ${"%.0f".format(tag.confidence * 100)}%",
                color = Color(0xFFF0F4FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ClassifyEmptyState(scanning: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (scanning) "\u626b\u63cf\u4e2d\u2026" else "\u8be5\u5206\u7c7b\u6682\u65e0\u6570\u636e",
                color = Color(0xFFF0F4FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "\u70b9\u51fb\u4e0a\u65b9\u300c\u626b\u63cf\u300d\u6309\u94ae\uff0cAI \u5c06\u4e3a\u4f60\u7684\u7167\u7247\u81ea\u52a8\u6253\u6807\u5206\u7c7b\u3002",
                color = Color(0xFF8A8A90),
                fontSize = 13.sp,
            )
        }
    }
}

private fun labelFor(category: ClassifyCategory): String = when (category) {
    ClassifyCategory.SCREENSHOT -> "截图"
    ClassifyCategory.ID_CARD -> "证件"
    ClassifyCategory.PORTRAIT -> "人像"
    ClassifyCategory.LANDSCAPE -> "风景"
    ClassifyCategory.FOOD -> "美食"
    ClassifyCategory.DOCUMENT -> "文档"
    ClassifyCategory.OTHER -> "其他"
}

@DrawableRes
private fun iconForCategory(category: ClassifyCategory): Int = when (category) {
    // TODO: 后续接入各分类专属 icon，现统一用 layers 图标
    else -> R.drawable.ic_ai_layers
}
