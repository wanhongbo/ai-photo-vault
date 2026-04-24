package com.photovault.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.feedback.throttledClickable
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import com.photovault.app.ui.theme.UiSize
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.VaultPhoto
import com.photovault.app.ui.vault.VaultStore
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "<",
                color = UiColors.Home.navItemActive,
                modifier = Modifier.throttledClickable(onClick = onBack),
            )
            Text(
                text = stringResource(R.string.vault_search_title),
                color = UiColors.Home.title,
                fontSize = UiTextSize.homeTitle,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.padding(end = 10.dp)) {}
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .border(1.dp, UiColors.Home.emptyCardStroke, RoundedCornerShape(UiRadius.homeThumb)),
            singleLine = true,
            label = { Text(stringResource(R.string.vault_search_input_hint)) },
        )
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
                    Box(
                        modifier = Modifier
                            .size(UiSize.homeThumbSize)
                            .clip(RoundedCornerShape(UiRadius.homeThumb))
                            .throttledClickable { onOpenPhoto(photo.path) },
                    ) {
                        val bmp = remember(photo.path) { android.graphics.BitmapFactory.decodeFile(photo.path) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(UiColors.Home.emptyIconBg))
                        }
                    }
                }
            }
        }
    }
}

