package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xpx.vault.LanguageManager
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LanguageManager.getCurrentLanguage(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(UiSize.backupScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiSize.backupSectionGap),
    ) {
        AppTopBar(title = stringResource(R.string.language_settings_title), onBack = onBack)
        Text(
            text = stringResource(R.string.language_settings_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
            modifier = Modifier.padding(top = UiSize.backupSubtitleTopGap),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                .padding(UiSize.backupCardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LanguageOptionRow(
                label = stringResource(R.string.language_option_english),
                selected = selected == LanguageManager.LANG_EN,
                onClick = {
                    selected = LanguageManager.LANG_EN
                    LanguageManager.setLanguage(context, LanguageManager.LANG_EN)
                },
            )
            LanguageOptionRow(
                label = stringResource(R.string.language_option_chinese),
                selected = selected == LanguageManager.LANG_ZH,
                onClick = {
                    selected = LanguageManager.LANG_ZH
                    LanguageManager.setLanguage(context, LanguageManager.LANG_ZH)
                },
            )
        }
    }
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiColors.Home.emptyCardBg, RoundedCornerShape(UiRadius.settingsRow))
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.settingsRow))
            .throttledClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeEmptyBody,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = stringResource(R.string.language_selected_mark),
                color = UiColors.Home.navItemActive,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
