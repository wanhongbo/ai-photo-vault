package com.xpx.vault.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.theme.UiColors

/**
 * AI 各功能子页面的通用占位。
 * PR2 起会被具体实现替换：
 *   - ai_cleanup  → [com.xpx.vault.ui.ai.AiCleanupScreen]
 *   - ai_sensitive → AiSensitiveReviewScreen
 *   - ai_classify  → AiClassifyScreen
 *   - ai_privacy   → AiPrivacyRedactScreen
 */
@Composable
fun AiFeaturePlaceholderScreen(
    title: String,
    description: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = title, onBack = onBack)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(8.dp))
                Text(
                    text = "\u5373\u5c06\u4e0a\u7ebf",
                    color = Color(0xFFF0F4FF),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(modifier = Modifier.size(12.dp))
                Text(
                    text = description,
                    color = Color(0xFF8A8A90),
                    fontSize = 14.sp,
                )
            }
        }
    }
}
