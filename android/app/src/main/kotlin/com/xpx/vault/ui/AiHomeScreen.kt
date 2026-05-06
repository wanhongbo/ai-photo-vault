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
import androidx.compose.foundation.layout.width
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
 *
 * 枚举保留 6 值，仅 [aiFeatures] 滤掉 SEARCH / COMPRESS，方便未来恢复时
 * 无需改动 MainActivity 的 when 分支。
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
            .padding(20.dp),
    ) {
        AiHeader()
        Spacer(Modifier.height(16.dp))
        // 建议卡片 wrap content；功能区拿剩余空间 weight(1f)，让 4 个卡片尽量铺满一屏。
        // 不再用 LazyColumn：建议卡 + 4 功能卡不会溢出，也不需要滚动。
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
            color = Color(0xFFF0F4FF),
            fontSize = 28.sp,
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
            color = Color(0xFFF0F4FF),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        // 「4 项功能」由运行时动态拼接（而非固定文案），跟随卡片数量变化。
        Text(
            text = stringResource(R.string.ai_features_count_fmt, count),
            color = Color(0xFF6B6B70),
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
    // 2×2 宫格。每行均分父高度，每列均分行宽度，让 4 张卡自适应铺满剩余屏幕。
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        features.chunked(2).forEach { rowFeatures ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowFeatures.forEach { feature ->
                    AiFeatureCard(
                        feature = feature,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onOpenFeature(feature.key) },
                    )
                }
                // 奇数卡片时补空占位（当前 4 个功能不会命中，但写成防御性防后续调整）。
        if (rowFeatures.size == 1) Spacer(Modifier.weight(1f))
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
            .clip(RoundedCornerShape(16.dp))
            .background(UiColors.Ai.featureCardBg)
            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(16.dp))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(feature.barColor),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(feature.iconBgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(feature.iconRes),
                    contentDescription = null,
                    tint = feature.barColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 不再用 Spacer(weight(1f)) 把文字推到底部，避免卡片高度不足时标题/描述被圆角裁切。
            Text(
                text = stringResource(feature.nameRes),
                color = UiColors.Ai.featureTitle,
                fontSize = 15.sp,
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
    // SEARCH（语义搜索）暂隐藏：当前实际路由同 CLASSIFY，没独立能力前无需暴露入口。
    AiFeature(
        key = AiFeatureKey.PRIVACY,
        nameRes = R.string.ai_feat_blur,
        descRes = R.string.ai_feat_blur_desc,
        iconRes = R.drawable.ic_ai_eye_off,
        barColor = UiColors.Ai.blurBar,
        iconBgColor = UiColors.Ai.blurIconBg,
    ),
    // COMPRESS（图片瘦身、实际路由同 DEDUP→垃圾清理页）移除：与智能去重重复。
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
