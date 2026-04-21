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
import kotlinx.coroutines.delay

private val SplashBgLeft = Color(0xFF0D1A2E)
private val SplashBgRight = Color(0xFF0D0D0D)
private val GlowInner = Color(0x451A3A6B)
private val GlowOuter = Color(0x114A9EFF)
private val IconSurface = Color(0xFF111418)
private val IconStroke = Color(0xFF2A3848)
private val TitleColor = Color(0xFFF0F4FF)
private val TaglineColor = Color(0xFF8A9BB0)
private val ProgressTrack = Color(0xFF1E2A38)
private val ProgressFill = Color(0xFF4A9EFF)
private val FooterColor = Color(0xFF3A4555)

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
                        0f to SplashBgLeft,
                        0.6f to SplashBgRight,
                        1f to SplashBgRight,
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
                        colors = listOf(GlowInner, GlowOuter, Color.Transparent),
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
                    .clip(RoundedCornerShape(22.dp))
                    .background(IconSurface)
                    .border(1.dp, IconStroke, RoundedCornerShape(22.dp)),
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
                color = TitleColor,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.splash_tagline),
                color = TaglineColor,
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
                color = FooterColor,
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
            .background(ProgressTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(2.dp))
                .background(ProgressFill),
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
