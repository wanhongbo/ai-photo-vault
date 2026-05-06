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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors

/**
 * 敏感审核页：展示 AI 扫描到的 pending 敏感命中。
 *
 * 布局：3 列宫格缩略图，左上角浮标展示 kind 中文标签。
 * 交互：点击缩略图 → 图片预览页（由外部 NavHost 处理 path → photo_viewer 路由）。
 *
 * 同一张照片若命中多种 kind（如身份证 + 清晰人脸），按 photoId 聚合为 1 个单元格，
 * 浮标文案用「·」拼接，避免同一缩略图重复出现打断用户扫视。
 */
@Composable
fun AiSensitiveReviewScreen(
    onBack: () -> Unit,
    onOpenPhoto: (String) -> Unit = {},
    viewModel: AiSensitiveReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 按 photoId 聚合：同一 photoId 的多条 record 合并展示为一个单元格，kind 去重后汇总。
    val cells: List<SensitiveGridItem> = remember(state.pending, state.pathByPhotoId) {
        state.pending.groupBy { it.photoId }
            .map { (photoId, records) ->
                SensitiveGridItem(
                    photoId = photoId,
                    path = state.pathByPhotoId[photoId],
                    kinds = records.map { it.kind }.distinct(),
                )
            }
            .sortedByDescending { it.photoId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = "\u654f\u611f\u5ba1\u6838", onBack = onBack)
        SensitiveSummary(
            photoCount = cells.size,
            scanning = state.scanning,
            onScan = viewModel::startScan,
        )
        if (cells.isEmpty()) {
            SensitiveEmptyState(scanning = state.scanning)
        } else {
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
                    SensitiveGridCell(
                        cell = cell,
                        onClick = cell.path?.let { p -> { onOpenPhoto(p) } },
                    )
                }
            }
        }
    }
}

/** 单个宫格单元格的数据模型：聚合后的 photoId + kind 列表 + path。 */
private data class SensitiveGridItem(
    val photoId: Long,
    val path: String?,
    val kinds: List<String>,
)

@Composable
private fun SensitiveSummary(photoCount: Int, scanning: Boolean, onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UiColors.Ai.iconBgWhite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_shield),
                    contentDescription = null,
                    tint = UiColors.Ai.dedupBar,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = "\u5f85\u5ba1\u6838 $photoCount \u5f20",
                    color = Color(0xFFF0F4FF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "\u5df2\u547d\u4e2d\u8eab\u4efd\u8bc1/\u94f6\u884c\u5361/\u624b\u673a\u53f7/\u4e8c\u7ef4\u7801/\u4eba\u8138 \u7b49",
                    color = Color(0xFF8A8A90),
                    fontSize = 12.sp,
                )
            }
        }
        val interaction = rememberFeedbackInteractionSource()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UiColors.Ai.execBtnBg)
                .pressFeedback(interaction)
                .throttledClickable(interactionSource = interaction, indication = null, onClick = onScan),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_zap),
                contentDescription = null,
                tint = UiColors.Ai.execBtnText,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (scanning) "\u6b63\u5728\u626b\u63cf\u2026" else "\u91cd\u65b0\u626b\u63cf",
                color = UiColors.Ai.execBtnText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SensitiveGridCell(
    cell: SensitiveGridItem,
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
            // path 映射缺失（文件刚被删除 / 尚未同步）时的占位，避免整格空白。
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
        // 左上角浮标：多 kind 用「·」拼接，与清理页「模糊·重复」一致的视觉风格。
        val badgeText = cell.kinds.joinToString("\u00b7") { kindLabel(it) }
        if (badgeText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCCE46A6A))
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
private fun SensitiveEmptyState(scanning: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (scanning) "\u626b\u63cf\u4e2d\u2026" else "\u6682\u65e0\u5f85\u5ba1\u6838\u9879",
                color = Color(0xFFF0F4FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "\u70b9\u51fb\u4e0a\u65b9\u300c\u91cd\u65b0\u626b\u63cf\u300d\uff0cAI \u5c06\u8bc6\u522b\u8eab\u4efd\u8bc1\u3001\u94f6\u884c\u5361\u3001\u4e8c\u7ef4\u7801\u7b49\u654f\u611f\u5185\u5bb9\u3002",
                color = Color(0xFF8A8A90),
                fontSize = 13.sp,
            )
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "ID_CARD" -> "\u8eab\u4efd\u8bc1"
    "BANK_CARD" -> "\u94f6\u884c\u5361"
    "PHONE_NUMBER" -> "\u624b\u673a\u53f7"
    "QR_CODE" -> "\u4e8c\u7ef4\u7801"
    "FACE_CLEAR" -> "\u4eba\u8138"
    "PRIVATE_CHAT" -> "\u804a\u5929"
    else -> kind
}
