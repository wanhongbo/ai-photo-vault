package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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

/**
 * 与 AiFeature 卡片关联的逻辑标识，供跳转路由映射使用。
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        AiHeader()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AiSuggestCard(
                    uiState = uiState,
                    onOpenPrivacy = { onOpenFeature(AiFeatureKey.PRIVACY) },
                    onOpenDedup = { onOpenFeature(AiFeatureKey.DEDUP) },
                    onRescan = viewModel::onRescan,
                    onSnooze = viewModel::onSnooze,
                )
            }
            item { AiFeaturesSection(onOpenFeature = onOpenFeature) }
        }
        if (showBottomNav) {
            HomeBottomNav(tabs = tabs, selectedIndex = selectedTab.ordinal, onSelect = { onOpenTab(tabs[it].tab) })
        }
    }
}

@Composable
private fun AiHeader() {
    // 仅保留标题 / 副标题；原右侧的「筛选 / 帮助」两个圆形按钮为占位（onClick 为空），
    // 因暂无实际功能且用户明确要求移除，整块 Row + AiHeaderButton 一并删掉。
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ai_title),
            color = Color(0xFFF0F4FF),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.ai_subtitle),
            color = Color(0xFF6B6B70),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun AiFeaturesSection(onOpenFeature: (AiFeatureKey) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ai_features_title),
                color = Color(0xFFF0F4FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.ai_features_count),
                color = Color(0xFF6B6B70),
                fontSize = 13.sp,
            )
        }
        val features = remember { aiFeatures() }
        features.chunked(2).forEach { rowFeatures ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowFeatures.forEach { feature ->
                    AiFeatureCard(
                        feature = feature,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenFeature(feature.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiFeatureCard(
    feature: AiFeature,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = modifier
            .height(106.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(UiColors.Ai.featureCardBg)
            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(16.dp))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxSize()
                .background(feature.barColor),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(feature.iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(feature.iconRes),
                    contentDescription = null,
                    tint = feature.barColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = stringResource(feature.nameRes),
                color = UiColors.Ai.featureTitle,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(feature.descRes),
                color = UiColors.Ai.featureDesc,
                fontSize = 12.sp,
            )
        }
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

private fun aiFeatures(): List<AiFeature> = listOf(
    AiFeature(
        key = AiFeatureKey.CLASSIFY,
        nameRes = R.string.ai_feat_classify,
        descRes = R.string.ai_feat_classify_desc,
        iconRes = R.drawable.ic_ai_layers,
        barColor = UiColors.Ai.classifyBar,
        iconBgColor = UiColors.Ai.classifyIconBg,
    ),
    AiFeature(
        key = AiFeatureKey.SEARCH,
        nameRes = R.string.ai_feat_search,
        descRes = R.string.ai_feat_search_desc,
        iconRes = R.drawable.ic_home_action_search,
        barColor = UiColors.Ai.searchBar,
        iconBgColor = UiColors.Ai.searchIconBg,
    ),
    AiFeature(
        key = AiFeatureKey.PRIVACY,
        nameRes = R.string.ai_feat_blur,
        descRes = R.string.ai_feat_blur_desc,
        iconRes = R.drawable.ic_ai_eye_off,
        barColor = UiColors.Ai.blurBar,
        iconBgColor = UiColors.Ai.blurIconBg,
    ),
    AiFeature(
        key = AiFeatureKey.COMPRESS,
        nameRes = R.string.ai_feat_compress,
        descRes = R.string.ai_feat_compress_desc,
        iconRes = R.drawable.ic_ai_image,
        barColor = UiColors.Ai.compressBar,
        iconBgColor = UiColors.Ai.compressIconBg,
    ),
    AiFeature(
        key = AiFeatureKey.ENCRYPT,
        nameRes = R.string.ai_feat_encrypt,
        descRes = R.string.ai_feat_encrypt_desc,
        iconRes = R.drawable.ic_ai_shield,
        barColor = UiColors.Ai.encryptBar,
        iconBgColor = UiColors.Ai.encryptIconBg,
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
