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
            enabled = state.ready,
            onSelect = { viewModel.selectStyle(it) },
        )

        ExportButton(
            enabled = state.ready && state.preview != null && !state.exporting,
            exporting = state.exporting,
            onClick = {
                viewModel.exportRedacted { success, msg ->
                    val text = if (success) {
                        "\u5df2\u5bfc\u51fa\u5230\u7cfb\u7edf\u76f8\u518c Redacted \u76ee\u5f55\uff1a$msg"
                    } else {
                        "\u5bfc\u51fa\u5931\u8d25\uff1a$msg"
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun RegionSummary(state: PrivacyRedactUiState) {
    val desc = when {
        state.loading -> "\u68c0\u6d4b\u4e2d\u2026"
        !state.mlKitReady -> "Google Play \u670d\u52a1\u4e0d\u53ef\u7528\uff0c\u5df2\u964d\u7ea7\u4e3a\u7eaf\u9884\u89c8\uff08\u672a\u68c0\u5230\u654f\u611f\u533a\u57df\uff09"
        state.regionCount == 0 -> "\u672a\u68c0\u6d4b\u5230\u4eba\u8138 / \u8bc1\u4ef6\u53f7 / \u6761\u7801\uff0c\u53ef\u4ee5\u76f4\u63a5\u5bfc\u51fa"
        else -> "\u5171\u8bc6\u522b\u5230 ${state.regionCount} \u4e2a\u654f\u611f\u533a\u57df\uff0c\u5df2\u81ea\u52a8\u906e\u76d6"
    }
    Text(
        text = desc,
        color = Color(0xFFB0B0B8),
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

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
                RedactionStyle.MOSAIC -> "\u9a6c\u8d5b\u514b"
                RedactionStyle.BLUR -> "\u9ad8\u65af\u6a21\u7cca"
                RedactionStyle.BAR -> "\u9ed1\u6761"
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
private fun ExportButton(
    enabled: Boolean,
    exporting: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    val bg = if (enabled) UiColors.Ai.execBtnBg else Color(0xFF2B2B33)
    val fg = if (enabled) UiColors.Ai.execBtnText else Color(0xFF707078)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
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
            painter = painterResource(R.drawable.ic_photo_save),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (exporting) "\u5bfc\u51fa\u4e2d\u2026" else "\u5bfc\u51fa\u8131\u654f\u526f\u672c\u5230\u7cfb\u7edf\u76f8\u518c",
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
