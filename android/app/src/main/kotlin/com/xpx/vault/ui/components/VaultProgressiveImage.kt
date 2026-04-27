package com.xpx.vault.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import com.xpx.vault.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun VaultProgressiveImage(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailMaxPx: Int = 360,
    loadHighQuality: Boolean = false,
    highQualityMaxPx: Int? = null,
    showVideoIndicator: Boolean = false,
) {
    val isVideo = remember(path) { isVideoPath(path) }
    var videoDurationMs by remember(path) { mutableStateOf(0L) }
    var thumbnail by remember(path, thumbnailMaxPx) { mutableStateOf<Bitmap?>(null) }
    var highQuality by remember(path, loadHighQuality, highQualityMaxPx) { mutableStateOf<Bitmap?>(null) }
    var allowBreathing by remember(path, thumbnailMaxPx, loadHighQuality, highQualityMaxPx) { mutableStateOf(false) }

    LaunchedEffect(path, thumbnailMaxPx, loadHighQuality, highQualityMaxPx) {
        allowBreathing = false
        videoDurationMs = if (isVideo) readVideoDurationMs(path) else 0L
        // Start subtle breathing only when loading exceeds 300ms.
        launch {
            delay(300)
            allowBreathing = true
        }
        thumbnail = if (isVideo) {
            decodeVideoFrame(path, max(128, thumbnailMaxPx))
        } else {
            decodeSampled(path, max(128, thumbnailMaxPx))
        }
        if (loadHighQuality) {
            highQuality = if (highQualityMaxPx != null) {
                if (isVideo) {
                    decodeVideoFrame(path, max(thumbnailMaxPx, highQualityMaxPx))
                } else {
                    decodeSampled(path, max(thumbnailMaxPx, highQualityMaxPx))
                }
            } else {
                if (isVideo) decodeVideoFrame(path, max(720, thumbnailMaxPx)) else decodeOriginal(path)
            }
        } else {
            highQuality = null
        }
    }
    val bitmap = highQuality ?: thumbnail
    val breathingEnabled = allowBreathing && bitmap == null
    val breathingAlpha by if (breathingEnabled) {
        val transition = rememberInfiniteTransition(label = "placeholderBreathing")
        transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "placeholderAlpha",
        )
    } else {
        rememberUpdatedState(1f)
    }
    val breathingScale by if (breathingEnabled) {
        val transition = rememberInfiniteTransition(label = "placeholderScaleBreathing")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "placeholderScale",
        )
    } else {
        rememberUpdatedState(1f)
    }

    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = if (isVideo) {
                    listOf(Color(0xFF202030), Color(0xFF141820), Color(0xFF0E131C))
                } else {
                    listOf(Color(0xFF1E304B), Color(0xFF13233A), Color(0xFF0D1729))
                },
            ),
        ),
    ) {
        // Rich placeholder graphics: soft glow circles + diagonal highlight + outline.
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(
                    alpha = breathingAlpha,
                    scaleX = breathingScale,
                    scaleY = breathingScale,
                ),
        ) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isVideo) listOf(Color(0x40A98BFF), Color.Transparent) else listOf(Color(0x4057A8FF), Color.Transparent),
                    center = Offset(w * 0.25f, h * 0.3f),
                    radius = max(w, h) * 0.45f,
                ),
                radius = max(w, h) * 0.45f,
                center = Offset(w * 0.25f, h * 0.3f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isVideo) listOf(Color(0x2A8B9DFF), Color.Transparent) else listOf(Color(0x2A78D0FF), Color.Transparent),
                    center = Offset(w * 0.78f, h * 0.74f),
                    radius = max(w, h) * 0.38f,
                ),
                radius = max(w, h) * 0.38f,
                center = Offset(w * 0.78f, h * 0.74f),
            )
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color(0x18FFFFFF), Color.Transparent),
                    start = Offset(w * -0.25f, h * 0.25f),
                    end = Offset(w * 0.85f, h * 0.95f),
                ),
            )
            drawRect(
                color = Color(0x33FFFFFF),
                style = Stroke(width = 1.2f),
            )
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
            )
        }
        if (isVideo && showVideoIndicator) {
            Box(modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_video_play_badge),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.Center)
                        .size(28.dp),
                )
                if (videoDurationMs > 0L) {
                    Text(
                        text = formatDuration(videoDurationMs),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color(0x8A000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
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

private suspend fun decodeVideoFrame(path: String, targetMaxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        if (frame == null) return@runCatching null
        val maxSide = max(frame.width, frame.height)
        if (maxSide <= targetMaxPx) frame else {
            val scale = targetMaxPx.toFloat() / maxSide.toFloat()
            val width = (frame.width * scale).toInt().coerceAtLeast(1)
            val height = (frame.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(frame, width, height, true).also {
                if (it !== frame) frame.recycle()
            }
        }
    }.getOrNull()
}

private suspend fun readVideoDurationMs(path: String): Long = withContext(Dispatchers.IO) {
    runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        value?.toLongOrNull() ?: 0L
    }.getOrDefault(0L)
}

private fun isVideoPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".mp4") ||
        lower.endsWith(".m4v") ||
        lower.endsWith(".mov") ||
        lower.endsWith(".3gp") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mkv")
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
