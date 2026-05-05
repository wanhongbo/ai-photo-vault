package com.xpx.vault.ai.privacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max
import kotlin.math.min

/**
 * 脱敏处理：在原图上对命中区域覆盖指定样式。
 *
 * 样式说明见 [RedactionStyle]。
 *
 * 高斯模糊策略：
 * - API 31+（Android 12）使用 [RenderEffect.createBlurEffect] 走 GPU 真高斯模糊，
 *   结合 [HardwareRenderer] + [ImageReader] 回读到 Bitmap。
 * - API <31 降级为 "两次缩放" 近似模糊，效果柔和但比真高斯差一截。
 *
 * 注：本渲染器只处理位图；输入 [src] 不会被修改，返回新的可变位图副本。
 */
object PrivacyRenderer {

    /**
     * 马赛克方块相对短边的比例；越大越粗。
     * 0.10f 在 400px 的脸上得到 ~40px 块，分辨率足够粗犷以遮盖五官特征。
     */
    private const val MOSAIC_RATIO = 0.10f

    /** 马赛克块的最小像素下限，避免小 ROI 下块太细仍然能辨认原始内容。 */
    private const val MOSAIC_MIN_BLOCK_PX = 10

    /** 模糊近似的缩放倍数（低版本 fallback 用）；倍数越大越糊。 */
    private const val BLUR_DOWNSCALE = 12

    /** RenderEffect 真高斯的模糊半径（像素）。ML Kit 框住的脸通常 200~600 px，25 是人脸打码常用值。 */
    private const val BLUR_RADIUS_PX = 25f

    fun render(
        src: Bitmap,
        regions: List<RedactionRegion>,
        style: RedactionStyle,
    ): Bitmap {
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        if (regions.isEmpty()) return output

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        regions.forEach { region ->
            val rect = region.clampTo(output.width, output.height) ?: return@forEach
            when (style) {
                RedactionStyle.BAR -> drawBar(canvas, rect, paint, Color.BLACK)
                RedactionStyle.WHITE_BAR -> drawBar(canvas, rect, paint, Color.WHITE)
                RedactionStyle.MOSAIC -> drawMosaic(canvas, output, rect, paint)
                RedactionStyle.BLUR -> drawBlur(canvas, output, rect, paint)
                RedactionStyle.OVAL_BLUR -> drawOvalBlur(canvas, output, rect, paint)
                RedactionStyle.EMOJI -> drawEmoji(canvas, rect, paint)
            }
        }
        return output
    }

    // ------------------------------------------------------------------
    // 单样式绘制
    // ------------------------------------------------------------------

    private fun drawBar(canvas: Canvas, rect: Rect, paint: Paint, @androidx.annotation.ColorInt color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.shader = null
        canvas.drawRect(RectF(rect), paint)
    }

    private fun drawMosaic(canvas: Canvas, src: Bitmap, rect: Rect, paint: Paint) {
        val roi = safeSubBitmap(src, rect) ?: return
        val shortSide = min(rect.width(), rect.height()).coerceAtLeast(1)
        // blockPx 同时受比例与绝对像素下限约束：
        //   - 大 ROI：按比例算 → 比例起作用，确保视觉一致的“粗颗粒度”
        //   - 小 ROI（如证件号码小区块）：下限起作用，避免块过细导致数字仍可辨认
        val blockPx = max(MOSAIC_MIN_BLOCK_PX, (shortSide * MOSAIC_RATIO).toInt())
        val w = max(1, roi.width / blockPx)
        val h = max(1, roi.height / blockPx)
        val small = Bitmap.createScaledBitmap(roi, w, h, false)
        val mosaic = Bitmap.createScaledBitmap(small, roi.width, roi.height, false)
        canvas.drawBitmap(mosaic, null, RectF(rect), paint.apply { shader = null })
        if (small !== roi) small.recycle()
        if (mosaic !== roi) mosaic.recycle()
    }

    private fun drawBlur(canvas: Canvas, src: Bitmap, rect: Rect, paint: Paint) {
        val blurred = blurRoi(src, rect) ?: return
        paint.shader = null
        canvas.drawBitmap(blurred, null, RectF(rect), paint)
        blurred.recycle()
    }

    /**
     * 椭圆模糊：把 ROI 模糊后，只透过一个椭圆蒙版写回 canvas。
     * 椭圆内模糊替换像素，椭圆外保持原图，形状比矩形更贴面部。
     */
    private fun drawOvalBlur(canvas: Canvas, src: Bitmap, rect: Rect, paint: Paint) {
        val blurred = blurRoi(src, rect) ?: return
        val rectF = RectF(rect)
        val saveCount = canvas.save()
        val path = Path().apply { addOval(rectF, Path.Direction.CW) }
        canvas.clipPath(path)
        paint.shader = null
        canvas.drawBitmap(blurred, null, rectF, paint)
        canvas.restoreToCount(saveCount)
        blurred.recycle()
    }

    private fun drawEmoji(canvas: Canvas, rect: Rect, paint: Paint) {
        paint.shader = null
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        // 字号取短边的 90%，让 emoji 基本充满 ROI；Paint.descent/ascent 调垂直居中。
        paint.textSize = (min(rect.width(), rect.height()) * 0.9f).coerceAtLeast(14f)
        val metrics = paint.fontMetrics
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(EMOJI_COVER, cx, cy, paint)
    }

    // ------------------------------------------------------------------
    // 高斯模糊：真 RenderEffect + 低版本 fallback
    // ------------------------------------------------------------------

    /**
     * 对 [src] 指定 [rect] 区域做高斯模糊，返回模糊后的 ROI 副本（调用方负责 recycle）。
     */
    private fun blurRoi(src: Bitmap, rect: Rect): Bitmap? {
        val roi = safeSubBitmap(src, rect) ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurred = runCatching { blurWithRenderEffect(roi, BLUR_RADIUS_PX) }.getOrNull()
            if (blurred != null && blurred !== roi) {
                roi.recycle()
                blurred
            } else {
                // GPU 路径失败（不支持/硬件异常），降级到两次缩放
                val fallback = blurTwoPassDownscale(roi)
                if (fallback !== roi) roi.recycle()
                fallback
            }
        } else {
            val fallback = blurTwoPassDownscale(roi)
            if (fallback !== roi) roi.recycle()
            fallback
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun blurWithRenderEffect(src: Bitmap, radius: Float): Bitmap {
        val w = src.width
        val h = src.height
        val reader = ImageReader.newInstance(
            w,
            h,
            PixelFormat.RGBA_8888,
            1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val node = RenderNode("privacy-blur")
        node.setPosition(0, 0, w, h)
        node.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
        val recCanvas = node.beginRecording()
        recCanvas.drawBitmap(src, 0f, 0f, null)
        node.endRecording()

        val renderer = HardwareRenderer().apply {
            setSurface(reader.surface)
            setContentRoot(node)
        }
        renderer.createRenderRequest()
            .setWaitForPresent(true)
            .syncAndDraw()

        val image = reader.acquireNextImage()
        return try {
            val hb = image?.hardwareBuffer
            if (hb != null) {
                val wrapped = Bitmap.wrapHardwareBuffer(
                    hb,
                    ColorSpace.get(ColorSpace.Named.SRGB),
                )
                val software = wrapped?.copy(Bitmap.Config.ARGB_8888, false) ?: src
                hb.close()
                software
            } else {
                src
            }
        } finally {
            image?.close()
            renderer.destroy()
            reader.close()
            node.discardDisplayList()
        }
    }

    private fun blurTwoPassDownscale(roi: Bitmap): Bitmap {
        val w = max(1, roi.width / BLUR_DOWNSCALE)
        val h = max(1, roi.height / BLUR_DOWNSCALE)
        val down = Bitmap.createScaledBitmap(roi, w, h, true)
        val up = Bitmap.createScaledBitmap(down, roi.width, roi.height, true)
        if (down !== roi && down !== up) down.recycle()
        return up
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

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

    /** 遮盖用 emoji：🙈 捂眼猴，意图最明显的 "别看" 表达。 */
    private const val EMOJI_COVER = "\uD83D\uDE48"
}
