package com.xpx.vault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * LumaVault 全局排版规范。
 *
 * 说明：
 * - 保持与 Material3 [Typography] 角色命名一致，方便 Material 标准组件（Snackbar、Chip、
 *   AlertDialog 等）取用，同时业务自绘组件也可直接读取 `MaterialTheme.typography.xxx` 获得
 *   一致字号/字重/行高。
 * - 字号刻度参考 UiTextSize（项目原有粒度），但额外补齐 11 / 13 / 15 / 20sp 等常用档位。
 * - 字母间距遵循 Material 建议：正文偏紧 (0.15~0.25)，标签偏松 (0.3~0.5)。
 */
val AppTypography: Typography = Typography(
    // —— Display / Headline：Home / Splash / 空态大标题 ——
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),

    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),

    // —— Title：分组 / 卡片 / 对话框标题 ——
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    // —— Body：正文 / 列表描述 ——
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, letterSpacing = 0.25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp, letterSpacing = 0.2.sp),

    // —— Label：按钮 / 徽章 / 角标 ——
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
