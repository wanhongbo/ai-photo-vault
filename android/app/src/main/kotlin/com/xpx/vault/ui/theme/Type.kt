package com.xpx.vault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * LumaVault 全局排版规范。
 *
 * 字体：使用系统 sans-serif（中文设备自动映射到 Noto Sans CJK / 思源黑体）。
 * 若需精确控制，可将 .ttf 放入 res/font/ 后改用 Font(resId = R.font.xxx)。
 *
 * 说明：
 * - 保持与 Material3 [Typography] 角色命名一致，方便 Material 标准组件（Snackbar、Chip、
 *   AlertDialog 等）取用，同时业务自绘组件也可直接读取 `MaterialTheme.typography.xxx` 获得
 *   一致字号/字重/行高。
 * - 字号刻度参考 UiTextSize（项目原有粒度），但额外补齐 11 / 13 / 15 / 20sp 等常用档位。
 * - 中文排版优化：CJK 字符无需 Latin 式大 letterSpacing，统一收紧；行高增加约 10%
 *   保证中文多行阅读舒适。
 */
val AppFontFamily: FontFamily = FontFamily.SansSerif

val AppTypography: Typography = Typography(
    // —— Display / Headline：Home / Splash / 空态大标题 ——
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 42.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),

    // —— Title：分组 / 卡片 / 对话框标题 ——
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),

    // —— Body：正文 / 列表描述 ——
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
        letterSpacing = 0.02.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
        letterSpacing = 0.02.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        letterSpacing = 0.02.sp,
    ),

    // —— Label：按钮 / 徽章 / 角标 ——
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
        letterSpacing = 0.02.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
        letterSpacing = 0.02.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.02.sp,
    ),
)
