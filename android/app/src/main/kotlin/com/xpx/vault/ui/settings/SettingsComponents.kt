package com.xpx.vault.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize

@Composable
fun SettingsGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.homeCard))
            .background(UiColors.Home.sectionBg)
            .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
            .padding(UiSize.settingsCardPadding),
    ) {
        Text(text = title, color = UiColors.Home.title, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun SettingsGroup(
    title: String,
    items: List<SettingsRowModel>,
    customRows: List<@Composable () -> Unit> = emptyList(),
    modifier: Modifier = Modifier,
) {
    SettingsGroupCard(title = title, modifier = modifier) {
        customRows.forEachIndexed { index, row ->
            Spacer(modifier = Modifier.height(if (index == 0) UiSize.settingsGroupTitleToRowsGap else UiSize.settingsRowGap))
            row()
        }
        if (customRows.isNotEmpty() && items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(UiSize.settingsRowGap))
        }
        items.forEachIndexed { index, item ->
            if (index == 0 && customRows.isEmpty()) {
                Spacer(modifier = Modifier.height(UiSize.settingsGroupTitleToRowsGap))
            }
            SettingsSimpleRow(item)
            if (index != items.lastIndex) Spacer(modifier = Modifier.height(UiSize.settingsRowGap))
        }
    }
}

@Composable
fun SettingsSwitchRow(
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
fun SettingsSimpleRow(model: SettingsRowModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.settingsRow))
            .background(UiColors.Home.emptyCardBg)
            .then(
                if (model.interactive) {
                    Modifier.throttledClickable(onClick = model.onClick)
                } else {
                    Modifier
                },
            )
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
            SettingsTrailing.TEXT -> Text(
                model.desc,
                color = UiColors.Home.subtitle,
                fontSize = UiTextSize.settingsRowDesc,
                fontStyle = FontStyle.Italic,
            )
            SettingsTrailing.NONE -> Unit
        }
    }
}

@Composable
fun SettingsDangerRow(
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.settingsRow))
            .background(UiColors.Home.emptyCardBg)
            .throttledClickable(onClick = onClick)
            .padding(
                horizontal = UiSize.settingsRowPaddingHorizontal,
                vertical = UiSize.settingsRowPaddingVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = UiColors.Lock.error, fontWeight = FontWeight.SemiBold)
            Text(
                text = desc,
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.settingsDangerDesc,
                modifier = Modifier.padding(top = UiSize.settingsDangerDescTopGap),
            )
        }
        Text(">", color = UiColors.Lock.error, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsMutedHint(text: String) {
    Text(
        text = text,
        color = UiColors.Home.subtitle,
        fontSize = UiTextSize.settingsRowDesc,
        modifier = Modifier.padding(top = UiSize.settingsGroupTitleToRowsGap),
    )
}

data class SettingsRowModel(
    val title: String,
    val desc: String,
    val trailing: SettingsTrailing,
    val onClick: () -> Unit,
    val interactive: Boolean = true,
)

enum class SettingsTrailing {
    CHEVRON,
    TEXT,
    NONE,
}
