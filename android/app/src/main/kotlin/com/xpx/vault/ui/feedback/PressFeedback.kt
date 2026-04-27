package com.xpx.vault.ui.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer

private const val PRESS_SCALE = 0.97f
private const val PRESS_ALPHA = 0.9f
private const val PRESS_IN_MS = 80
private const val RELEASE_MS = 180

@Composable
fun rememberFeedbackInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    extraHighlight: Boolean = false,
): Modifier {
    val pressed = interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value) PRESS_SCALE else 1f,
        animationSpec = tween(durationMillis = if (pressed.value) PRESS_IN_MS else RELEASE_MS),
        label = "pressScale",
    )
    val alpha = if (pressed.value) (if (extraHighlight) 0.85f else PRESS_ALPHA) else 1f
    return this
        .scale(scale.value)
        .graphicsLayer(alpha = alpha)
}

