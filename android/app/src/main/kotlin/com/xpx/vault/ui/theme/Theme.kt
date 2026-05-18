package com.xpx.vault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * LumaNox 全局 Shape scale。
 * - extraSmall：Chip / 小徽章
 * - small：密集列表行（设置行）
 * - medium：TopBar / 输入域
 * - large：通用卡片（Home section、AI suggest、Paywall tier）
 * - extraLarge：沉浸式卡片（底部导航栏容器）
 */
val AppShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * LumaNox 深色主色方案。将项目原有的 UiColors 品牌色映射到 Material3 [ColorScheme] 的
 * 标准角色上，使 Material 标准组件（Snackbar / Chip / AlertDialog / Switch 等）获得一致
 * 的品牌感；自绘组件仍可继续读取 UiColors 专用令牌。
 *
 * 注：当前产品定位仅做深色主题。若未来增加亮色主题，可新增 AppLightColorScheme 并在
 * [XpxVaultTheme] 中根据系统主题或用户偏好切换。
 */
private val AppDarkColorScheme = darkColorScheme(
    primary = UiColors.Button.primaryContainer,
    onPrimary = UiColors.Button.primaryContent,
    primaryContainer = UiColors.Home.navItemActiveBg,
    onPrimaryContainer = UiColors.Home.navItemActive,

    secondary = UiColors.Button.secondaryContainer,
    onSecondary = UiColors.Button.secondaryContent,
    secondaryContainer = UiColors.Home.sectionBg,
    onSecondaryContainer = UiColors.Home.title,

    tertiary = UiColors.Paywall.crownRing,
    onTertiary = Color(0xFF0D0D0D),

    error = UiColors.Lock.error,
    onError = Color.White,
    errorContainer = UiColors.Lock.errorBg,
    onErrorContainer = UiColors.Lock.error,

    background = UiColors.Home.bgBottom,
    onBackground = UiColors.Home.title,

    surface = UiColors.Home.sectionBg,
    onSurface = UiColors.Home.title,
    surfaceVariant = UiColors.Home.emptyCardBg,
    onSurfaceVariant = UiColors.Home.subtitle,

    outline = UiColors.Home.emptyCardStroke,
    outlineVariant = UiColors.Home.navBarStroke,

    scrim = Color(0xCC000000),
)

@Composable
fun XpxVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppDarkColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
