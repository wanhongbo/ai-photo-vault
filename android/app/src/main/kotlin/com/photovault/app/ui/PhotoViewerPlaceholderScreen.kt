package com.photovault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.components.VaultProgressiveImage
import com.photovault.app.ui.theme.UiColors

@Composable
fun PhotoViewerPlaceholderScreen(path: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.photo_viewer_placeholder_title))
        VaultProgressiveImage(
            path = path,
            modifier = Modifier.fillMaxWidth(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            thumbnailMaxPx = 1080,
            loadHighQuality = true,
        )
    }
}

