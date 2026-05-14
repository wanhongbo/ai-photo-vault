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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xpx.vault.R
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.settings.SettingsListChevronIcon
import com.xpx.vault.ui.settings.SettingsRowModel
import com.xpx.vault.ui.settings.SettingsSimpleRow
import com.xpx.vault.ui.settings.SettingsTrailing
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

@Composable
fun SettingsHomeScreen(
    onOpenTab: (HomeTab) -> Unit,
    onOpenSettingsHub: (SettingsHubDestination) -> Unit,
    selectedTab: HomeTab = HomeTab.SETTINGS,
    showBottomNav: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { homeTabs() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(
                horizontal = UiSize.settingsScreenHorizontalPad,
                vertical = UiSize.settingsScreenVerticalPad,
            ),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = UiSize.settingsListTopGap),
            verticalArrangement = Arrangement.spacedBy(UiSize.settingsSectionGap),
        ) {
            item {
                SettingsSubscriptionEntryCard(
                    onClick = { onOpenSettingsHub(SettingsHubDestination.SUBSCRIPTION) },
                )
            }
            // 备份提示 banner 置于订阅卡下方，作为一级入口的辅助提醒，避免占据顶部视觉焦点。
            item {
                SettingsBackupBanner()
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_security),
                    subtitle = stringResource(R.string.settings_l1_security_sub),
                    leadingIcon = R.drawable.ic_ai_shield,
                    onClick = { onOpenSettingsHub(SettingsHubDestination.SECURITY_PRIVACY) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_backup),
                    subtitle = stringResource(R.string.settings_l1_backup_sub),
                    leadingIcon = R.drawable.ic_backup_restore,
                    onClick = { onOpenSettingsHub(SettingsHubDestination.BACKUP_SYNC) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_data),
                    subtitle = stringResource(R.string.settings_l1_data_sub),
                    leadingIcon = R.drawable.ic_settings_data_storage,
                    onClick = { onOpenSettingsHub(SettingsHubDestination.DATA_STORAGE) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_general),
                    subtitle = stringResource(R.string.settings_l1_general_sub),
                    leadingIcon = R.drawable.ic_settings_general,
                    onClick = { onOpenSettingsHub(SettingsHubDestination.GENERAL) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_about),
                    subtitle = stringResource(R.string.settings_l1_about_sub),
                    leadingIcon = R.drawable.ic_ai_help,
                    onClick = { onOpenSettingsHub(SettingsHubDestination.ABOUT_SUPPORT) },
                )
            }
        }
        if (showBottomNav) {
            HomeBottomNav(
                tabs = tabs,
                selectedIndex = selectedTab.ordinal,
                onSelect = { onOpenTab(tabs[it].tab) },
            )
        }
    }
}

@Composable
private fun SettingsSubscriptionEntryCard(onClick: () -> Unit) {
    val context = LocalContext.current
    // 订阅 isPremium StateFlow，购买成功后自动重组
    val repo = remember { com.xpx.vault.billing.SubscriptionRepoProvider.get(context) }
    val premiumState = repo?.isPremium?.collectAsStateWithLifecycle()
    val premium = premiumState?.value ?: false

    val goldStroke = Color(0xFFE6B94D)
    val goldFillStart = Color(0xFFFFE29A)
    val goldFillEnd = Color(0xFFE0A94A)

    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Ai.featureCardBg)
            .border(
                width = if (premium) 1.5.dp else 1.dp,
                color = if (premium) goldStroke else UiColors.Ai.featureCardStroke,
                shape = RoundedCornerShape(UiRadius.homeCard),
            )
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = UiSize.settingsCardPadding, vertical = UiSize.settingsCardPadding * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithSubscriptionIcon(
            premium = premium,
            goldFillStart = goldFillStart,
            goldFillEnd = goldFillEnd,
            goldStroke = goldStroke,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = UiSize.settingsAvatarGap * 2),
        ) {
            Text(
                text = stringResource(
                    if (premium) R.string.settings_l1_subscription_premium_title
                    else R.string.settings_l1_subscription,
                ),
                color = if (premium) goldStroke else UiColors.Ai.featureTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(
                text = stringResource(
                    if (premium) R.string.settings_l1_subscription_premium_sub
                    else R.string.settings_l1_subscription_sub,
                ),
                color = UiColors.Ai.featureDesc,
                fontSize = UiTextSize.settingsRowDesc,
                modifier = Modifier.padding(top = UiSize.settingsProfileDescTopGap),
            )
        }
        SettingsListChevronIcon(
            tint = if (premium) goldStroke else UiColors.Home.navItemActive,
            size = 28.dp,
        )
    }
}

@Composable
private fun BoxWithSubscriptionIcon(
    premium: Boolean,
    goldFillStart: Color,
    goldFillEnd: Color,
    goldStroke: Color,
) {
    val outer = UiSize.settingsAvatarSize * 2
    val glyph = 44.dp
    Box(
        modifier = Modifier.size(outer),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(outer)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (premium) {
                        Modifier.background(
                            Brush.linearGradient(listOf(goldFillStart, goldFillEnd)),
                        )
                    } else {
                        Modifier.background(UiColors.Ai.execBtnBg)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_subscription),
                contentDescription = stringResource(R.string.settings_l1_subscription),
                tint = if (premium) Color.White else UiColors.Ai.execBtnText,
                modifier = Modifier.size(glyph),
            )
        }
        // Premium 徐章：右上角皇冠小圆形
        if (premium) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(goldStroke)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_premium_crown),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    leadingIcon: Int,
    onClick: () -> Unit,
) {
    SettingsSimpleRow(
        SettingsRowModel(
            title = title,
            desc = subtitle,
            trailing = SettingsTrailing.CHEVRON,
            onClick = onClick,
            leadingIcon = leadingIcon,
            rowVerticalPaddingScale = 1.2f,
        ),
    )
}

@Composable
private fun SettingsBackupBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ai_shield),
            contentDescription = null,
            tint = UiColors.Home.navItemActive,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.settings_backup_banner),
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.settingsRowDesc,
            lineHeight = (UiTextSize.settingsRowDesc.value * 1.45).sp,
        )
    }
}
