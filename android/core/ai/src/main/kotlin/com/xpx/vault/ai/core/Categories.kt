package com.xpx.vault.ai.core

/**
 * 分类标签的归一化命名。Image Labeler 原始标签（英文 400+）通过
 * `AiClassifyMapper` 映射到这里的 6 大类，供 UI Virtual Album 直接使用。
 */
enum class ClassifyCategory {
    SCREENSHOT,  // 截图
    ID_CARD,     // 证件
    PORTRAIT,    // 人像
    LANDSCAPE,   // 风景
    FOOD,        // 美食
    DOCUMENT,    // 文档/票据
    OTHER,       // 未归类
}

/**
 * 敏感内容子类。
 */
enum class SensitiveKind {
    ID_CARD,       // 身份证 / 护照
    BANK_CARD,     // 银行卡
    PHONE_NUMBER,  // 手机号
    QR_CODE,       // 二维码 / 条码
    FACE_CLEAR,    // 清晰人脸（用于"面部信息暴露"提示）
    PRIVATE_CHAT,  // 聊天隐私截图（多关键词命中）
}
