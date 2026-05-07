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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpx.vault.R
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
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
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_security),
                    subtitle = stringResource(R.string.settings_l1_security_sub),
                    onClick = { onOpenSettingsHub(SettingsHubDestination.SECURITY_PRIVACY) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_backup),
                    subtitle = stringResource(R.string.settings_l1_backup_sub),
                    onClick = { onOpenSettingsHub(SettingsHubDestination.BACKUP_SYNC) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_data),
                    subtitle = stringResource(R.string.settings_l1_data_sub),
                    onClick = { onOpenSettingsHub(SettingsHubDestination.DATA_STORAGE) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_general),
                    subtitle = stringResource(R.string.settings_l1_general_sub),
                    onClick = { onOpenSettingsHub(SettingsHubDestination.GENERAL) },
                )
            }
            item {
                SettingsHubRow(
                    title = stringResource(R.string.settings_l1_about),
                    subtitle = stringResource(R.string.settings_l1_about_sub),
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
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Ai.featureCardBg)
            .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .pressFeedback(interaction)
            .throttledClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(UiSize.settingsCardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithSubscriptionIcon()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = UiSize.settingsAvatarGap),
        ) {
            Text(
                text = stringResource(R.string.settings_l1_subscription),
                color = UiColors.Ai.featureTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(
                text = stringResource(R.string.settings_l1_subscription_sub),
                color = UiColors.Ai.featureDesc,
                fontSize = UiTextSize.settingsRowDesc,
                modifier = Modifier.padding(top = UiSize.settingsProfileDescTopGap),
            )
        }
        Text(">", color = UiColors.Home.navItemActive, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BoxWithSubscriptionIcon() {
    Box(
        modifier = Modifier
            .size(UiSize.settingsAvatarSize)
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Ai.execBtnBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ai_zap),
            contentDescription = null,
            tint = UiColors.Ai.execBtnText,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SettingsSimpleRow(
        SettingsRowModel(
            title = title,
            desc = subtitle,
            trailing = SettingsTrailing.CHEVRON,
            onClick = onClick,
        ),
    )
}
