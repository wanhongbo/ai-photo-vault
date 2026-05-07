package com.xpx.vault.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xpx.vault.BuildConfig
import com.xpx.vault.LanguageManager
import com.xpx.vault.R
import com.xpx.vault.ui.backup.AutoBackupScheduler
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun SettingsSubscriptionPlaceholderScreen(
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(R.string.settings_subscription_title), onBack = onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = UiSize.settingsScreenHorizontalPad)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_subscription_placeholder),
                color = UiColors.Home.subtitle,
                fontSize = UiTextSize.settingsRowDesc,
            )
            Spacer(Modifier.height(20.dp))
            AppButton(
                text = stringResource(R.string.settings_subscription_cta),
                onClick = onOpenPaywall,
                variant = AppButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun SettingsSecurityPrivacyScreen(
    onBack: () -> Unit,
    onOpenChangePin: () -> Unit,
) {
    var biometricEnabled by remember { mutableStateOf(true) }
    var autoLockEnabled by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(R.string.settings_l1_security), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = UiSize.settingsScreenHorizontalPad)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSize.settingsSectionGap),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsGroup(
                title = stringResource(R.string.settings_sec_unlock),
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
                        SettingsSimpleRow(
                            SettingsRowModel(
                                title = stringResource(R.string.settings_item_pin_settings),
                                desc = stringResource(R.string.settings_item_pin_settings_desc),
                                trailing = SettingsTrailing.CHEVRON,
                                onClick = onOpenChangePin,
                            ),
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
                items = emptyList(),
            )
            SettingsGroupCard(title = stringResource(R.string.settings_sec_privacy)) {
                SettingsMutedHint(stringResource(R.string.settings_sec_privacy_hint))
            }
        }
    }
}

@Composable
fun SettingsBackupSyncScreen(
    onBack: () -> Unit,
    onOpenBackupRestore: () -> Unit,
) {
    val context = LocalContext.current
    var autoBackupEnabled by remember { mutableStateOf(AutoBackupScheduler.isEnabled(context)) }
    var autoBackupRequireCharging by remember { mutableStateOf(AutoBackupScheduler.isRequireCharging(context)) }
    var autoBackupRequireIdle by remember { mutableStateOf(AutoBackupScheduler.isRequireIdle(context)) }
    val backupSwitches = listOf<@Composable () -> Unit>(
        {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_item_auto_backup),
                desc = stringResource(R.string.settings_item_auto_backup_desc),
                checked = autoBackupEnabled,
                onChange = {
                    autoBackupEnabled = it
                    AutoBackupScheduler.setEnabled(context, it)
                },
            )
        },
        {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_item_auto_backup_charging),
                desc = stringResource(R.string.settings_item_auto_backup_charging_desc),
                checked = autoBackupRequireCharging,
                onChange = {
                    autoBackupRequireCharging = it
                    AutoBackupScheduler.setRequireCharging(context, it)
                },
            )
        },
        {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_item_auto_backup_idle),
                desc = stringResource(R.string.settings_item_auto_backup_idle_desc),
                checked = autoBackupRequireIdle,
                onChange = {
                    autoBackupRequireIdle = it
                    AutoBackupScheduler.setRequireIdle(context, it)
                },
            )
        },
    )
    val manualRows = listOf(
        SettingsRowModel(
            title = stringResource(R.string.settings_item_backup_local_cloud),
            desc = stringResource(R.string.settings_item_backup_local_cloud_desc),
            trailing = SettingsTrailing.CHEVRON,
            onClick = onOpenBackupRestore,
        ),
        SettingsRowModel(
            title = stringResource(R.string.settings_item_restore_from_backup),
            desc = stringResource(R.string.settings_item_restore_from_backup_desc),
            trailing = SettingsTrailing.CHEVRON,
            onClick = onOpenBackupRestore,
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(R.string.settings_l1_backup), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = UiSize.settingsScreenHorizontalPad)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSize.settingsSectionGap),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsGroup(
                title = stringResource(R.string.settings_sec_autobackup),
                customRows = backupSwitches,
                items = emptyList(),
            )
            SettingsGroup(
                title = stringResource(R.string.settings_sec_manual_backup),
                items = manualRows,
            )
        }
    }
}

@Composable
fun SettingsDataStorageScreen(
    onBack: () -> Unit,
    onOpenStorageUsage: () -> Unit,
    onOpenBulkExport: () -> Unit,
    onOpenTrashBin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(R.string.settings_l1_data), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = UiSize.settingsScreenHorizontalPad)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSize.settingsSectionGap),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsGroup(
                title = stringResource(R.string.settings_sec_storage),
                items = listOf(
                    SettingsRowModel(
                        title = stringResource(R.string.settings_item_storage),
                        desc = stringResource(R.string.settings_item_storage_desc),
                        trailing = SettingsTrailing.CHEVRON,
                        onClick = onOpenStorageUsage,
                    ),
                ),
            )
            SettingsGroup(
                title = stringResource(R.string.settings_sec_data_ops),
                items = listOf(
                    SettingsRowModel(
                        title = stringResource(R.string.settings_item_bulk_export),
                        desc = stringResource(R.string.settings_item_bulk_export_desc),
                        trailing = SettingsTrailing.CHEVRON,
                        onClick = onOpenBulkExport,
                    ),
                    SettingsRowModel(
                        title = stringResource(R.string.settings_item_trash),
                        desc = stringResource(R.string.settings_item_trash_desc),
                        trailing = SettingsTrailing.CHEVRON,
                        onClick = onOpenTrashBin,
                    ),
                ),
            )
            SettingsGroupCard(title = stringResource(R.string.settings_danger_reset)) {
                Spacer(Modifier.height(UiSize.settingsGroupTitleToRowsGap))
                SettingsDangerRow(
                    title = stringResource(R.string.settings_danger_reset),
                    desc = stringResource(R.string.settings_danger_reset_desc),
                    onClick = { showClearDialog = true },
                )
            }
        }
    }
    AppDialog(
        show = showClearDialog,
        title = stringResource(R.string.settings_clear_vault_dialog_title),
        message = stringResource(R.string.settings_clear_vault_dialog_message),
        confirmText = stringResource(R.string.settings_clear_vault_confirm),
        dismissText = stringResource(R.string.settings_clear_vault_cancel),
        onConfirm = {
            showClearDialog = false
            scope.launch {
                val n = VaultStore.moveAllVaultPhotosToTrash(context)
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_clear_vault_done, n),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onDismiss = { showClearDialog = false },
        confirmVariant = AppButtonVariant.DANGER,
    )
}

@Composable
fun SettingsGeneralScreen(
    onBack: () -> Unit,
    onOpenLanguageSettings: () -> Unit,
) {
    val context = LocalContext.current
    val currentLanguageCode = LanguageManager.getCurrentLanguage(context)
    val languageDescRes = if (currentLanguageCode == LanguageManager.LANG_ZH) {
        R.string.language_option_chinese
    } else {
        R.string.language_option_english
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(R.string.settings_l1_general), onBack = onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = UiSize.settingsScreenHorizontalPad)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            SettingsGroup(
                title = stringResource(R.string.settings_group_general),
                items = listOf(
                    SettingsRowModel(
                        title = stringResource(R.string.settings_item_language),
                        desc = stringResource(languageDescRes),
                        trailing = SettingsTrailing.CHEVRON,
                        onClick = onOpenLanguageSettings,
                    ),
                ),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_general_reserved_hint),
                color = UiColors.Home.subtitle,
                fontSize = UiTextSize.settingsRowDesc,
            )
        }
    }
}

@Composable
fun SettingsAboutSupportScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val version = BuildConfig.VERSION_NAME
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(R.string.settings_l1_about), onBack = onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = UiSize.settingsScreenHorizontalPad)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            SettingsGroup(
                title = stringResource(R.string.settings_l1_about),
                items = listOf(
                    SettingsRowModel(
                        title = stringResource(R.string.settings_about_version_label),
                        desc = version,
                        trailing = SettingsTrailing.NONE,
                        onClick = {},
                        interactive = false,
                    ),
                    SettingsRowModel(
                        title = stringResource(R.string.settings_about_privacy),
                        desc = stringResource(R.string.settings_about_privacy_desc),
                        trailing = SettingsTrailing.CHEVRON,
                        onClick = {
                            Toast.makeText(context, context.getString(R.string.settings_link_coming_soon), Toast.LENGTH_SHORT).show()
                        },
                    ),
                    SettingsRowModel(
                        title = stringResource(R.string.settings_about_terms),
                        desc = stringResource(R.string.settings_about_terms_desc),
                        trailing = SettingsTrailing.CHEVRON,
                        onClick = {
                            Toast.makeText(context, context.getString(R.string.settings_link_coming_soon), Toast.LENGTH_SHORT).show()
                        },
                    ),
                    SettingsRowModel(
                        title = stringResource(R.string.settings_about_contact),
                        desc = stringResource(R.string.settings_about_contact_desc),
                        trailing = SettingsTrailing.CHEVRON,
                        onClick = {
                            Toast.makeText(context, context.getString(R.string.settings_link_coming_soon), Toast.LENGTH_SHORT).show()
                        },
                    ),
                ),
            )
        }
    }
}
