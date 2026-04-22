package com.photovault.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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

    LaunchedEffect(Unit) {
        vaultItems = loadVaultItems(context)
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importPickedImageToVault(context, uri)
                vaultItems = loadVaultItems(context)
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
            if (currentTab.tab == HomeTab.VAULT && !hasAlbumPermission) {
                HomeAlbumPermissionState(
                    onGrant = { permissionLauncher.launch(requiredAlbumPermissions()) },
                    onOpenSettings = { openAppSettings(context) },
                    permanentlyDenied = permanentlyDenied,
                )
            } else if (currentTab.tab == HomeTab.VAULT && vaultItems.isNotEmpty()) {
                VaultAlbumList(vaultItems = vaultItems)
            } else {
                HomeEmptyState(
                    tab = currentTab,
                    onAction = {
                        when (currentTab.tab) {
                            HomeTab.VAULT -> pickerLauncher.launch(
                                PickVisualMediaRequest.Builder()
                                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    .build(),
                            )
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
                        .background(UiColors.Home.emptyIconBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_home_nav_vault),
                        contentDescription = null,
                        tint = UiColors.Home.navItemActive,
                        modifier = Modifier.size(20.dp),
                    )
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

private suspend fun importPickedImageToVault(context: Context, uri: Uri) {
    withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "vault_album")
        if (!targetDir.exists()) targetDir.mkdirs()
        val fileName = "asset_${System.currentTimeMillis()}.jpg"
        val target = File(targetDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
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
