package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun SettingsHomeScreen(
    onOpenTab: (HomeTab) -> Unit,
) {
    val tabs = remember { homeTabs() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(20.dp),
    ) {
        Text("设置", color = UiColors.Home.title, fontSize = UiTextSize.homeTitle, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 16.dp)
                .background(UiColors.Home.emptyCardBg, RoundedCornerShape(UiRadius.homeCard))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard)),
            contentAlignment = Alignment.Center,
        ) {
            Text("设置页空状态", color = UiColors.Home.emptyBody)
        }
        HomeBottomNav(tabs = tabs, selectedIndex = HomeTab.SETTINGS.ordinal, onSelect = { onOpenTab(tabs[it].tab) })
    }
}

