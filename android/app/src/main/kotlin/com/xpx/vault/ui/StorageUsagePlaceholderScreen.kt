package com.xpx.vault.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StorageUsagePlaceholderScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var usage by remember { mutableStateOf<StorageUsage?>(null) }

    LaunchedEffect(Unit) {
        usage = withContext(Dispatchers.IO) { calculateStorageUsage(context) }
    }

    val loading = stringResource(R.string.settings_storage_loading)
    val photoText = usage?.let { formatStorageSize(it.photos) } ?: loading
    val backupText = usage?.let { formatStorageSize(it.backup) } ?: loading
    val trashText = usage?.let { formatStorageSize(it.trash) } ?: loading
    val cacheText = usage?.let { formatStorageSize(it.cache) } ?: loading
    val totalText = usage?.let { formatStorageSize(it.total) } ?: loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTopBar(title = stringResource(R.string.settings_storage_title), onBack = onBack)
        Text(
            text = stringResource(R.string.settings_storage_subtitle),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeSubtitle,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(UiColors.Home.sectionBg, RoundedCornerShape(UiRadius.homeCard))
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                .padding(UiSize.homeCardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_storage_photo, photoText),
                color = UiColors.Home.title,
            )
            Text(
                text = stringResource(R.string.settings_storage_backup, backupText),
                color = UiColors.Home.title,
            )
            Text(
                text = stringResource(R.string.settings_storage_trash, trashText),
                color = UiColors.Home.title,
            )
            Text(
                text = stringResource(R.string.settings_storage_cache, cacheText),
                color = UiColors.Home.title,
            )
            Text(
                text = stringResource(R.string.settings_storage_total, totalText),
                color = UiColors.Home.title,
            )
            Text(
                text = stringResource(R.string.settings_storage_placeholder_desc),
                color = UiColors.Home.emptyBody,
                fontSize = UiTextSize.homeEmptyBody,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        AppButton(
            text = stringResource(R.string.common_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class StorageUsage(
    val photos: Long,
    val backup: Long,
    val trash: Long,
    val cache: Long,
) {
    val total: Long get() = photos + backup + trash + cache
}

private fun calculateStorageUsage(context: Context): StorageUsage {
    val base = context.filesDir
    val photos = dirSizeBytes(File(base, "vault_albums")) +
        dirSizeBytes(File(base, "vault_album"))
    val backup = dirSizeBytes(File(base, "vault_backups_mvp"))
    val trash = dirSizeBytes(File(base, "vault_trash"))
    val cache = dirSizeBytes(context.cacheDir)
    return StorageUsage(photos = photos, backup = backup, trash = trash, cache = cache)
}

private fun dirSizeBytes(dir: File): Long {
    if (!dir.exists()) return 0L
    var total = 0L
    dir.walkTopDown().forEach { f ->
        if (f.isFile) total += f.length()
    }
    return total
}

private fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.2f KB", bytes / kb)
        else -> "$bytes B"
    }
}
