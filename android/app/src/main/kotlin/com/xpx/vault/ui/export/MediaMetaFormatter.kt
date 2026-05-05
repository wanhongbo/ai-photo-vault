package com.xpx.vault.ui.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 预览页"详细信息"弹窗用到的轻量格式化工具。仅做展示层处理。
 */
object MediaMetaFormatter {

    /** 1024 进制，最多保留 2 位小数；B/KB/MB/GB。 */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var idx = 0
        while (value >= 1024.0 && idx < units.lastIndex) {
            value /= 1024.0
            idx++
        }
        return if (idx == 0) "${bytes} B" else String.format(Locale.US, "%.2f %s", value, units[idx])
    }

    /** yyyy-MM-dd HH:mm，使用系统时区和默认 Locale。 */
    fun formatModifiedTime(epochMs: Long): String {
        if (epochMs <= 0L) return "-"
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }
}
