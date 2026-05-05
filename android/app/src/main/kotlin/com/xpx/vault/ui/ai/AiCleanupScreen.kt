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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.xpx.vault.R
import com.xpx.vault.domain.model.AiQualityRecord
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors

/**
 * 垃圾清理页：展示 AI 识别出的模糊/重复照片，供用户批量清理。
 *
 * 当前版本：
 *   - 提供三个 Tab（全部 / 模糊 / 重复）
 *   - 点击 Tab 下方显示对应分类的 3 列缩略图宫格
 *   - 点击缩略图跳转到图片预览页
 *   - 空态 + 扫描按钮；消费 [AiCleanupViewModel] 的 Flow与 photoId→path 映射
 */
@Composable
fun AiCleanupScreen(
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit = {},
    viewModel: AiCleanupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(CleanupTab.ALL) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 清理完成后弹 Toast
    LaunchedEffect(state.lastCleanedCount) {
        if (state.lastCleanedCount >= 0) {
            Toast.makeText(context, "已清理 ${state.lastCleanedCount} 张冗余照片", Toast.LENGTH_SHORT).show()
            viewModel.consumeCleanedCount()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = "垃圾清理", onBack = onBack)

        CleanupSummary(
            state = state,
            onScan = { viewModel.startScan() },
            onCleanup = { showConfirmDialog = true },
        )

        CleanupTabRow(
            selected = selectedTab,
            counts = Triple(state.totalCount, state.blurry.size, state.duplicates.size),
            onSelect = { selectedTab = it },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isEmpty -> EmptyState()
                else -> CleanupGrid(
                    tab = selectedTab,
                    blurry = state.blurry,
                    duplicates = state.duplicates,
                    pathMap = state.pathByPhotoId,
                    onOpenPhoto = onOpenPhoto,
                )
            }
        }
    }

    // 确认弹窗
    AppDialog(
        show = showConfirmDialog,
        title = "一键清理",
        message = "将把 ${state.redundantCount} 张冗余照片（模糊 + 重复组的非最清晰照片）移入垃圾桶，30 天后自动彻底删除。",
        confirmText = "确认清理",
        dismissText = "取消",
        onConfirm = {
            showConfirmDialog = false
            viewModel.cleanupRedundant()
        },
        onDismiss = { showConfirmDialog = false },
        confirmVariant = AppButtonVariant.PRIMARY,
    )
}

private enum class CleanupTab(val labelZh: String) {
    ALL("\u5168\u90e8"),
    BLURRY("\u6a21\u7cca"),
    DUPLICATE("\u91cd\u590d"),
}

@Composable
private fun CleanupSummary(state: AiCleanupUiState, onScan: () -> Unit, onCleanup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
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
                    painter = painterResource(R.drawable.ic_ai_copy),
                    contentDescription = null,
                    tint = UiColors.Ai.dedupBar,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = "检测到 ${state.totalCount} 张可清理照片",
                    color = Color(0xFFF0F4FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "模糊 ${state.blurry.size}  ·  重复 ${state.duplicates.size}",
                    color = Color(0xFF8A8A90),
                    fontSize = 12.sp,
                )
            }
        }
        // 扫描 + 清理并排
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val scanInteraction = rememberFeedbackInteractionSource()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UiColors.Ai.execBtnBg)
                    .pressFeedback(scanInteraction)
                    .throttledClickable(
                        interactionSource = scanInteraction,
                        indication = null,
                        onClick = onScan,
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_zap),
                    contentDescription = null,
                    tint = UiColors.Ai.execBtnText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.scanning) "正在扫描…" else "立即扫描",
                    color = UiColors.Ai.execBtnText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            // 一键清理按钮（仅有冗余照片时可点击）
            if (state.redundantCount > 0 && !state.cleaning) {
                val cleanInteraction = rememberFeedbackInteractionSource()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE04848))
                        .pressFeedback(cleanInteraction)
                        .throttledClickable(
                            interactionSource = cleanInteraction,
                            indication = null,
                            onClick = onCleanup,
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ai_copy),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "清理 ${state.redundantCount} 张",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanupTabRow(
    selected: CleanupTab,
    counts: Triple<Int, Int, Int>,
    onSelect: (CleanupTab) -> Unit,
) {
    val tabs = remember { CleanupTab.values().toList() }
    val countFor: (CleanupTab) -> Int = { tab ->
        when (tab) {
            CleanupTab.ALL -> counts.first
            CleanupTab.BLURRY -> counts.second
            CleanupTab.DUPLICATE -> counts.third
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val bg = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardBg
            val border = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardStroke
            val fg = if (isSelected) UiColors.Ai.execBtnText else Color(0xFFB0B0B8)
            val interaction = rememberFeedbackInteractionSource()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .pressFeedback(interaction)
                    .throttledClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(tab) },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${tab.labelZh} ${countFor(tab)}",
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\u5c1a\u672a\u626b\u63cf",
                color = Color(0xFFF0F4FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\u70b9\u51fb\u4e0a\u65b9\u300c\u7acb\u5373\u626b\u63cf\u300d\uff0cAI \u5c06\u5206\u6790\u4f60\u7684\u4fdd\u9669\u7bb1\u7167\u7247\uff0c\u627e\u51fa\u53ef\u6e05\u7406\u7684\u6a21\u7cca\u548c\u91cd\u590d\u9879\u3002",
                color = Color(0xFF8A8A90),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CleanupGrid(
    tab: CleanupTab,
    blurry: List<AiQualityRecord>,
    duplicates: List<AiQualityRecord>,
    pathMap: Map<Long, String>,
    onOpenPhoto: (String) -> Unit,
) {
    // 组装当前 Tab 要展示的单元格列表。
    //  - ALL: blurry + duplicates 取并集（同一 photoId 可能同时为模糊与重复）
    //  - BLURRY / DUPLICATE: 只取对应分类
    val blurryById = remember(blurry) { blurry.associateBy { it.photoId } }
    val dupById = remember(duplicates) { duplicates.associateBy { it.photoId } }
    val cells: List<CleanupCellItem> = remember(tab, blurry, duplicates, pathMap) {
        val base = when (tab) {
            CleanupTab.ALL -> (blurry.map { it.photoId } + duplicates.map { it.photoId })
                .toSet()
                .sortedDescending()
            CleanupTab.BLURRY -> blurry.map { it.photoId }
            CleanupTab.DUPLICATE -> duplicates.sortedBy { it.duplicateGroupId ?: 0L }
                .map { it.photoId }
        }
        base.map { id ->
            val b = blurryById[id]
            val d = dupById[id]
            CleanupCellItem(
                photoId = id,
                path = pathMap[id],
                isBlurry = b != null,
                isDuplicate = d != null,
                duplicateGroupId = d?.duplicateGroupId,
            )
        }
    }
    if (cells.isEmpty()) {
        TabEmptyState(tab)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(cells, key = { it.photoId }) { cell ->
            CleanupGridCell(
                cell = cell,
                onClick = cell.path?.let { p -> { onOpenPhoto(p) } },
            )
        }
    }
}

/** 单个宫格单元的 UI 模型。 */
private data class CleanupCellItem(
    val photoId: Long,
    val path: String?,
    val isBlurry: Boolean,
    val isDuplicate: Boolean,
    val duplicateGroupId: Long?,
)

@Composable
private fun CleanupGridCell(
    cell: CleanupCellItem,
    onClick: (() -> Unit)?,
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
            .clip(RoundedCornerShape(10.dp))
            .background(UiColors.Ai.featureCardBg)
            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(10.dp))
            .then(clickModifier),
    ) {
        if (cell.path != null) {
            VaultProgressiveImage(
                path = cell.path,
                modifier = Modifier.fillMaxSize(),
                thumbnailMaxPx = 360,
            )
        } else {
            // 映射缺失（已删除 / 尚未同步）时的占位信息。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                Text(
                    text = "photoId",
                    color = Color(0xFF8A8A90),
                    fontSize = 10.sp,
                )
                Text(
                    text = cell.photoId.toString(),
                    color = Color(0xFFF0F4FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        // 左上角征标：模糊 / 重复 / 模糊+重复
        val badgeText = when {
            cell.isBlurry && cell.isDuplicate -> "模糊·重复"
            cell.isBlurry -> "模糊"
            cell.isDuplicate -> "重复"
            else -> null
        }
        val badgeColor = when {
            cell.isBlurry && cell.isDuplicate -> Color(0xCCE46A6A)
            cell.isBlurry -> Color(0xCCFFB547)
            cell.isDuplicate -> Color(0xCC4A90E2)
            else -> Color.Transparent
        }
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TabEmptyState(tab: CleanupTab) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        val desc = when (tab) {
            CleanupTab.ALL -> "暂无可清理照片"
            CleanupTab.BLURRY -> "未发现模糊照片"
            CleanupTab.DUPLICATE -> "未发现重复照片"
        }
        Text(text = desc, color = Color(0xFFB0B0B8), fontSize = 14.sp)
    }
}
