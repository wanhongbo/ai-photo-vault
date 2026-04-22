package com.photovault.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.feedback.pressFeedback
import com.photovault.app.ui.feedback.rememberFeedbackInteractionSource
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen() {
    var selectedTab by remember { mutableIntStateOf(HomeTab.VAULT.ordinal) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val hostActivity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var hasAlbumPermission by remember {
        mutableStateOf(checkAlbumReadPermission(context))
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var vaultItems by remember { mutableStateOf(emptyList<VaultItem>()) }
    var importing by remember { mutableStateOf(false) }
    var importTip by remember { mutableStateOf<ImportTip?>(null) }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vaultItems = loadVaultItems(context)
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                val result = importPickedImageToVault(context, uri)
                vaultItems = loadVaultItems(context)
                importTip = when (result) {
                    ImportResult.ADDED -> ImportTip(
                        message = context.getString(R.string.home_import_success),
                        isError = false,
                    )
                    ImportResult.DUPLICATE -> ImportTip(
                        message = context.getString(R.string.home_import_duplicate),
                        isError = false,
                    )
                    ImportResult.FAILED -> ImportTip(
                        message = context.getString(R.string.home_import_failed),
                        isError = true,
                    )
                }
                importing = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAlbumPermission = checkAlbumReadPermission(context)
        permanentlyDenied = !hasAlbumPermission && isPermanentlyDenied(hostActivity)
    }
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
    val filteredVaultItems = remember(vaultItems, searchQuery) {
        if (searchQuery.isBlank()) {
            vaultItems
        } else {
            vaultItems.filter { item ->
                item.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val triggerImportFromLibrary = {
        selectedTab = HomeTab.VAULT.ordinal
        if (hasAlbumPermission && !importing) {
            importTip = null
            pickerLauncher.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build(),
            )
        }
    }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            }
            HeaderActionButton(
                iconRes = R.drawable.ic_home_action_search,
                contentDesc = stringResource(R.string.home_action_search),
                onClick = {
                    searchMode = !searchMode
                    if (!searchMode) searchQuery = ""
                },
            )
            Spacer(modifier = Modifier.size(8.dp))
            HeaderActionButton(
                iconRes = R.drawable.ic_home_action_add,
                contentDesc = stringResource(R.string.home_action_add),
                onClick = triggerImportFromLibrary,
            )
        }
        if (searchMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                singleLine = true,
                label = { Text(stringResource(R.string.home_search_hint)) },
            )
        }
        importTip?.let { tip ->
            Text(
                text = tip.message,
                color = if (tip.isError) UiColors.Lock.error else UiColors.Lock.success,
                fontSize = UiTextSize.homeNavLabel,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (currentTab.tab == HomeTab.VAULT && !hasAlbumPermission) {
                HomeAlbumPermissionState(
                    onGrant = { permissionLauncher.launch(requiredAlbumPermissions()) },
                    onOpenSettings = { openAppSettings(context) },
                    permanentlyDenied = permanentlyDenied,
                )
            } else if (currentTab.tab == HomeTab.VAULT && filteredVaultItems.isNotEmpty()) {
                VaultAlbumList(vaultItems = filteredVaultItems)
            } else {
                HomeEmptyState(
                    tab = currentTab,
                    isLoading = importing,
                    onAction = {
                        when (currentTab.tab) {
                            HomeTab.VAULT -> triggerImportFromLibrary()
                            else -> Unit
                        }
                    },
                )
            }
        }

        HomeBottomNav(
            tabs = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
    }
}

@Composable
private fun HeaderActionButton(
    iconRes: Int,
    contentDesc: String,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(UiColors.Home.navBarBg)
            .border(1.dp, UiColors.Home.navBarStroke, RoundedCornerShape(12.dp))
            .pressFeedback(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDesc,
            tint = UiColors.Home.navItemActive,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HomeAlbumPermissionState(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    permanentlyDenied: Boolean,
) {
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
                painter = painterResource(R.drawable.ic_home_album_permission),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(UiSize.homeEmptyIcon),
            )
        }
        Text(
            text = stringResource(R.string.home_permission_title),
            color = UiColors.Home.emptyTitle,
            fontSize = UiTextSize.homeEmptyTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = if (permanentlyDenied) {
                stringResource(R.string.home_permission_denied_desc)
            } else {
                stringResource(R.string.home_permission_desc)
            },
            color = UiColors.Home.emptyBody,
            fontSize = UiTextSize.homeEmptyBody,
            modifier = Modifier.padding(top = 10.dp),
        )
        AppButton(
            text = if (permanentlyDenied) {
                stringResource(R.string.home_permission_settings)
            } else {
                stringResource(R.string.home_permission_grant)
            },
            onClick = if (permanentlyDenied) onOpenSettings else onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        )
        if (permanentlyDenied) {
            AppButton(
                text = stringResource(R.string.home_permission_later),
                onClick = {},
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun HomeEmptyState(
    tab: HomeNavTab,
    isLoading: Boolean,
    onAction: () -> Unit,
) {
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
            onClick = onAction,
            loading = isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        )
    }
}

@Composable
private fun VaultAlbumList(
    vaultItems: List<VaultItem>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(vaultItems, key = { it.path }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(UiRadius.homeCard))
                    .background(UiColors.Home.emptyCardBg)
                    .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeCard))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UiColors.Home.emptyIconBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = remember(item.path) { BitmapFactory.decodeFile(item.path) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_home_nav_vault),
                            contentDescription = null,
                            tint = UiColors.Home.navItemActive,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = item.name,
                        color = UiColors.Home.emptyTitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = item.path,
                        color = UiColors.Home.emptyBody,
                        fontSize = UiTextSize.homeNavLabel,
                    )
                }
            }
        }
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

private data class VaultItem(
    val name: String,
    val path: String,
)

private data class ImportTip(
    val message: String,
    val isError: Boolean,
)

private enum class ImportResult {
    ADDED,
    DUPLICATE,
    FAILED,
}

private fun requiredAlbumPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun checkAlbumReadPermission(context: android.content.Context): Boolean {
    return requiredAlbumPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isPermanentlyDenied(activity: Activity?): Boolean {
    if (activity == null) return false
    return requiredAlbumPermissions().any { permission ->
        ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
            !activity.shouldShowRequestPermissionRationale(permission)
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private suspend fun importPickedImageToVault(context: Context, uri: Uri): ImportResult {
    return withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "vault_album")
        if (!targetDir.exists()) targetDir.mkdirs()
        val tempFile = File(targetDir, "tmp_${System.currentTimeMillis()}.jpg")
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext ImportResult.FAILED

        input.use { stream ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                }
            }
        }
        val hash = digest.digest().joinToString("") { b -> "%02x".format(b) }
        val finalFile = File(targetDir, "asset_$hash.jpg")
        if (finalFile.exists()) {
            tempFile.delete()
            return@withContext ImportResult.DUPLICATE
        }
        val renamed = tempFile.renameTo(finalFile)
        if (!renamed) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }
        ImportResult.ADDED
    }
}

private suspend fun loadVaultItems(context: Context): List<VaultItem> {
    return withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "vault_album")
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file -> VaultItem(name = file.nameWithoutExtension, path = file.absolutePath) }
            ?: emptyList()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
