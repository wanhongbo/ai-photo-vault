package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

/**
 * 与 AiFeature 卡片关联的逻辑标识，供跳转路由映射使用。
 *
 * 枚举保留 6 值（含 ENCRYPT 等），AI Tab 仅展示 [aiFeatures] 中的 3 项；MainActivity 路由 when 仍覆盖全部 key。
 */
enum class AiFeatureKey { CLASSIFY, SEARCH, PRIVACY, COMPRESS, ENCRYPT, DEDUP }

@Composable
fun AiHomeScreen(
    onOpenTab: (HomeTab) -> Unit,
    selectedTab: HomeTab = HomeTab.AI,
    showBottomNav: Boolean = true,
    onOpenFeature: (AiFeatureKey) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: AiHomeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = remember { homeTabs() }
    val features = remember { aiFeatures() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.settingsScreenHorizontalPad),
    ) {
        AiHeader()
        Spacer(Modifier.height(16.dp))
        // 建议卡片 wrap content；功能区拿剩余空间 weight(1f)，3 张功能卡品字形铺满剩余区域。
        // 不再用 LazyColumn：建议卡 + 功能卡不滚动。
        AiSuggestCard(
            uiState = uiState,
            onOpenPrivacy = { onOpenFeature(AiFeatureKey.PRIVACY) },
            onOpenDedup = { onOpenFeature(AiFeatureKey.DEDUP) },
            onRescan = viewModel::onRescan,
            onSnooze = viewModel::onSnooze,
        )
        Spacer(Modifier.height(16.dp))
        AiFeaturesHeader(count = features.size)
        Spacer(Modifier.height(12.dp))
        AiFeaturesGrid(
            features = features,
            onOpenFeature = onOpenFeature,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        if (showBottomNav) {
            HomeBottomNav(tabs = tabs, selectedIndex = selectedTab.ordinal, onSelect = { onOpenTab(tabs[it].tab) })
        }
    }
}

@Composable
private fun AiHeader() {
    // 仅保留标题。副标题（日期 + 「智能引擎已就绪」）被用户明确移除，
    // ai_subtitle 串暂不删，保留兼容。
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ai_title),
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AiFeaturesHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ai_features_title),
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeSectionTitle,
            fontWeight = FontWeight.Bold,
        )
        // 「4 项功能」由运行时动态拼接（而非固定文案），跟随卡片数量变化。
        Text(
            text = stringResource(R.string.ai_features_count_fmt, count),
            color = UiColors.Home.subtitle,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun AiFeaturesGrid(
    features: List<AiFeature>,
    onOpenFeature: (AiFeatureKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 品字形：上排隐私打码横向占满；下排智能分类 + 智能去重各占一半。
    require(features.size == 3) { "AI tab expects exactly 3 feature cards" }
    val top = features[0]
    val bottomLeft = features[1]
    val bottomRight = features[2]
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AiFeatureCard(
            feature = top,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            wideTopLayout = true,
            onClick = { onOpenFeature(top.key) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AiFeatureCard(
                feature = bottomLeft,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = { onOpenFeature(bottomLeft.key) },
            )
            AiFeatureCard(
                feature = bottomRight,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = { onOpenFeature(bottomRight.key) },
            )
        }
    }
}

@Composable
private fun AiFeatureCard(
    feature: AiFeature,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wideTopLayout: Boolean = false,
) {
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(UiSize.homeCardPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (wideTopLayout) 56.dp else 52.dp)
                .clip(RoundedCornerShape(UiRadius.homeAlbumCard))
                .background(feature.iconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(feature.iconRes),
                contentDescription = null,
                tint = feature.barColor,
                modifier = Modifier.size(if (wideTopLayout) 28.dp else 26.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(feature.nameRes),
            color = UiColors.Home.emptyTitle,
            fontSize = UiTextSize.homeEmptyTitle,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

data class AiFeature(
    val key: AiFeatureKey,
    val nameRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val barColor: Color,
    val iconBgColor: Color,
)

/** 顺序与 [AiFeaturesGrid] 品字形布局绑定：上排、左下、右下。 */
private fun aiFeatures(): List<AiFeature> = listOf(
    AiFeature(
        key = AiFeatureKey.PRIVACY,
        nameRes = R.string.ai_feat_blur,
        descRes = R.string.ai_feat_blur_desc,
        iconRes = R.drawable.ic_ai_eye_off,
        barColor = UiColors.Ai.blurBar,
        iconBgColor = UiColors.Ai.blurIconBg,
    ),
    AiFeature(
        key = AiFeatureKey.CLASSIFY,
        nameRes = R.string.ai_feat_classify,
        descRes = R.string.ai_feat_classify_desc,
        iconRes = R.drawable.ic_ai_layers,
        barColor = UiColors.Ai.classifyBar,
        iconBgColor = UiColors.Ai.classifyIconBg,
    ),
    AiFeature(
        key = AiFeatureKey.DEDUP,
        nameRes = R.string.ai_feat_dedup,
        descRes = R.string.ai_feat_dedup_desc,
        iconRes = R.drawable.ic_ai_copy,
        barColor = UiColors.Ai.dedupBar,
        iconBgColor = UiColors.Ai.dedupIconBg,
    ),
)
