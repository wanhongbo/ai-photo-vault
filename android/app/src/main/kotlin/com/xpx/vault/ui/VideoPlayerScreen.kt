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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.components.AppDialog
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.VaultStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.res.Configuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    path: String,
    onBack: () -> Unit,
    isTrash: Boolean = false,
    onOpenAlbum: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localView = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val playbackStore = remember(context) { VideoPlaybackStore(context) }
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
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var sliderPositionMs by remember { mutableStateOf<Float?>(null) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var isSeeking by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun applyMuteState() {
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        currentPositionMs = target
        seekFeedback = if (deltaMs >= 0) "+10s" else "-10s"
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
            delay(64)
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            delay(800)
            if (!isPlaying) exoPlayer.pause()
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
                .fillMaxWidth()
                .background(UiColors.Home.bgBottom),
        ) {
            val playerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isPlaying, durationMs, playerWidthPx) {
                        detectTapGestures(
                            onTap = {
                                isPlaying = !isPlaying
                                if (isPlaying) exoPlayer.play() else exoPlayer.pause()
                            },
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
                            setShutterBackgroundColor(UiColors.Home.bgBottom.toArgb())
                            setBackgroundColor(UiColors.Home.bgBottom.toArgb())
                        }
                    },
                    update = {
                        if (it.player !== exoPlayer) it.player = exoPlayer
                    },
                )
                if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(UiColors.Home.emptyCardBg.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(16.dp)
                            .throttledClickable {
                                isPlaying = true
                                exoPlayer.play()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_video_play),
                            contentDescription = stringResource(R.string.video_player_play),
                            tint = UiColors.Home.title.copy(alpha = 0.92f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
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
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDuration(currentPositionMs),
                        color = UiColors.Home.title.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                    )
                    val sliderInteractionSource = remember { MutableInteractionSource() }
                    val sliderColors = SliderDefaults.colors(
                        thumbColor = UiColors.Home.title.copy(alpha = 0.82f),
                        activeTrackColor = UiColors.Home.title.copy(alpha = 0.7f),
                        inactiveTrackColor = UiColors.Home.subtitle.copy(alpha = 0.35f),
                    )
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
                        colors = sliderColors,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = sliderInteractionSource,
                                colors = sliderColors,
                                thumbSize = DpSize(12.dp, 12.dp),
                            )
                        },
                        track = { state ->
                            SliderDefaults.Track(
                                sliderState = state,
                                modifier = Modifier.height(2.dp),
                                colors = sliderColors,
                                thumbTrackGapSize = 0.dp,
                                trackInsideCornerSize = 0.dp,
                            )
                        },
                    )
                    Text(
                        text = formatDuration(durationMs),
                        color = UiColors.Home.title.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                    )
                    Icon(
                        painter = painterResource(if (muted) R.drawable.ic_video_mute else R.drawable.ic_video_unmute),
                        contentDescription = if (muted) stringResource(R.string.video_player_unmute) else stringResource(R.string.video_player_mute),
                        tint = UiColors.Home.title.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(20.dp)
                            .throttledClickable { muted = !muted },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = if (isTrash) Arrangement.spacedBy(12.dp) else Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isTrash) {
                AppButton(
                    text = stringResource(R.string.trash_recover),
                    onClick = {
                        scope.launch {
                            val album = VaultStore.restoreFromTrash(context, path)
                            if (album != null) {
                                if (onOpenAlbum != null) onOpenAlbum(album) else onBack()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.SECONDARY,
                )
                AppButton(
                    text = stringResource(R.string.trash_delete),
                    onClick = { showPurgeDialog = true },
                    modifier = Modifier.weight(1f),
                    variant = AppButtonVariant.DANGER,
                )
            } else {
                VideoActionButton(
                    iconRes = R.drawable.ic_photo_share,
                    label = stringResource(R.string.photo_viewer_share),
                    onClick = { /* TODO: share */ },
                )
                VideoActionButton(
                    iconRes = R.drawable.ic_photo_edit,
                    label = stringResource(R.string.photo_viewer_edit),
                    onClick = { /* TODO: edit */ },
                )
                VideoActionButton(
                    iconRes = R.drawable.ic_photo_info,
                    label = stringResource(R.string.photo_viewer_info),
                    onClick = { /* TODO: info */ },
                )
                VideoActionButton(
                    iconRes = R.drawable.ic_photo_delete,
                    label = stringResource(R.string.photo_viewer_delete),
                    onClick = { /* TODO: delete */ },
                )
            }
        }
    }
    AppDialog(
        show = showPurgeDialog,
        title = stringResource(R.string.trash_purge_title),
        message = stringResource(R.string.trash_purge_message),
        confirmText = stringResource(R.string.trash_delete),
        onConfirm = {
            showPurgeDialog = false
            scope.launch {
                val purged = VaultStore.purgeFromTrash(path)
                if (purged) onBack()
            }
        },
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = { showPurgeDialog = false },
        confirmVariant = AppButtonVariant.DANGER,
    )
}

@Composable
private fun VideoActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    Column(
        modifier = Modifier
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = UiColors.Home.title,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeNavLabel,
        )
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
