package com.xpx.vault.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.xpx.vault.ai.core.ClassifyCategory
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors

/**
 * 智能分类页：按 6 大类浏览 AI 识别到的照片（以 tag 记录为索引）。
 *
 * PR3 版：水平滚动 Tab + 列表信息行（photoId / label / confidence）。
 * 缩略图渲染留到 PR4 接入（与敏感审核的路径映射同步解决）。
 */
@Composable
fun AiClassifyScreen(
    onBack: () -> Unit,
    viewModel: AiClassifyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = "\u667a\u80fd\u5206\u7c7b", onBack = onBack)
        ClassifySummary(scanning = state.scanning, onScan = viewModel::startScan)
        ClassifyTabRow(selected = state.selected, onSelect = viewModel::select)
        if (state.tags.isEmpty()) {
            ClassifyEmptyState(scanning = state.scanning)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.tags, key = { it.id }) { tag ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(UiColors.Ai.featureCardBg)
                            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = tag.label,
                            color = Color(0xFFF0F4FF),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "photoId=${tag.photoId}  confidence=${"%.2f".format(tag.confidence)}  (${tag.source})",
                            color = Color(0xFF8A8A90),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
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
private fun ClassifyTabRow(selected: ClassifyCategory, onSelect: (ClassifyCategory) -> Unit) {
    val tabs = remember { ClassifyCategory.values().toList() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { cat ->
            val isSelected = cat == selected
            val bg = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardBg
            val border = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardStroke
            val fg = if (isSelected) UiColors.Ai.execBtnText else Color(0xFFB0B0B8)
            val interaction = rememberFeedbackInteractionSource()
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .pressFeedback(interaction)
                    .throttledClickable(interactionSource = interaction, indication = null, onClick = { onSelect(cat) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelFor(cat),
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
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
    ClassifyCategory.SCREENSHOT -> "\u622a\u56fe"
    ClassifyCategory.ID_CARD -> "\u8bc1\u4ef6"
    ClassifyCategory.PORTRAIT -> "\u4eba\u50cf"
    ClassifyCategory.LANDSCAPE -> "\u98ce\u666f"
    ClassifyCategory.FOOD -> "\u7f8e\u98df"
    ClassifyCategory.DOCUMENT -> "\u6587\u6863"
    ClassifyCategory.OTHER -> "\u5176\u4ed6"
}
