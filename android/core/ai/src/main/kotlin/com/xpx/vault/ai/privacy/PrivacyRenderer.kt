package com.xpx.vault.ai.privacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * 脱敏处理：在原图上对命中区域覆盖指定样式。
 *
 * - [RedactionStyle.MOSAIC]：马赛克（将 ROI 缩放到小图再放大回来，平均像素化）。
 * - [RedactionStyle.BLUR]：简易高斯模糊（多次平均缩放近似，不依赖 RenderScript）。
 * - [RedactionStyle.BAR]：纯黑色填充条。
 *
 * 注：本渲染器只处理位图；输入 [src] 不会被修改，返回新的可变位图副本。
 */
object PrivacyRenderer {

    /** 马赛克方块相对短边的比例；越大越粗。 */
    private const val MOSAIC_RATIO = 0.04f

    /** 模糊近似的缩放倍数；倍数越大越糊。 */
    private const val BLUR_DOWNSCALE = 12

    fun render(
        src: Bitmap,
        regions: List<RedactionRegion>,
        style: RedactionStyle,
    ): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        if (regions.isEmpty()) return output

        val canvas = Canvas(output)
        val clipPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        regions.forEach { region ->
            val rect = region.clampTo(output.width, output.height) ?: return@forEach
            when (style) {
                RedactionStyle.BAR -> drawBar(canvas, rect, clipPaint)
                RedactionStyle.MOSAIC -> drawMosaic(canvas, output, rect, clipPaint)
                RedactionStyle.BLUR -> drawBlur(canvas, output, rect, clipPaint)
            }
        }
        return output
    }

    private fun drawBar(canvas: Canvas, rect: Rect, paint: Paint) {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        paint.shader = null
        canvas.drawRect(RectF(rect), paint)
    }

    private fun drawMosaic(canvas: Canvas, src: Bitmap, rect: Rect, paint: Paint) {
        val roi = safeSubBitmap(src, rect) ?: return
        val shortSide = min(rect.width(), rect.height()).coerceAtLeast(1)
        val blockPx = max(1, (shortSide * MOSAIC_RATIO).toInt())
        val w = max(1, roi.width / blockPx)
        val h = max(1, roi.height / blockPx)
        val small = Bitmap.createScaledBitmap(roi, w, h, false)
        val mosaic = Bitmap.createScaledBitmap(small, roi.width, roi.height, false)
        canvas.drawBitmap(mosaic, null, RectF(rect), paint.apply { shader = null })
        if (small !== roi) small.recycle()
        if (mosaic !== roi) mosaic.recycle()
    }

    private fun drawBlur(canvas: Canvas, src: Bitmap, rect: Rect, paint: Paint) {
        val roi = safeSubBitmap(src, rect) ?: return
        val w = max(1, roi.width / BLUR_DOWNSCALE)
        val h = max(1, roi.height / BLUR_DOWNSCALE)
        // 两次缩放模拟低通滤波：先缩小到极小再线性插值放大，产生柔和模糊。
        val down = Bitmap.createScaledBitmap(roi, w, h, true)
        val up = Bitmap.createScaledBitmap(down, roi.width, roi.height, true)
        canvas.drawBitmap(up, null, RectF(rect), paint.apply { shader = null })
        if (down !== roi) down.recycle()
        if (up !== roi) up.recycle()
    }

    private fun safeSubBitmap(src: Bitmap, rect: Rect): Bitmap? {
        val w = rect.width()
        val h = rect.height()
        if (w <= 0 || h <= 0) return null
        return try {
            Bitmap.createBitmap(src, rect.left, rect.top, w, h)
        } catch (_: Throwable) {
            null
        }
    }
}
