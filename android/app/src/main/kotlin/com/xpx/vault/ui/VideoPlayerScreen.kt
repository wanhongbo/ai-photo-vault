package com.xpx.vault.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiTextSize
import kotlinx.coroutines.delay
import android.content.res.Configuration

@Composable
fun VideoPlayerScreen(
    path: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localView = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val playbackStore = remember(context) { VideoPlaybackStore(context) }
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val controlsHorizontalInset: Dp = if (isLandscape) 72.dp else 10.dp
    val controlsVerticalPadding: Dp = if (isLandscape) 8.dp else 7.dp
    val controlsInnerGap: Dp = if (isLandscape) 6.dp else 5.dp
    val exoPlayer = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(path)))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
        }
    }

    var muted by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var sliderPositionMs by remember { mutableStateOf<Float?>(null) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    var showLandscapeMore by remember { mutableStateOf(false) }

    fun applyMuteState() {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        currentPositionMs = target
        seekFeedback = if (deltaMs >= 0) "+10s" else "-10s"
        showControls = true
    }

    DisposableEffect(path) {
        exoPlayer.seekTo(playbackStore.get(path))
        exoPlayer.playWhenReady = true
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            playbackStore.put(path, exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(activity, localView) {
        val decorView = activity?.window?.decorView ?: localView
        val originalOrientation = activity?.requestedOrientation
        val insetsController = activity?.window?.let { window -> WindowInsetsControllerCompat(window, decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        // Keep status bar visible across the entire app, including video playback.
        insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        onDispose {
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            if (originalOrientation != null) activity.requestedOrientation = originalOrientation
        }
    }

    LaunchedEffect(muted) { applyMuteState() }

    LaunchedEffect(exoPlayer, isSeeking) {
        while (true) {
            if (!isSeeking) currentPositionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(1400)
            showControls = false
            showLandscapeMore = false
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(800)
            seekFeedback = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTopBar(title = stringResource(R.string.video_player_title), onBack = onBack)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val playerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isPlaying, durationMs, playerWidthPx) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                if (offset.x < playerWidthPx / 2f) seekBy(-10_000L) else seekBy(10_000L)
                            },
                        )
                    },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        PlayerView(it).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = {
                        if (it.player !== exoPlayer) it.player = exoPlayer
                    },
                )
                if (seekFeedback != null) {
                    Text(
                        text = seekFeedback.orEmpty(),
                        color = UiColors.Home.title,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(UiColors.Home.emptyCardBg.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
                if (showControls) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = controlsHorizontalInset)
                            .background(UiColors.Home.emptyCardBg.copy(alpha = 0.58f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = controlsVerticalPadding),
                        verticalArrangement = Arrangement.spacedBy(controlsInnerGap),
                    ) {
                        Slider(
                            value = sliderPositionMs ?: currentPositionMs.toFloat(),
                            onValueChange = {
                                isSeeking = true
                                sliderPositionMs = it
                            },
                            onValueChangeFinished = {
                                val seekTo = sliderPositionMs?.toLong() ?: currentPositionMs
                                exoPlayer.seekTo(seekTo)
                                currentPositionMs = seekTo
                                sliderPositionMs = null
                                isSeeking = false
                            },
                            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = UiColors.Home.title.copy(alpha = 0.82f),
                                activeTrackColor = UiColors.Home.title.copy(alpha = 0.7f),
                                inactiveTrackColor = UiColors.Home.subtitle.copy(alpha = 0.35f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isLandscape) 6.dp else 0.dp),
                        )
                        Text(
                            text = "${formatDuration(currentPositionMs)} / ${formatDuration(durationMs)}",
                            color = UiColors.Home.subtitle.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VideoIconControlButton(
                                iconRes = if (isPlaying) R.drawable.ic_video_pause else R.drawable.ic_video_play,
                                contentDescription = if (isPlaying) stringResource(R.string.video_player_pause) else stringResource(R.string.video_player_play),
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    showControls = true
                                },
                                modifier = Modifier.weight(1f),
                                primary = true,
                            )
                            VideoIconControlButton(
                                iconRes = R.drawable.ic_video_more,
                                contentDescription = if (showLandscapeMore) stringResource(R.string.video_player_less) else stringResource(R.string.video_player_more),
                                onClick = {
                                    showLandscapeMore = !showLandscapeMore
                                    showControls = true
                                },
                                modifier = Modifier.weight(1f),
                                primary = false,
                            )
                        }
                        if (showLandscapeMore) {
                            if (!isLandscape) {
                                Text(
                                    text = stringResource(R.string.video_player_hint),
                                    color = UiColors.Home.subtitle,
                                    fontSize = 11.sp,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VideoControlButton(
                                    text = if (muted) stringResource(R.string.video_player_unmute) else stringResource(R.string.video_player_mute),
                                    onClick = { muted = !muted },
                                    modifier = Modifier.weight(1f),
                                    primary = false,
                                )
                                VideoControlButton(
                                    text = stringResource(R.string.video_player_restart),
                                    onClick = {
                                        exoPlayer.seekTo(0L)
                                        exoPlayer.play()
                                        showControls = true
                                    },
                                    modifier = Modifier.weight(1f),
                                    primary = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class VideoPlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("video_playback_resume", Context.MODE_PRIVATE)

    fun get(path: String): Long = prefs.getLong(path, 0L).coerceAtLeast(0L)

    fun put(path: String, positionMs: Long, durationMs: Long) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition = positionMs.coerceAtLeast(0L)
        val shouldClear = safePosition < MIN_RESUME_MS || (safeDuration > 0L && safePosition >= safeDuration - END_TOLERANCE_MS)
        if (shouldClear) {
            prefs.edit().remove(path).apply()
        } else {
            prefs.edit().putLong(path, safePosition).apply()
        }
    }

    private companion object {
        private const val MIN_RESUME_MS = 3_000L
        private const val END_TOLERANCE_MS = 2_000L
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun VideoControlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val bgColor = if (primary) UiColors.Button.primaryContainer.copy(alpha = 0.78f) else UiColors.Button.secondaryContainer.copy(alpha = 0.64f)
    val textColor = if (primary) UiColors.Button.primaryContent else UiColors.Button.secondaryContent
    Box(
        modifier = modifier
            .height(36.dp)
            .background(bgColor, RoundedCornerShape(999.dp))
            .throttledClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun VideoIconControlButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val bgColor = if (primary) UiColors.Button.primaryContainer.copy(alpha = 0.78f) else UiColors.Button.secondaryContainer.copy(alpha = 0.64f)
    val tint = if (primary) UiColors.Button.primaryContent else UiColors.Button.secondaryContent
    Box(
        modifier = modifier
            .height(36.dp)
            .background(bgColor, RoundedCornerShape(999.dp))
            .throttledClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.height(18.dp),
        )
    }
}
