package com.photovault.app.ui.feedback

import android.os.SystemClock
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

private const val DEFAULT_THROTTLE_MS = 500L

@Composable
fun rememberThrottledClick(
    intervalMs: Long = DEFAULT_THROTTLE_MS,
    onClick: () -> Unit,
): () -> Unit {
    val latestClick = rememberUpdatedState(onClick)
    val lastClickTime = remember { mutableLongStateOf(0L) }
    return remember(intervalMs) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTime.longValue >= intervalMs) {
                lastClickTime.longValue = now
                latestClick.value.invoke()
            }
        }
    }
}

fun Modifier.throttledClickable(
    enabled: Boolean = true,
    intervalMs: Long = DEFAULT_THROTTLE_MS,
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val throttledOnClick = rememberThrottledClick(intervalMs = intervalMs, onClick = onClick)
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val resolvedIndication = indication ?: LocalIndication.current
    clickable(
        enabled = enabled,
        interactionSource = resolvedInteractionSource,
        indication = resolvedIndication,
        onClick = throttledOnClick,
    )
}

