package com.xpx.vault.ui.feedback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

private const val PRESS_SCALE = 0.9f
private const val PRESS_IN_MS = 80
private const val RELEASE_MS = 180

@Composable
fun rememberFeedbackInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
): Modifier {
    val pressed = interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed.value) PRESS_SCALE else 1f,
        animationSpec = tween(durationMillis = if (pressed.value) PRESS_IN_MS else RELEASE_MS),
        label = "pressScale",
    )
    // Use graphicsLayer so layout bounds stay full size; outer border/clip drawn earlier in the chain
    // keeps stroke extent stable while only the inner content visually scales.
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = TransformOrigin.Center
    }
}

