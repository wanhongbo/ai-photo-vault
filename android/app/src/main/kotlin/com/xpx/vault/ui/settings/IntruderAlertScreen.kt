package com.xpx.vault.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xpx.vault.R
import com.xpx.vault.findAppLockManager
import com.xpx.vault.launchExternalSystemUi
import com.xpx.vault.startExternalActivityForAppLock
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import java.text.DateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IntruderAlertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appLockManager = remember(context) { context.findAppLockManager() }
    fun launchSystemUi(reason: String, launch: () -> Unit) {
        appLockManager?.launchExternalSystemUi(reason, launch) ?: launch()
    }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(IntruderAlertStore.isEnabled(context)) }
    var records by remember { mutableStateOf<List<IntruderAlertRecord>>(emptyList()) }
    var showClearAll by remember { mutableStateOf(false) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        appLockManager?.endExternalSystemUi("intruder camera permission result")
        cameraGranted = granted
        enabled = true
        IntruderAlertStore.setEnabled(context, true)
    }

    fun refresh() {
        scope.launch {
            enabled = IntruderAlertStore.isEnabled(context)
            records = IntruderAlertStore.loadRecords(context)
            cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(R.string.intruder_alert_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = UiSize.settingsScreenHorizontalPad)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiSize.settingsSectionGap),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsGroupCard(title = stringResource(R.string.intruder_alert_intro_title)) {
                Text(
                    text = stringResource(R.string.intruder_alert_intro_desc),
                    color = UiColors.Home.subtitle,
                    fontSize = UiTextSize.settingsRowDesc,
                    modifier = Modifier.padding(top = UiSize.settingsGroupTitleToRowsGap),
                )
                Spacer(Modifier.height(UiSize.settingsRowGap))
                IntruderToggleRow(
                    enabled = enabled,
                    onChange = { checked ->
                        enabled = checked
                        IntruderAlertStore.setEnabled(context, checked)
                        if (checked && !cameraGranted) {
                            launchSystemUi("intruder camera permission") {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                )
            }

            if (enabled && !cameraGranted) {
                SettingsGroupCard(title = stringResource(R.string.intruder_alert_permission_title)) {
                    Text(
                        text = stringResource(R.string.intruder_alert_permission_desc),
                        color = UiColors.Home.subtitle,
                        fontSize = UiTextSize.settingsRowDesc,
                        modifier = Modifier.padding(top = UiSize.settingsGroupTitleToRowsGap),
                    )
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text = stringResource(R.string.intruder_alert_open_settings),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startExternalActivityForAppLock(intent, "app settings")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SettingsGroupCard(title = stringResource(R.string.intruder_alert_records)) {
                if (records.isEmpty()) {
                    Text(
                        text = stringResource(R.string.intruder_alert_empty_desc),
                        color = UiColors.Home.subtitle,
                        fontSize = UiTextSize.settingsRowDesc,
                        modifier = Modifier.padding(top = UiSize.settingsGroupTitleToRowsGap),
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = UiSize.settingsGroupTitleToRowsGap),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        AppButton(
                            text = stringResource(R.string.intruder_alert_clear_all),
                            onClick = { showClearAll = true },
                            variant = AppButtonVariant.DANGER,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(UiSize.settingsRowGap))
                    records.forEachIndexed { index, record ->
                        IntruderRecordRow(record = record)
                        if (index != records.lastIndex) Spacer(Modifier.height(UiSize.settingsRowGap))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    AppDialog(
        show = showClearAll,
        title = stringResource(R.string.intruder_alert_clear_title),
        message = stringResource(R.string.intruder_alert_clear_desc),
        confirmText = stringResource(R.string.intruder_alert_clear_all),
        dismissText = stringResource(R.string.common_cancel),
        confirmVariant = AppButtonVariant.DANGER,
        onConfirm = {
            showClearAll = false
            scope.launch {
                IntruderAlertStore.clearAll(context)
                refresh()
            }
        },
        onDismiss = { showClearAll = false },
    )
}

@Composable
private fun IntruderToggleRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
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
            Text(
                text = stringResource(R.string.intruder_alert_enable_title),
                color = UiColors.Home.emptyTitle,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.intruder_alert_enable_desc),
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.settingsRowDesc,
                modifier = Modifier.padding(top = UiSize.settingsProfileDescTopGap),
            )
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun IntruderRecordRow(record: IntruderAlertRecord) {
    val context = LocalContext.current
    val timeText = remember(record.createdAtMillis) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(record.createdAtMillis)
    }
    val dayText = remember(record.createdAtMillis) {
        val now = System.currentTimeMillis()
        val age = now - record.createdAtMillis
        when {
            android.text.format.DateUtils.isToday(record.createdAtMillis) -> context.getString(R.string.common_today)
            age in 0 until 48L * 60L * 60L * 1000L &&
                !android.text.format.DateUtils.isToday(record.createdAtMillis) -> context.getString(R.string.common_yesterday)
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(record.createdAtMillis)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.settingsRow))
            .background(UiColors.Home.emptyCardBg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IntruderThumbnail(record)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(dayText, color = UiColors.Home.emptyTitle, fontWeight = FontWeight.SemiBold)
            Text(timeText, color = UiColors.Home.emptyBody, fontSize = UiTextSize.settingsRowDesc)
            Text(
                text = stringResource(statusLabel(record.captureStatus)),
                color = UiColors.Home.subtitle,
                fontSize = 11.sp,
            )
        }
        Text(
            text = record.attemptedPin,
            color = UiColors.Home.subtitle,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun IntruderThumbnail(record: IntruderAlertRecord) {
    val context = LocalContext.current
    var bytes by remember(record.id) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(record.id, record.capturePath) {
        bytes = withContext(Dispatchers.IO) { IntruderAlertStore.loadCaptureBytes(context, record) }
    }
    val bitmap = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Home.sectionBg),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.intruder_alert_thumbnail_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_ai_shield),
                contentDescription = null,
                tint = UiColors.Home.navItemActive,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

private fun statusLabel(status: IntruderCaptureStatus): Int = when (status) {
    IntruderCaptureStatus.CAPTURED -> R.string.intruder_alert_status_captured
    IntruderCaptureStatus.PERMISSION_DENIED -> R.string.intruder_alert_status_permission
    IntruderCaptureStatus.UNAVAILABLE -> R.string.intruder_alert_status_unavailable
    IntruderCaptureStatus.FAILED -> R.string.intruder_alert_status_failed
}
