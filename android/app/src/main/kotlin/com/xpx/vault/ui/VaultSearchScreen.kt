package com.xpx.vault.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.components.VaultProgressiveImage
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiRadius
import com.xpx.vault.ui.theme.UiSize
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultPhoto
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.launch

@Composable
fun VaultSearchScreen(
    onOpenPhoto: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf(emptyList<VaultPhoto>()) }

    LaunchedEffect(Unit) { VaultStore.ensureInit(context) }
    LaunchedEffect(query) {
        scope.launch { result = VaultStore.searchPhotos(context, query) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        AppTopBar(title = stringResource(R.string.vault_search_title), onBack = onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(UiColors.Home.emptyCardBg)
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_home_action_search),
                contentDescription = null,
                tint = UiColors.Home.subtitle,
                modifier = Modifier.size(18.dp),
            )
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = UiColors.Home.title,
                    fontSize = UiTextSize.homeEmptyBody,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.vault_search_input_hint),
                            color = UiColors.Home.subtitle,
                            fontSize = UiTextSize.homeEmptyBody,
                        )
                    }
                    innerTextField()
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(UiRadius.homeCard))
                .background(UiColors.Home.sectionBg)
                .padding(UiSize.homeCardPadding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
                verticalArrangement = Arrangement.spacedBy(UiSize.homeGridGap),
            ) {
                items(result, key = { it.path }) { photo ->
                    VaultProgressiveImage(
                        path = photo.path,
                        modifier = Modifier
                            .size(UiSize.homeThumbSize)
                            .clip(RoundedCornerShape(UiRadius.homeThumb))
                            .throttledClickable { onOpenPhoto(photo.path) },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        thumbnailMaxPx = 360,
                    )
                }
            }
        }
    }
}

