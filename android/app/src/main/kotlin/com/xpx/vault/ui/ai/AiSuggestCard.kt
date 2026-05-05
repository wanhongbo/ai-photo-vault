package com.xpx.vault.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpx.vault.R
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors

/**
 * AI Tab 顶部「建议卡片」主入口。根据 [uiState.suggestion] 分发到对应的子 composable。
 *
 * @param onOpenFeature 跳转到指定 AI 功能页（由上层把 [AiFeatureKey] 映射成路由）。
 *                      注意：为避免与 [AiHomeScreen] 形成循环依赖，这里用 String key。
 * @param onRescan      用户点击 AllClear 的"重新扫描"或 Idle 的"开始扫描"。
 * @param onSnooze      用户点击副按钮"忽略 7 天"。
 */
@Composable
fun AiSuggestCard(
    uiState: AiHomeUiState,
    onOpenPrivacy: () -> Unit,
    onOpenDedup: () -> Unit,
    onRescan: () -> Unit,
    onSnooze: (AiSuggestSnoozePrefs.Kind) -> Unit,
) {
    when (val s = uiState.suggestion) {
        is AiSuggestion.Scanning -> ScanningSuggestCard(s)
        is AiSuggestion.Sensitive -> SensitiveSuggestCard(
            state = s,
            onExec = onOpenPrivacy,
            onViewCleanup = onOpenDedup,
            onSnooze = { onSnooze(AiSuggestSnoozePrefs.Kind.SENSITIVE) },
        )
        is AiSuggestion.Cleanup -> CleanupSuggestCard(
            state = s,
            onExec = onOpenDedup,
            onSnooze = { onSnooze(AiSuggestSnoozePrefs.Kind.CLEANUP) },
        )
        AiSuggestion.AllClear -> AllClearSuggestCard(onRescan = onRescan)
        AiSuggestion.Idle -> IdleSuggestCard(onStartScan = onRescan)
    }
}

// ============================================================================
// 公共组件
// ============================================================================

/** 卡片外壳：圆角 + 垂直渐变背景 + 内边距。 */
@Composable
private fun SuggestCardContainer(
    gradientStart: Color,
    gradientEnd: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(colors = listOf(gradientStart, gradientEnd)),
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

/** 卡片头部：左侧图标 + 右侧 badge + 标题。 */
@Composable
private fun SuggestHeader(
    iconRes: Int,
    iconTint: Color,
    iconBg: Color,
    badgeText: String,
    badgeBg: Color,
    badgeTextColor: Color,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = badgeText,
                    color = badgeTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            }
            Text(
                text = title,
                color = UiColors.Ai.suggestTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 主操作按钮（填色实心）。 */
@Composable
private fun PrimaryActionButton(
    text: String,
    iconRes: Int?,
    containerColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 次操作按钮（透明描边）。 */
@Composable
private fun SecondaryActionButton(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = UiColors.Ai.skipBtnBg,
    textColor: Color = UiColors.Ai.skipBtnText,
    strokeColor: Color = UiColors.Ai.skipBtnStroke,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, strokeColor, RoundedCornerShape(12.dp))
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 卡片正文段落。 */
@Composable
private fun SuggestDesc(text: String) {
    Text(
        text = text,
        color = UiColors.Ai.suggestDesc,
        fontSize = 14.sp,
        lineHeight = (14 * 1.55).sp,
    )
}

// ============================================================================
// 状态子卡片
// ============================================================================

@Composable
private fun ScanningSuggestCard(state: AiSuggestion.Scanning) {
    SuggestCardContainer(
        gradientStart = UiColors.Ai.suggestCardGradientStart,
        gradientEnd = UiColors.Ai.suggestCardGradientEnd,
    ) {
        SuggestHeader(
            iconRes = R.drawable.ic_ai_brain,
            iconTint = Color.White,
            iconBg = UiColors.Ai.iconBgWhite,
            badgeText = stringResource(R.string.ai_suggest_badge_scanning),
            badgeBg = UiColors.Ai.badgeBg,
            badgeTextColor = UiColors.Ai.badgeText,
            title = stringResource(R.string.ai_suggest_scanning_title),
        )
        // 进度条：total == 0 时显示不确定状态。
        if (state.total > 0) {
            val ratio = (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = UiColors.Ai.scanningProgressFill,
                trackColor = UiColors.Ai.scanningProgressTrack,
            )
            SuggestDesc(
                text = stringResource(
                    R.string.ai_suggest_scanning_desc_progress,
                    state.done,
                    state.total,
                ),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = UiColors.Ai.scanningProgressFill,
                trackColor = UiColors.Ai.scanningProgressTrack,
            )
            SuggestDesc(text = stringResource(R.string.ai_suggest_scanning_desc_indeterminate))
        }
    }
}

@Composable
private fun SensitiveSuggestCard(
    state: AiSuggestion.Sensitive,
    onExec: () -> Unit,
    onViewCleanup: () -> Unit,
    onSnooze: () -> Unit,
) {
    SuggestCardContainer(
        gradientStart = UiColors.Ai.sensitiveGradStart,
        gradientEnd = UiColors.Ai.sensitiveGradEnd,
    ) {
        SuggestHeader(
            iconRes = R.drawable.ic_ai_eye_off,
            iconTint = UiColors.Ai.sensitiveBadgeText,
            iconBg = UiColors.Ai.sensitiveIconBg,
            badgeText = stringResource(R.string.ai_suggest_badge_alert),
            badgeBg = UiColors.Ai.sensitiveBadgeBg,
            badgeTextColor = UiColors.Ai.sensitiveBadgeText,
            title = stringResource(R.string.ai_suggest_sensitive_title, state.count),
        )
        SuggestDesc(text = stringResource(R.string.ai_suggest_sensitive_desc))
        if (state.cleanupCount > 0) {
            val interaction = rememberFeedbackInteractionSource()
            Text(
                text = stringResource(R.string.ai_suggest_sensitive_secondary, state.cleanupCount),
                color = UiColors.Ai.cleanupBadgeText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pressFeedback(interaction)
                    .throttledClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onViewCleanup,
                    )
                    .padding(vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryActionButton(
                text = stringResource(R.string.ai_action_redact),
                iconRes = R.drawable.ic_ai_zap,
                containerColor = UiColors.Ai.sensitiveExecBtnBg,
                textColor = UiColors.Ai.sensitiveExecBtnText,
                modifier = Modifier.weight(1f),
                onClick = onExec,
            )
            SecondaryActionButton(
                text = stringResource(R.string.ai_action_snooze),
                modifier = Modifier.weight(1f),
                onClick = onSnooze,
            )
        }
    }
}

@Composable
private fun CleanupSuggestCard(
    state: AiSuggestion.Cleanup,
    onExec: () -> Unit,
    onSnooze: () -> Unit,
) {
    SuggestCardContainer(
        gradientStart = UiColors.Ai.cleanupGradStart,
        gradientEnd = UiColors.Ai.cleanupGradEnd,
    ) {
        SuggestHeader(
            iconRes = R.drawable.ic_ai_copy,
            iconTint = UiColors.Ai.cleanupBadgeText,
            iconBg = UiColors.Ai.cleanupIconBg,
            badgeText = stringResource(R.string.ai_suggest_badge_cleanup),
            badgeBg = UiColors.Ai.cleanupBadgeBg,
            badgeTextColor = UiColors.Ai.cleanupBadgeText,
            title = stringResource(R.string.ai_suggest_cleanup_title, state.count),
        )
        SuggestDesc(text = stringResource(R.string.ai_suggest_cleanup_desc))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryActionButton(
                text = stringResource(R.string.ai_action_cleanup),
                iconRes = R.drawable.ic_ai_zap,
                containerColor = UiColors.Ai.cleanupExecBtnBg,
                textColor = UiColors.Ai.cleanupExecBtnText,
                modifier = Modifier.weight(1f),
                onClick = onExec,
            )
            SecondaryActionButton(
                text = stringResource(R.string.ai_action_snooze),
                modifier = Modifier.weight(1f),
                onClick = onSnooze,
            )
        }
    }
}

@Composable
private fun AllClearSuggestCard(onRescan: () -> Unit) {
    SuggestCardContainer(
        gradientStart = UiColors.Ai.allClearGradStart,
        gradientEnd = UiColors.Ai.allClearGradEnd,
    ) {
        SuggestHeader(
            iconRes = R.drawable.ic_ai_shield,
            iconTint = UiColors.Ai.allClearBadgeText,
            iconBg = UiColors.Ai.allClearIconBg,
            badgeText = stringResource(R.string.ai_suggest_badge_all_clear),
            badgeBg = UiColors.Ai.allClearBadgeBg,
            badgeTextColor = UiColors.Ai.allClearBadgeText,
            title = stringResource(R.string.ai_suggest_all_clear_title),
        )
        SuggestDesc(text = stringResource(R.string.ai_suggest_all_clear_desc))
        PrimaryActionButton(
            text = stringResource(R.string.ai_action_rescan),
            iconRes = R.drawable.ic_ai_zap,
            containerColor = UiColors.Ai.allClearExecBtnBg,
            textColor = UiColors.Ai.allClearExecBtnText,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    UiColors.Ai.allClearExecBtnStroke,
                    RoundedCornerShape(12.dp),
                ),
            onClick = onRescan,
        )
    }
}

@Composable
private fun IdleSuggestCard(onStartScan: () -> Unit) {
    SuggestCardContainer(
        gradientStart = UiColors.Ai.suggestCardGradientStart,
        gradientEnd = UiColors.Ai.suggestCardGradientEnd,
    ) {
        SuggestHeader(
            iconRes = R.drawable.ic_ai_brain,
            iconTint = Color.White,
            iconBg = UiColors.Ai.iconBgWhite,
            badgeText = stringResource(R.string.ai_suggest_badge_idle),
            badgeBg = UiColors.Ai.badgeBg,
            badgeTextColor = UiColors.Ai.badgeText,
            title = stringResource(R.string.ai_suggest_idle_title),
        )
        SuggestDesc(text = stringResource(R.string.ai_suggest_idle_desc))
        PrimaryActionButton(
            text = stringResource(R.string.ai_action_start_scan),
            iconRes = R.drawable.ic_ai_zap,
            containerColor = UiColors.Ai.execBtnBg,
            textColor = UiColors.Ai.execBtnText,
            modifier = Modifier.fillMaxWidth(),
            onClick = onStartScan,
        )
    }
}
