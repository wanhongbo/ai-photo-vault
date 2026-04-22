package com.photovault.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.photovault.app.BuildConfig
import com.photovault.app.R
import com.photovault.app.ui.theme.PhotoVaultTheme
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiRadius
import kotlinx.coroutines.delay

private const val SplashHoldMs = 1_600L
private const val ProgressAnimMs = 1_200

/**
 * 闪屏（对齐 Pixso node `4:2` 布局）。盾牌位图可替换为 `drawable-nodpi` 下导出的高清 PNG，并改 [R.drawable.ic_splash_shield] 引用。
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
) {
    val progressTarget by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = ProgressAnimMs),
        label = "splashProgress",
    )

    LaunchedEffect(Unit) {
        delay(SplashHoldMs)
        onFinished()
    }

    val glowRadiusPx = with(LocalDensity.current) { 140.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to UiColors.Splash.bgLeft,
                        0.6f to UiColors.Splash.bgRight,
                        1f to UiColors.Splash.bgRight,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-72).dp)
                .size(280.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(UiColors.Splash.glowInner, UiColors.Splash.glowOuter, Color.Transparent),
                        radius = glowRadiusPx,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(UiRadius.splashIcon))
                    .background(UiColors.Splash.iconSurface)
                    .border(1.dp, UiColors.Splash.iconStroke, RoundedCornerShape(UiRadius.splashIcon)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.shield_check),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.splash_title),
                color = UiColors.Splash.title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = UiColors.Splash.tagline,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SplashProgressBar(progress = progressTarget)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.splash_footer, BuildConfig.VERSION_NAME),
                color = UiColors.Splash.footer,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SplashProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val trackWidth = 140.dp
    val height = 3.dp
    Box(
        modifier = modifier
            .size(width = trackWidth, height = height)
            .clip(RoundedCornerShape(2.dp))
            .background(UiColors.Splash.progressTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(2.dp))
                .background(UiColors.Splash.progressFill),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenPreview() {
    PhotoVaultTheme {
        SplashScreen(onFinished = {})
    }
}
