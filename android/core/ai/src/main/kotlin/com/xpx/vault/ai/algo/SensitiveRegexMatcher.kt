package com.xpx.vault.ai.algo

import com.xpx.vault.ai.core.SensitiveKind

/**
 * 敏感信息正则匹配器。输入为 ML Kit OCR 识别得到的文本与二维码载荷，
 * 输出一组 [SensitiveKind]（去重）。匹配完全在本地进行，无任何网络请求。
 *
 * 匹配规则（保守阈值，宁少勿多）：
 *  - ID_CARD：
 *    · 18 位大陆身份证（17 位数字 + 末位数字或 X），或 15 位数字（老版）
 *    · 护照号 [A-Z]{1,2}\d{7,8}（中/日/韩/澳/意 等亚洲主流格式）
 *    · MRZ 机读区行：大写字母 / 数字 / '<' 混排，长度 ≥30，含 '<<' 填充符（ICAO 国际标准）
 *    · 美国 SSN：\d{3}-\d{2}-\d{4}
 *  - BANK_CARD：文本去掉空格/短横线后 13~19 位数字且通过 Luhn 校验（覆盖 Visa/MC/Amex/Diners 等）。
 *  - PHONE_NUMBER：中国大陆 1 开头 11 位；美加 NANP 格式；E.164 国际号。
 *  - QR_CODE：barcode 命中任意条（由上层传入，不在此处做正则）。
 *  - PRIVATE_CHAT：聊天截图关键词 ≥2 条（WeChat/微信/聊天/朋友圈/Chat/对话 等）。
 */
object SensitiveRegexMatcher {

    private val ID_CARD_REGEX = Regex("""(?<!\d)(\d{17}[\dXx]|\d{15})(?!\d)""")
    // 美国社会安全号 SSN：NNN-NN-NNNN（严格带短横线，不带会被银行卡覆盖）。
    private val US_SSN_REGEX = Regex("""(?<!\d)\d{3}-\d{2}-\d{4}(?!\d)""")
    // 中国大陆手机号。
    private val PHONE_CN_REGEX = Regex("""(?<!\d)(1[3-9]\d{9})(?!\d)""")
    // 北美 NANP 格式：(xxx) xxx-xxxx / xxx-xxx-xxxx / xxx.xxx.xxxx / +1 xxx xxx xxxx
    // 区号首位 2-9（NANP 规范），防止误伤 0/1 开头的普通长数字。
    private val PHONE_NANP_REGEX = Regex(
        """(?<![\d+])(?:\+?1[\s\-.])?\(?([2-9]\d{2})\)?[\s\-.]?(\d{3})[\s\-.]?(\d{4})(?!\d)""",
    )
    // E.164 国际号：+国家码(1-3)+正文(总长 8-15 数字)。仅限 '+' 开头以降低误伤。
    private val PHONE_E164_REGEX = Regex("""(?<!\d)\+\d{7,15}(?!\d)""")
    // 银行卡候选：放宽到 13~19 位以覆盖 Amex(15)/Diners(14)/老 Visa(13)；Luhn 再校验。
    private val BANK_CANDIDATE_REGEX = Regex("""(?<!\d)(\d{13,19})(?!\d)""")
    // 护照号：1~2 位大写字母 + 7~8 位数字，前后不能紧跟其他字母数字（避免变名、产品型号误伤）。
    private val PASSPORT_REGEX = Regex("""(?<![A-Z0-9])[A-Z]{1,2}\d{7,8}(?![A-Z0-9])""")
    // MRZ 机读区特征：连续大写字母/数字/'<' 长度≥ 30（护照 标准 44 字符，证件 ≥30）。
    private val MRZ_SEQ_REGEX = Regex("""[A-Z0-9<]{30,}""")

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
            if (US_SSN_REGEX.containsMatchIn(ocrText)) result += SensitiveKind.ID_CARD
            if (detectPassport(ocrText)) result += SensitiveKind.ID_CARD
            if (detectMrzLine(ocrText)) result += SensitiveKind.ID_CARD
            if (detectPhone(ocrText)) result += SensitiveKind.PHONE_NUMBER
            if (detectBankCard(ocrText)) result += SensitiveKind.BANK_CARD
            if (detectPrivateChat(ocrText)) result += SensitiveKind.PRIVATE_CHAT
        }
        return result
    }

    private fun detectBankCard(text: String): Boolean {
        // 银行卡号在 OCR 输出里常被空格 / 短横线分隔 4+4+4+4（如 "4514 6175 7250 7590"），
        // 或 Amex 的 4+6+5；原始正则要求连续 13+ 位会直接漏。先 normalize 去掉这些分隔符再匹配。
        val normalized = text.replace(Regex("""[\s\u3000\-—–]"""), "")
        return BANK_CANDIDATE_REGEX.findAll(normalized).any { luhnValid(it.value) }
    }
    
    private fun detectPhone(text: String): Boolean {
        // 三条规则任一命中即算：大陆手机 / NANP 北美 / E.164 国际号。
        if (PHONE_CN_REGEX.containsMatchIn(text)) return true
        if (PHONE_NANP_REGEX.containsMatchIn(text)) return true
        if (PHONE_E164_REGEX.containsMatchIn(text)) return true
        return false
    }

    private fun detectPassport(text: String): Boolean {
        // 先去掉空白再匹配，OCR 有时会在字母数字间插入空格（如 "EM 62713 64"）。
        val compact = text.replace(Regex("""[\s\u3000]"""), "")
        return PASSPORT_REGEX.containsMatchIn(compact)
    }

    private fun detectMrzLine(text: String): Boolean {
        val compact = text.replace(Regex("""[\s\u3000]"""), "")
        // MRZ 行的核心标志是 "<<" 填充符，没有这两个不算 MRZ。
        if (!compact.contains("<<")) return false
        return MRZ_SEQ_REGEX.containsMatchIn(compact)
    }

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
