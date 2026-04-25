package com.photovault.app.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.photovault.app.R
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiTextSize

@Composable
fun VideoPlayerScreen(
    path: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(true) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef?.release()
            mediaPlayerRef = null
            videoViewRef = null
        }
    }

    fun applyMuteState() {
        mediaPlayerRef?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
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
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = {
                VideoView(context).apply {
                    setVideoURI(Uri.parse(path))
                    setOnPreparedListener { mp ->
                        mediaPlayerRef?.release()
                        mediaPlayerRef = mp
                        mp.isLooping = true
                        applyMuteState()
                        start()
                    }
                }.also { videoViewRef = it }
            },
            update = { view ->
                if (view.tag != path) {
                    view.tag = path
                    view.setVideoURI(Uri.parse(path))
                    view.start()
                }
                applyMuteState()
            },
        )
        Text(
            text = stringResource(R.string.video_player_hint),
            color = UiColors.Home.subtitle,
            fontSize = UiTextSize.homeNavLabel,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppButton(
                text = if (muted) stringResource(R.string.video_player_unmute) else stringResource(R.string.video_player_mute),
                onClick = {
                    muted = !muted
                    applyMuteState()
                },
                variant = AppButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = stringResource(R.string.video_player_restart),
                onClick = {
                    videoViewRef?.seekTo(0)
                    videoViewRef?.start()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
