package com.xpx.vault.ai.algo

import com.xpx.vault.ai.core.SensitiveKind

/**
 * 敏感信息正则匹配器。输入为 ML Kit OCR 识别得到的文本与二维码载荷，
 * 输出一组 [SensitiveKind]（去重）。匹配完全在本地进行，无任何网络请求。
 *
 * 匹配规则（保守阈值，宁少勿多）：
 *  - ID_CARD：18 位大陆身份证（17 位数字 + 末位数字或 X），或 15 位数字（老版）。
 *  - BANK_CARD：16/17/18/19 位数字且通过 Luhn 校验。
 *  - PHONE_NUMBER：1 开头 11 位数字，第二位 3-9。
 *  - QR_CODE：barcode 命中任意条（由上层传入，不在此处做正则）。
 *  - PRIVATE_CHAT：聊天截图关键词 ≥2 条（WeChat/微信/聊天/朋友圈/Chat/对话 等）。
 */
object SensitiveRegexMatcher {

    private val ID_CARD_REGEX = Regex("""(?<!\d)(\d{17}[\dXx]|\d{15})(?!\d)""")
    private val PHONE_REGEX = Regex("""(?<!\d)(1[3-9]\d{9})(?!\d)""")
    // 宽 16-19 位数字串，Luhn 再校验。
    private val BANK_CANDIDATE_REGEX = Regex("""(?<!\d)(\d{16,19})(?!\d)""")

    private val CHAT_KEYWORDS = listOf(
        "微信", "朋友圈", "聊天", "对话", "群聊",
        "WeChat", "Chat", "Messenger", "WhatsApp", "QQ",
    )

    /**
     * 对识别结果做规则匹配，返回命中的敏感子类集合（不含 FACE_CLEAR，由 Face Detector 单独贡献）。
     */
    fun match(ocrText: String, barcodeHit: Boolean): Set<SensitiveKind> {
        if (ocrText.isBlank() && !barcodeHit) return emptySet()
        val result = mutableSetOf<SensitiveKind>()

        if (barcodeHit) result += SensitiveKind.QR_CODE

        if (ocrText.isNotBlank()) {
            if (ID_CARD_REGEX.containsMatchIn(ocrText)) result += SensitiveKind.ID_CARD
            if (PHONE_REGEX.containsMatchIn(ocrText)) result += SensitiveKind.PHONE_NUMBER
            if (detectBankCard(ocrText)) result += SensitiveKind.BANK_CARD
            if (detectPrivateChat(ocrText)) result += SensitiveKind.PRIVATE_CHAT
        }
        return result
    }

    private fun detectBankCard(text: String): Boolean =
        BANK_CANDIDATE_REGEX.findAll(text).any { luhnValid(it.value) }

    private fun detectPrivateChat(text: String): Boolean {
        val hitCount = CHAT_KEYWORDS.count { kw -> text.contains(kw, ignoreCase = true) }
        return hitCount >= 2
    }

    private fun luhnValid(digits: String): Boolean {
        var sum = 0
        var alt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }
}
