package com.xpx.vault.ai.algo

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 基于拉普拉斯算子方差的清晰度评估：对灰度图做 3x3 Laplacian 卷积，
 * 输出像素的方差作为锐度指标。方差越低图像越模糊（细节越少）。
 *
 * 经验阈值：
 *  - < 60：明显模糊 → 标记为 isBlurry
 *  - 60 ~ 150：边缘/低细节图（证件正面常见）
 *  - > 150：清晰
 *
 * 亮度：所有像素灰度均值 / 255.0；
 * 过曝：高亮像素（>235）占比 > 30% 视为过曝。
 *
 * 实现策略：为省内存，对输入 bitmap 按比例缩放到短边 128，再跑算子。
 */
object SharpnessAnalyzer {

    private const val TARGET_SHORT_SIDE = 128
    private const val BLURRY_THRESHOLD = 60.0
    private const val OVEREXP_THRESHOLD_PIXEL = 235
    private const val OVEREXP_RATIO_LIMIT = 0.30

    data class Quality(
        val sharpness: Float,
        val brightness: Float,
        val isBlurry: Boolean,
        val isOverExposed: Boolean,
    )

    fun analyze(bitmap: Bitmap): Quality {
        val (w, h) = scaledSize(bitmap)
        val scaled = if (w != bitmap.width || h != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, w, h, /*filter*/ true)
        } else {
            bitmap
        }
        val gray = IntArray(w * h)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        var sumBrightness = 0L
        var overExpCount = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val g = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt()
                .coerceIn(0, 255)
            gray[i] = g
            sumBrightness += g
            if (g > OVEREXP_THRESHOLD_PIXEL) overExpCount++
        }
        val brightness = (sumBrightness.toDouble() / (pixels.size * 255.0)).toFloat()
        val overExpRatio = overExpCount.toDouble() / pixels.size

        // Laplacian 3x3：[[0,1,0],[1,-4,1],[0,1,0]]
        // 只在内部像素（去掉 1px 边）计算，避免越界判断。
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val lap = -4 * gray[idx] +
                    gray[idx - 1] + gray[idx + 1] +
                    gray[idx - w] + gray[idx + w]
                val v = lap.toDouble()
                sum += v
                sumSq += v * v
                count++
            }
        }
        val mean = if (count > 0) sum / count else 0.0
        val variance = if (count > 0) (sumSq / count) - mean * mean else 0.0

        if (scaled !== bitmap) scaled.recycle()

        return Quality(
            sharpness = variance.toFloat(),
            brightness = brightness,
            isBlurry = variance < BLURRY_THRESHOLD,
            isOverExposed = overExpRatio > OVEREXP_RATIO_LIMIT,
        )
    }

    private fun scaledSize(bitmap: Bitmap): Pair<Int, Int> {
        val w = bitmap.width
        val h = bitmap.height
        val shortSide = minOf(w, h)
        if (shortSide <= TARGET_SHORT_SIDE) return w to h
        val ratio = TARGET_SHORT_SIDE.toFloat() / shortSide
        return (w * ratio).toInt().coerceAtLeast(1) to (h * ratio).toInt().coerceAtLeast(1)
    }
}
