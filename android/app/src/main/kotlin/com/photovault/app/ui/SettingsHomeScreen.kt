package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun SettingsHomeScreen(
    onOpenTab: (HomeTab) -> Unit,
) {
    val tabs = remember { homeTabs() }
    var biometricEnabled by remember { mutableStateOf(true) }
    var autoLockEnabled by remember { mutableStateOf(true) }
    val securityItems = listOf(
        SettingsRowModel(
            title = stringResource(R.string.settings_item_change_pin),
            desc = stringResource(R.string.settings_item_change_pin_desc),
            trailing = SettingsTrailing.CHEVRON,
        ),
    )
    val generalItems = listOf(
        SettingsRowModel(
            title = stringResource(R.string.settings_item_language),
            desc = stringResource(R.string.settings_item_language_desc),
            trailing = SettingsTrailing.CHEVRON,
        ),
        SettingsRowModel(
            title = stringResource(R.string.settings_item_storage),
            desc = stringResource(R.string.settings_item_storage_desc),
            trailing = SettingsTrailing.CHEVRON,
        ),
    )
    val aboutItems = listOf(
        SettingsRowModel(
            title = stringResource(R.string.settings_item_version),
            desc = stringResource(R.string.settings_item_version_desc),
            trailing = SettingsTrailing.TEXT,
        ),
    )

    Column(
        modifier = Modifier
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
        Text(
            text = stringResource(R.string.settings_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
            modifier = Modifier.padding(top = UiSize.settingsSubtitleTopGap),
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = UiSize.settingsListTopGap),
            verticalArrangement = Arrangement.spacedBy(UiSize.settingsSectionGap),
        ) {
            item { SettingsProfileCard() }
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_group_security),
                    customRows = listOf(
                        {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_item_biometric),
                                desc = stringResource(R.string.settings_item_biometric_desc),
                                checked = biometricEnabled,
                                onChange = { biometricEnabled = it },
                            )
                        },
                        {
                            SettingsSwitchRow(
                                title = stringResource(R.string.settings_item_auto_lock),
                                desc = stringResource(R.string.settings_item_auto_lock_desc),
                                checked = autoLockEnabled,
                                onChange = { autoLockEnabled = it },
                            )
                        },
                    ),
                    items = securityItems,
                )
            }
            item { SettingsGroup(title = stringResource(R.string.settings_group_general), items = generalItems) }
            item { SettingsGroup(title = stringResource(R.string.settings_group_about), items = aboutItems) }
            item { SettingsDangerCard() }
        }
        HomeBottomNav(
            tabs = tabs,
            selectedIndex = HomeTab.SETTINGS.ordinal,
            onSelect = { onOpenTab(tabs[it].tab) },
        )
    }
}

@Composable
private fun SettingsProfileCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.settingsCardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(UiSize.settingsAvatarSize)
                .background(UiColors.Home.navItemActiveBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "VS",
                color = UiColors.Home.navItemActive,
                fontWeight = FontWeight.Bold,
                fontSize = UiTextSize.settingsAvatar,
            )
        }
        Column(modifier = Modifier.padding(start = UiSize.settingsAvatarGap)) {
            Text(
                text = stringResource(R.string.settings_profile_name),
                color = UiColors.Home.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_profile_desc),
                color = UiColors.Home.subtitle,
                fontSize = UiTextSize.settingsProfileDesc,
                modifier = Modifier.padding(top = UiSize.settingsProfileDescTopGap),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    items: List<SettingsRowModel>,
    customRows: List<@Composable () -> Unit> = emptyList(),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.settingsCardPadding),
    ) {
        Text(text = title, color = UiColors.Home.title, fontWeight = FontWeight.SemiBold)
        customRows.forEachIndexed { index, row ->
            Spacer(modifier = Modifier.height(if (index == 0) UiSize.settingsGroupTitleToRowsGap else UiSize.settingsRowGap))
            row()
        }
        if (customRows.isNotEmpty() && items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(UiSize.settingsRowGap))
        }
        items.forEachIndexed { index, item ->
            SettingsSimpleRow(item)
            if (index != items.lastIndex) Spacer(modifier = Modifier.height(UiSize.settingsRowGap))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.settingsRow))
            .background(UiColors.Home.emptyCardBg)
            .padding(
                horizontal = UiSize.settingsRowPaddingHorizontal,
                vertical = UiSize.settingsRowPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = UiColors.Home.emptyTitle, fontWeight = FontWeight.Medium)
            Text(
                text = desc,
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.settingsRowDesc,
                modifier = Modifier.padding(top = UiSize.settingsProfileDescTopGap),
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsSimpleRow(model: SettingsRowModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.settingsRow))
            .background(UiColors.Home.emptyCardBg)
            .clickable { }
            .padding(
                horizontal = UiSize.settingsRowPaddingHorizontal,
                vertical = UiSize.settingsRowPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = model.title, color = UiColors.Home.emptyTitle, fontWeight = FontWeight.Medium)
            Text(
                text = model.desc,
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.settingsRowDesc,
                modifier = Modifier.padding(top = UiSize.settingsProfileDescTopGap),
            )
        }
        when (model.trailing) {
            SettingsTrailing.CHEVRON -> Text(">", color = UiColors.Home.navItemIdle, fontWeight = FontWeight.Bold)
            SettingsTrailing.TEXT -> Text(model.desc, color = UiColors.Home.subtitle, fontSize = UiTextSize.settingsRowDesc, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun SettingsDangerCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Lock.error, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.settingsCardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_danger_reset),
                color = UiColors.Lock.error,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_danger_reset_desc),
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.settingsDangerDesc,
                modifier = Modifier.padding(top = UiSize.settingsDangerDescTopGap),
            )
        }
        Text(">", color = UiColors.Lock.error, fontWeight = FontWeight.Bold)
    }
}

private data class SettingsRowModel(
    val title: String,
    val desc: String,
    val trailing: SettingsTrailing,
)

private enum class SettingsTrailing {
    CHEVRON,
    TEXT,
}

