package com.xpx.vault.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xpx.vault.R
import com.xpx.vault.ui.ai.AiHomeViewModel
import com.xpx.vault.ui.ai.AiSuggestCard
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

enum class AiFeatureKey { CLASSIFY, SEARCH, PRIVACY, COMPRESS, ENCRYPT, DEDUP }

@Composable
fun AiHomeScreen(
    onOpenTab: (HomeTab) -> Unit,
    selectedTab: HomeTab = HomeTab.AI,
    showBottomNav: Boolean = true,
    onOpenFeature: (AiFeatureKey) -> Unit = {},
    onOpenPrivateCamera: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: AiHomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = remember { homeTabs() }
    val tools = remember { aiTools() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.settingsScreenHorizontalPad),
    ) {
        AiHeader()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AiSuggestCard(
                uiState = uiState,
                onOpenVault = { onOpenTab(HomeTab.VAULT) },
                onOpenPrivateCamera = onOpenPrivateCamera,
                onOpenPrivacy = { onOpenFeature(AiFeatureKey.PRIVACY) },
                onOpenDedup = { onOpenFeature(AiFeatureKey.DEDUP) },
                onOpenClassify = { onOpenFeature(AiFeatureKey.CLASSIFY) },
                onStartScan = viewModel::onStartScan,
                onRescan = viewModel::onRescan,
                onCancelScan = viewModel::onCancelScan,
                onSnooze = viewModel::onSnooze,
            )
            AiToolsHeader(count = tools.size)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tools.forEach { tool ->
                    AiToolRow(
                        tool = tool,
                        onClick = { onOpenFeature(tool.key) },
                    )
                }
            }
        }
        if (showBottomNav) {
            HomeBottomNav(tabs = tabs, selectedIndex = selectedTab.ordinal, onSelect = { onOpenTab(tabs[it].tab) })
        }
    }
}

@Composable
private fun AiHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ai_title),
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.ai_home_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AiToolsHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ai_tools_title),
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeSectionTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.ai_tools_count_fmt, count),
            color = UiColors.Home.subtitle,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AiToolRow(
    tool: AiTool,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, tool.strokeColor, RoundedCornerShape(18.dp))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(tool.iconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(tool.iconRes),
                contentDescription = null,
                tint = tool.iconColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(tool.titleRes),
            color = UiColors.Home.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(tool.statusRes),
            color = Color(0xFFB7C6DD),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF122033))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private data class AiTool(
    val key: AiFeatureKey,
    val titleRes: Int,
    val statusRes: Int,
    val iconRes: Int,
    val iconColor: Color,
    val iconBgColor: Color,
    val strokeColor: Color,
)

private fun aiTools(): List<AiTool> = listOf(
    AiTool(
        key = AiFeatureKey.PRIVACY,
        titleRes = R.string.ai_feat_blur,
        statusRes = R.string.ai_tool_status_open,
        iconRes = R.drawable.ic_ai_eye_off,
        iconColor = UiColors.Lock.brandBlue,
        iconBgColor = UiColors.Ai.classifyIconBg,
        strokeColor = Color(0xFF244869),
    ),
    AiTool(
        key = AiFeatureKey.CLASSIFY,
        titleRes = R.string.ai_feat_classify,
        statusRes = R.string.ai_tool_status_ready,
        iconRes = R.drawable.ic_ai_layers,
        iconColor = Color(0xFF7DBBFF),
        iconBgColor = Color(0xFF18283D),
        strokeColor = UiColors.Home.emptyCardStroke,
    ),
    AiTool(
        key = AiFeatureKey.DEDUP,
        titleRes = R.string.ai_tool_dedup_title,
        statusRes = R.string.ai_tool_status_ready,
        iconRes = R.drawable.ic_ai_copy,
        iconColor = Color(0xFF5BC0D4),
        iconBgColor = Color(0x335BC0D4),
        strokeColor = Color(0xFF214536),
    ),
)
