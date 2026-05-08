package com.xpx.vault.ui.feedback

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

/** 带主题默认水波纹；不需要 ripple 时请用 [throttledClickable] 的重载并传入 [indication] = null。 */
fun Modifier.throttledClickable(
    enabled: Boolean = true,
    intervalMs: Long = DEFAULT_THROTTLE_MS,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val throttledOnClick = rememberThrottledClick(intervalMs = intervalMs, onClick = onClick)
    clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = throttledOnClick,
    )
}

fun Modifier.throttledClickable(
    enabled: Boolean = true,
    intervalMs: Long = DEFAULT_THROTTLE_MS,
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    onClick: () -> Unit,
): Modifier = composed {
    val throttledOnClick = rememberThrottledClick(intervalMs = intervalMs, onClick = onClick)
    clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = indication,
        onClick = throttledOnClick,
    )
}
