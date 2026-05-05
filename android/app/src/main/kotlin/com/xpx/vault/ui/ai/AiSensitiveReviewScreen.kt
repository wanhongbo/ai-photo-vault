package com.xpx.vault.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.xpx.vault.domain.model.AiSensitiveRecord
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors

/**
 * 敏感审核页：展示 AI 扫描到的"待处理"敏感命中，供用户逐条决定。
 *
 * PR3 版：列表使用轻量信息行（photoId / kind / confidence），暂不渲染缩略图
 * （缩略图路径映射在 PR4 脱敏渲染时统一接入）。每项两个动作：
 *  - 移入：status → moved
 *  - 忽略：status → ignored
 */
@Composable
fun AiSensitiveReviewScreen(
    onBack: () -> Unit,
    viewModel: AiSensitiveReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = "\u654f\u611f\u5ba1\u6838", onBack = onBack)
        SensitiveSummary(count = state.pending.size, scanning = state.scanning, onScan = viewModel::startScan)
        if (state.pending.isEmpty()) {
            SensitiveEmptyState(scanning = state.scanning)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.pending, key = { it.id }) { record ->
                    SensitiveItemRow(
                        record = record,
                        onMove = { viewModel.markMoved(record.id) },
                        onIgnore = { viewModel.markIgnored(record.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SensitiveSummary(count: Int, scanning: Boolean, onScan: () -> Unit) {
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
                    text = "\u5f85\u5ba1\u6838 $count \u9879",
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
private fun SensitiveItemRow(record: AiSensitiveRecord, onMove: () -> Unit, onIgnore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Ai.featureCardBg)
            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = kindLabel(record.kind),
            color = Color(0xFFF0F4FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "photoId=${record.photoId}  confidence=${"%.2f".format(record.confidence)}",
            color = Color(0xFF8A8A90),
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val moveI = rememberFeedbackInteractionSource()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UiColors.Ai.execBtnBg)
                    .pressFeedback(moveI)
                    .throttledClickable(interactionSource = moveI, indication = null, onClick = onMove),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\u79fb\u5165\u654f\u611f\u5206\u7ec4",
                    color = UiColors.Ai.execBtnText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val igI = rememberFeedbackInteractionSource()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UiColors.Ai.featureCardBg)
                    .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(10.dp))
                    .pressFeedback(igI)
                    .throttledClickable(interactionSource = igI, indication = null, onClick = onIgnore),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "\u5ffd\u7565", color = Color(0xFFB0B0B8), fontSize = 13.sp)
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
    "ID_CARD" -> "\u8eab\u4efd\u8bc1 / \u62a4\u7167"
    "BANK_CARD" -> "\u94f6\u884c\u5361"
    "PHONE_NUMBER" -> "\u624b\u673a\u53f7"
    "QR_CODE" -> "\u4e8c\u7ef4\u7801 / \u6761\u7801"
    "FACE_CLEAR" -> "\u6e05\u6670\u4eba\u8138"
    "PRIVATE_CHAT" -> "\u804a\u5929\u622a\u56fe"
    else -> kind
}
