package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.feedback.pressFeedback
import com.photovault.app.ui.feedback.rememberFeedbackInteractionSource
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun HomeScreen() {
    var selectedTab by remember { mutableIntStateOf(HomeTab.VAULT.ordinal) }
    val tabs = remember {
        listOf(
            HomeNavTab(
                tab = HomeTab.VAULT,
                iconRes = R.drawable.ic_home_nav_vault,
                labelRes = R.string.home_nav_vault,
                emptyTitleRes = R.string.home_vault_empty_title,
                emptyDescRes = R.string.home_vault_empty_desc,
                emptyActionRes = R.string.home_vault_empty_action,
            ),
            HomeNavTab(
                tab = HomeTab.CAMERA,
                iconRes = R.drawable.ic_home_nav_camera,
                labelRes = R.string.home_nav_camera,
                emptyTitleRes = R.string.home_camera_empty_title,
                emptyDescRes = R.string.home_camera_empty_desc,
                emptyActionRes = R.string.home_camera_empty_action,
            ),
            HomeNavTab(
                tab = HomeTab.AI,
                iconRes = R.drawable.ic_home_nav_ai,
                labelRes = R.string.home_nav_ai,
                emptyTitleRes = R.string.home_ai_empty_title,
                emptyDescRes = R.string.home_ai_empty_desc,
                emptyActionRes = R.string.home_ai_empty_action,
            ),
            HomeNavTab(
                tab = HomeTab.SETTINGS,
                iconRes = R.drawable.ic_home_nav_settings,
                labelRes = R.string.home_nav_settings,
                emptyTitleRes = R.string.home_settings_empty_title,
                emptyDescRes = R.string.home_settings_empty_desc,
                emptyActionRes = R.string.home_settings_empty_action,
            ),
        )
    }
    val currentTab = tabs.getOrNull(selectedTab) ?: tabs.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(UiColors.Home.bgTop, UiColors.Home.bgBottom),
                ),
            )
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
            modifier = Modifier.padding(top = 6.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HomeEmptyState(currentTab)
        }

        HomeBottomNav(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
    }
}

@Composable
private fun HomeEmptyState(tab: HomeNavTab) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.emptyCardBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(UiSize.homeEmptyIconWrap)
                .background(UiColors.Home.emptyIconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.shield_check),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(UiSize.homeEmptyIcon),
            )
        }
        Text(
            text = stringResource(tab.emptyTitleRes),
            color = UiColors.Home.emptyTitle,
            fontSize = UiTextSize.homeEmptyTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(tab.emptyDescRes),
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.homeEmptyBody,
            modifier = Modifier.padding(top = 10.dp),
        )
        AppButton(
            text = stringResource(tab.emptyActionRes),
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        )
    }
}

@Composable
private fun HomeBottomNav(
    tabs: List<HomeNavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UiSize.homeNavBarHeight)
            .clip(RoundedCornerShape(UiRadius.homeNavBar))
            .background(UiColors.Home.navBarBg)
            .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(UiRadius.homeNavBar))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { idx, tab ->
            val selected = idx == selectedIndex
            val interaction = rememberFeedbackInteractionSource()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(UiRadius.homeNavItem))
                    .background(if (selected) UiColors.Home.navItemActiveBg else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (selected) UiColors.Home.navItemActiveStroke else Color.Transparent,
                        shape = RoundedCornerShape(UiRadius.homeNavItem),
                    )
                    .pressFeedback(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { onSelect(idx) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(tab.iconRes),
                    contentDescription = stringResource(tab.labelRes),
                    tint = if (selected) UiColors.Home.navItemActive else UiColors.Home.navItemIdle,
                    modifier = Modifier.size(UiSize.homeNavIcon),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(tab.labelRes),
                    color = if (selected) UiColors.Home.navItemActive else UiColors.Home.navItemIdle,
                    fontSize = UiTextSize.homeNavLabel,
                )
            }
        }
    }
}

private data class HomeNavTab(
    val tab: HomeTab,
    val iconRes: Int,
    val labelRes: Int,
    val emptyTitleRes: Int,
    val emptyDescRes: Int,
    val emptyActionRes: Int,
)

private enum class HomeTab {
    VAULT,
    CAMERA,
    AI,
    SETTINGS,
}
