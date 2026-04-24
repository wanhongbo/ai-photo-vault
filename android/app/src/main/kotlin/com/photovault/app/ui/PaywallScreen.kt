package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun PaywallScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "<",
                color = UiColors.Home.navItemActive,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Text(
                text = stringResource(R.string.paywall_title),
                color = UiColors.Home.title,
                fontSize = UiTextSize.homeTitle,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = stringResource(R.string.paywall_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                .padding(UiSize.homeCardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.paywall_feature_backup), color = UiColors.Home.title)
            Text(stringResource(R.string.paywall_feature_ai), color = UiColors.Home.title)
            Text(stringResource(R.string.paywall_feature_ads), color = UiColors.Home.title)
        }
        AppButton(
            text = stringResource(R.string.paywall_action_upgrade),
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

