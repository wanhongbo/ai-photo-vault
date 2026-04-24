package com.photovault.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun VaultProgressiveImage(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailMaxPx: Int = 360,
    loadHighQuality: Boolean = false,
) {
    var thumbnail by remember(path, thumbnailMaxPx) { mutableStateOf<Bitmap?>(null) }
    var highQuality by remember(path, loadHighQuality) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path, thumbnailMaxPx, loadHighQuality) {
        thumbnail = decodeSampled(path, max(128, thumbnailMaxPx))
        if (loadHighQuality) {
            highQuality = decodeOriginal(path)
        } else {
            highQuality = null
        }
    }

    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1F2F47), Color(0xFF101C31)),
            ),
        ),
    ) {
        val bitmap = highQuality ?: thumbnail
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        }
    }
}

private suspend fun decodeSampled(path: String, targetMaxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, boundsOptions)
    val sourceWidth = boundsOptions.outWidth
    val sourceHeight = boundsOptions.outHeight
    if (sourceWidth <= 0 || sourceHeight <= 0) return@withContext null

    var inSampleSize = 1
    val longestSide = max(sourceWidth, sourceHeight)
    while (longestSide / inSampleSize > targetMaxPx) {
        inSampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
    BitmapFactory.decodeFile(path, decodeOptions)
}

private suspend fun decodeOriginal(path: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        BitmapFactory.decodeFile(path)
    }.getOrNull()
}
