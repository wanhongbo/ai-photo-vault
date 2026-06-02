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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpx.vault.R
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize

@Composable
fun AiSuggestCard(
    uiState: AiHomeUiState,
    onOpenVault: () -> Unit,
    onOpenPrivateCamera: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDedup: () -> Unit,
    onOpenClassify: () -> Unit,
    onStartScan: () -> Unit,
    onRescan: () -> Unit,
    onCancelScan: () -> Unit,
    onSnooze: (AiSuggestSnoozePrefs.Kind) -> Unit,
) {
    val suggestion = uiState.suggestion
    val model = rememberSummaryModel(
        uiState = uiState,
        suggestion = suggestion,
        onOpenVault = onOpenVault,
        onOpenPrivateCamera = onOpenPrivateCamera,
        onOpenPrivacy = onOpenPrivacy,
        onOpenDedup = onOpenDedup,
        onOpenClassify = onOpenClassify,
        onStartScan = onStartScan,
        onRescan = onRescan,
        onSnooze = onSnooze,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.homeCardPadding),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SummaryHeader(model)
        Text(
            text = stringResource(model.descriptionRes),
            color = UiColors.Home.subtitle,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        SummaryStats(uiState)
        if (suggestion is AiSuggestion.Scanning) {
            ScanningProgress(
                state = suggestion,
                onCancelScan = onCancelScan,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryActionButton(
                    text = stringResource(model.primaryRes),
                    containerColor = UiColors.Ai.execBtnBg,
                    textColor = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    onClick = model.primaryAction,
                )
                SummaryActionButton(
                    text = stringResource(model.secondaryRes),
                    containerColor = Color(0xFF122033),
                    textColor = Color(0xFFB7C6DD),
                    strokeColor = UiColors.Home.emptyCardStroke,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    onClick = model.secondaryAction,
                )
            }
        }
    }
}

@Composable
private fun SummaryHeader(model: SummaryModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFF18283D)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ai_sparkles),
                contentDescription = null,
                tint = Color(0xFFB7D7FF),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(model.badgeRes),
                color = Color(0xFFB7D7FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (model.titleArg == null) {
                    stringResource(model.titleRes)
                } else {
                    stringResource(model.titleRes, model.titleArg)
                },
                color = UiColors.Home.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryStats(uiState: AiHomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryStatChip(uiState.scannedCount, R.string.ai_stat_scanned, Modifier.weight(1f))
        SummaryStatChip(uiState.pendingSensitive, R.string.ai_stat_sensitive, Modifier.weight(1f))
        SummaryStatChip(uiState.locationRiskCount, R.string.ai_stat_location, Modifier.weight(1f))
        SummaryStatChip(uiState.totalCleanup, R.string.ai_stat_cleanup, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryStatChip(value: Int, labelRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF122033))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value.toString(),
            color = UiColors.Home.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = stringResource(labelRes),
            color = UiColors.Home.subtitle,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScanningProgress(
    state: AiSuggestion.Scanning,
    onCancelScan: () -> Unit,
) {
    if (state.total > 0) {
        LinearProgressIndicator(
            progress = { state.done.toFloat().div(state.total).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = UiColors.Ai.scanningProgressFill,
            trackColor = UiColors.Ai.scanningProgressTrack,
        )
        Text(
            text = stringResource(R.string.ai_scan_progress_fmt, state.done, state.total),
            color = UiColors.Home.subtitle,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
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
    }
    Text(
        text = stringResource(if (state.cancelling) R.string.ai_scan_cancelling else R.string.ai_scan_incremental_hint),
        color = UiColors.Home.subtitle,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
    SummaryActionButton(
        text = stringResource(R.string.ai_action_pause_scan),
        containerColor = Color(0xFF122033),
        textColor = Color(0xFFB7C6DD),
        strokeColor = UiColors.Home.emptyCardStroke,
        fontWeight = FontWeight.SemiBold,
        enabled = !state.cancelling,
        modifier = Modifier.fillMaxWidth(),
        onClick = onCancelScan,
    )
}

@Composable
private fun SummaryActionButton(
    text: String,
    containerColor: Color,
    textColor: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    strokeColor: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.55f))
            .then(
                if (strokeColor != null) {
                    Modifier.border(1.dp, strokeColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else textColor.copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

private data class SummaryModel(
    val badgeRes: Int,
    val titleRes: Int,
    val titleArg: Int? = null,
    val descriptionRes: Int,
    val primaryRes: Int,
    val secondaryRes: Int,
    val primaryAction: () -> Unit,
    val secondaryAction: () -> Unit,
)

private fun rememberSummaryModel(
    uiState: AiHomeUiState,
    suggestion: AiSuggestion,
    onOpenVault: () -> Unit,
    onOpenPrivateCamera: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenDedup: () -> Unit,
    onOpenClassify: () -> Unit,
    onStartScan: () -> Unit,
    onRescan: () -> Unit,
    onSnooze: (AiSuggestSnoozePrefs.Kind) -> Unit,
): SummaryModel = when (suggestion) {
    AiSuggestion.Empty -> SummaryModel(
        badgeRes = R.string.ai_summary_empty_badge,
        titleRes = R.string.ai_summary_empty_title,
        descriptionRes = R.string.ai_summary_empty_desc,
        primaryRes = R.string.ai_action_open_vault,
        secondaryRes = R.string.ai_action_private_camera,
        primaryAction = onOpenVault,
        secondaryAction = onOpenPrivateCamera,
    )
    is AiSuggestion.Scanning -> SummaryModel(
        badgeRes = R.string.ai_summary_scanning_badge,
        titleRes = R.string.ai_summary_scanning_title,
        descriptionRes = R.string.ai_vault_scan_scanning_desc,
        primaryRes = R.string.ai_action_pause_scan,
        secondaryRes = R.string.ai_action_pause_scan,
        primaryAction = {},
        secondaryAction = {},
    )
    is AiSuggestion.Sensitive -> {
        val locationOnly = suggestion.locationRiskCount > 0 && suggestion.locationRiskCount == suggestion.count
        SummaryModel(
            badgeRes = R.string.ai_summary_label,
            titleRes = if (locationOnly) R.string.ai_summary_location_title_fmt else R.string.ai_summary_sensitive_title_fmt,
            titleArg = if (locationOnly) suggestion.locationRiskCount else suggestion.count,
            descriptionRes = if (suggestion.locationRiskCount > 0) R.string.ai_summary_location_desc else R.string.ai_summary_desc,
            primaryRes = R.string.ai_summary_review_now,
            secondaryRes = R.string.ai_summary_later,
            primaryAction = onOpenPrivacy,
            secondaryAction = { onSnooze(AiSuggestSnoozePrefs.Kind.SENSITIVE) },
        )
    }
    is AiSuggestion.Cleanup -> SummaryModel(
        badgeRes = R.string.ai_summary_cleanup_badge,
        titleRes = R.string.ai_summary_cleanup_title_fmt,
        titleArg = uiState.totalCleanup,
        descriptionRes = R.string.ai_summary_live_desc,
        primaryRes = R.string.ai_action_review_cleanup,
        secondaryRes = R.string.ai_summary_later,
        primaryAction = onOpenDedup,
        secondaryAction = { onSnooze(AiSuggestSnoozePrefs.Kind.CLEANUP) },
    )
    AiSuggestion.AllClear -> SummaryModel(
        badgeRes = R.string.ai_summary_all_clear_badge,
        titleRes = R.string.ai_summary_all_clear_title,
        descriptionRes = R.string.ai_summary_live_desc,
        primaryRes = R.string.ai_action_rescan,
        secondaryRes = R.string.ai_action_view_categories,
        primaryAction = onRescan,
        secondaryAction = onOpenClassify,
    )
    AiSuggestion.Idle -> SummaryModel(
        badgeRes = R.string.ai_summary_unscanned_badge,
        titleRes = R.string.ai_summary_unscanned_title,
        descriptionRes = R.string.ai_summary_live_desc,
        primaryRes = R.string.ai_action_scan_vault,
        secondaryRes = R.string.ai_action_view_categories,
        primaryAction = onStartScan,
        secondaryAction = onOpenClassify,
    )
}
