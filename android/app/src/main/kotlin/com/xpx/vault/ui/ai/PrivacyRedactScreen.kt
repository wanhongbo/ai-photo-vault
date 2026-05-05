package com.xpx.vault.ui.ai

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xpx.vault.R
import com.xpx.vault.ai.privacy.RedactionStyle
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors

/**
 * 隐私脱敏页：进入后自动解密原图 + ML Kit 检测敏感区域，提供三种样式预览与导出。
 *
 * 导出走 [com.xpx.vault.ui.export.MediaExporter.exportRedactedBitmap]，落到系统相册
 * Pictures/AIPhotoVault/Redacted，不覆盖原图。
 */
@Composable
fun PrivacyRedactScreen(
    path: String,
    onBack: () -> Unit,
    viewModel: PrivacyRedactViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(path) { viewModel.load(path) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = "\u9690\u79c1\u8131\u654f", onBack = onBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = UiColors.Ai.execBtnBg)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "\u6b63\u5728\u89e3\u5bc6\u539f\u56fe\u5e76\u68c0\u6d4b\u654f\u611f\u533a\u57df\u2026",
                            color = Color(0xFFB0B0B8),
                            fontSize = 13.sp,
                        )
                    }
                }
                state.errorMessage != null -> {
                    Text(
                        text = "\u6253\u5f00\u5931\u8d25\uff1a${state.errorMessage}",
                        color = Color(0xFFF07878),
                        fontSize = 13.sp,
                    )
                }
                state.preview != null -> {
                    Image(
                        bitmap = state.preview!!.asImageBitmap(),
                        contentDescription = "\u8131\u654f\u9884\u89c8",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        RegionSummary(state = state)

        StylePicker(
            current = state.style,
            // 无敏感区域时样式切换不会改变图像（PrivacyRenderer 会直接返回原图拷贝），
            // 置灰按钮避免用户误以为 "点击无效"。
            enabled = state.ready && state.regionCount > 0,
            onSelect = { viewModel.selectStyle(it) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 主按钮：保存到安全相册（加密入库，与原图共存）
        PrimaryActionButton(
            iconRes = R.drawable.ic_ai_shield,
            label = if (state.saving) "保存中…" else "保存到安全相册",
            enabled = state.ready && state.preview != null && !state.saving && !state.exporting,
            onClick = {
                viewModel.saveToVault { success, msg ->
                    val text = if (success) "已保存到安全相册" else "保存失败：$msg"
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 次按钮：导出明文副本到系统相册
        SecondaryActionButton(
            iconRes = R.drawable.ic_photo_save,
            label = if (state.exporting) "导出中…" else "导出到系统相册",
            enabled = state.ready && state.preview != null && !state.exporting && !state.saving,
            onClick = {
                viewModel.exportRedacted { success, msg ->
                    val text = if (success) {
                        "已导出到系统相册 Redacted 目录：$msg"
                    } else {
                        "导出失败：$msg"
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun RegionSummary(state: PrivacyRedactUiState) {
    val (desc, tone) = when {
        state.loading -> "检测中…" to SummaryTone.INFO
        !state.mlKitReady -> "Google Play 服务不可用，已降级为纯预览。未检到敏感区域" to SummaryTone.WARN
        state.regionCount == 0 -> "未检测到人脸 / 证件号 / 条码，脱敏样式不可用，可直接导出原图副本" to SummaryTone.INFO
        else -> "已识别 ${state.regionCount} 个敏感区域，应用脱敏样式即可预览" to SummaryTone.OK
    }
    val dotColor = when (tone) {
        SummaryTone.OK -> UiColors.Ai.execBtnBg
        SummaryTone.WARN -> Color(0xFFFFB547)
        SummaryTone.INFO -> Color(0xFF707078)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Ai.featureCardBg)
            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = desc,
            color = Color(0xFFC8C8CE),
            fontSize = 12.sp,
        )
    }
}

private enum class SummaryTone { OK, WARN, INFO }

@Composable
private fun StylePicker(
    current: RedactionStyle,
    enabled: Boolean,
    onSelect: (RedactionStyle) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RedactionStyle.values().forEach { style ->
            val label = when (style) {
                RedactionStyle.MOSAIC -> "马赛克"
                RedactionStyle.BLUR -> "高斯模糊"
                RedactionStyle.BAR -> "黑条"
            }
            val isSelected = current == style
            val bg = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardBg
            val border = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardStroke
            val fg = if (isSelected) UiColors.Ai.execBtnText else Color(0xFFB0B0B8)
            val interaction = rememberFeedbackInteractionSource()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .alpha(if (enabled) 1f else 0.4f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(12.dp))
                    .pressFeedback(interaction)
                    .throttledClickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClick = { onSelect(style) },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    val bg = if (enabled) UiColors.Ai.execBtnBg else Color(0xFF2B2B33)
    val fg = if (enabled) UiColors.Ai.execBtnText else Color(0xFF707078)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    val bg = UiColors.Ai.featureCardBg
    val stroke = if (enabled) UiColors.Ai.featureCardStroke else Color(0xFF1E1E22)
    val fg = if (enabled) Color(0xFFD8DAE0) else Color(0xFF555559)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(14.dp))
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
