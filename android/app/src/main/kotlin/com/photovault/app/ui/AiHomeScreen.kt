package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.photovault.app.R
import com.photovault.app.ui.feedback.pressFeedback
import com.photovault.app.ui.feedback.rememberFeedbackInteractionSource
import com.photovault.app.ui.feedback.throttledClickable
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize

@Composable
fun AiHomeScreen(
    onOpenTab: (HomeTab) -> Unit,
    selectedTab: HomeTab = HomeTab.AI,
    showBottomNav: Boolean = true,
    modifier: Modifier = Modifier,
) {
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
            item { AiSuggestCard() }
            item { AiFeaturesSection() }
        }
        if (showBottomNav) {
            HomeBottomNav(tabs = tabs, selectedIndex = selectedTab.ordinal, onSelect = { onOpenTab(tabs[it].tab) })
        }
    }
}

@Composable
private fun AiHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AiHeaderButton(
                iconRes = R.drawable.ic_ai_sliders,
                contentDesc = "Filter",
                onClick = { },
            )
            AiHeaderButton(
                iconRes = R.drawable.ic_ai_help,
                contentDesc = "Help",
                onClick = { },
            )
        }
    }
}

@Composable
private fun AiHeaderButton(
    iconRes: Int,
    contentDesc: String,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(UiColors.Ai.headerBtnBg)
            .border(1.dp, UiColors.Ai.headerBtnStroke, RoundedCornerShape(20.dp))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDesc,
            tint = Color(0xFFF0F4FF),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AiSuggestCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        UiColors.Ai.suggestCardGradientStart,
                        UiColors.Ai.suggestCardGradientEnd,
                    ),
                ),
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
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
                    painter = painterResource(R.drawable.ic_ai_brain),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(UiColors.Ai.badgeBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ai_suggest_badge),
                        color = UiColors.Ai.badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                }
                Text(
                    text = stringResource(R.string.ai_suggest_title),
                    color = UiColors.Ai.suggestTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            text = stringResource(R.string.ai_suggest_desc),
            color = UiColors.Ai.suggestDesc,
            fontSize = 14.sp,
            lineHeight = (14 * 1.55).sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val execInteraction = rememberFeedbackInteractionSource()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UiColors.Ai.execBtnBg)
                    .pressFeedback(execInteraction)
                    .throttledClickable(
                        interactionSource = execInteraction,
                        indication = null,
                        onClick = { },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_zap),
                    contentDescription = null,
                    tint = UiColors.Ai.execBtnText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ai_action_exec),
                    color = UiColors.Ai.execBtnText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val skipInteraction = rememberFeedbackInteractionSource()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UiColors.Ai.skipBtnBg)
                    .border(1.dp, UiColors.Ai.skipBtnStroke, RoundedCornerShape(12.dp))
                    .pressFeedback(skipInteraction)
                    .throttledClickable(
                        interactionSource = skipInteraction,
                        indication = null,
                        onClick = { },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ai_action_skip),
                    color = UiColors.Ai.skipBtnText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AiFeaturesSection() {
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
                        onClick = { },
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
    val nameRes: Int,
    val descRes: Int,
    val iconRes: Int,
    val barColor: Color,
    val iconBgColor: Color,
)

private fun aiFeatures(): List<AiFeature> = listOf(
    AiFeature(
        nameRes = R.string.ai_feat_classify,
        descRes = R.string.ai_feat_classify_desc,
        iconRes = R.drawable.ic_ai_layers,
        barColor = UiColors.Ai.classifyBar,
        iconBgColor = UiColors.Ai.classifyIconBg,
    ),
    AiFeature(
        nameRes = R.string.ai_feat_search,
        descRes = R.string.ai_feat_search_desc,
        iconRes = R.drawable.ic_home_action_search,
        barColor = UiColors.Ai.searchBar,
        iconBgColor = UiColors.Ai.searchIconBg,
    ),
    AiFeature(
        nameRes = R.string.ai_feat_blur,
        descRes = R.string.ai_feat_blur_desc,
        iconRes = R.drawable.ic_ai_eye_off,
        barColor = UiColors.Ai.blurBar,
        iconBgColor = UiColors.Ai.blurIconBg,
    ),
    AiFeature(
        nameRes = R.string.ai_feat_compress,
        descRes = R.string.ai_feat_compress_desc,
        iconRes = R.drawable.ic_ai_image,
        barColor = UiColors.Ai.compressBar,
        iconBgColor = UiColors.Ai.compressIconBg,
    ),
    AiFeature(
        nameRes = R.string.ai_feat_encrypt,
        descRes = R.string.ai_feat_encrypt_desc,
        iconRes = R.drawable.ic_ai_shield,
        barColor = UiColors.Ai.encryptBar,
        iconBgColor = UiColors.Ai.encryptIconBg,
    ),
    AiFeature(
        nameRes = R.string.ai_feat_dedup,
        descRes = R.string.ai_feat_dedup_desc,
        iconRes = R.drawable.ic_ai_copy,
        barColor = UiColors.Ai.dedupBar,
        iconBgColor = UiColors.Ai.dedupIconBg,
    ),
)
