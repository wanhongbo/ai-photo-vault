package com.xpx.vault.ai.privacy

import android.graphics.Rect

/**
 * 脱敏样式。
 *
 * - [MOSAIC]  方块马赛克
 * - [BLUR]    矩形区域高斯模糊（API 31+ 真 RenderEffect，低版本降级两次缩放）
 * - [BAR]     黑色矩形条
 * - [OVAL_BLUR] 椭圆高斯模糊（形状比矩形更贴面部，不露硬边）
 * - [EMOJI]   将 🙈 emoji 画在区域内，社交风
 */
enum class RedactionStyle { MOSAIC, BLUR, BAR, OVAL_BLUR, EMOJI }

/** 脱敏区域命中类别，用于 UI 分组展示/区分颜色。 */
enum class RedactionKind { FACE, TEXT, BARCODE, MANUAL }

/**
 * 像素坐标系下的脱敏矩形（相对于输入 Bitmap 像素尺寸）。
 *
 * 不使用 [android.graphics.Rect] 作为构造入参，是为了让纯 Kotlin 单测能直接构造；
 * [clampTo] 会裁剪到合法范围内再输出 Android Rect。
 */
data class RedactionRegion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val kind: RedactionKind,
) {
    fun clampTo(width: Int, height: Int): Rect? {
        val l = left.coerceIn(0, width)
        val t = top.coerceIn(0, height)
        val r = right.coerceIn(0, width)
        val b = bottom.coerceIn(0, height)
        if (r - l <= 0 || b - t <= 0) return null
        return Rect(l, t, r, b)
    }
}
