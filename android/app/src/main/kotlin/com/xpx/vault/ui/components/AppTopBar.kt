package com.xpx.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xpx.vault.R
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.theme.UiTouch

/**
 * 通用顶栏：居中标题 + 左侧返回。
 *
 * 变更点（对比旧版 38x36dp、contentDescription=null）：
 * - 返回按钮命中区域提升至 [UiTouch.minTarget]（44dp），满足 Material Accessibility。
 * - Icon 绑定 [stringResource]，屏幕阅读器可朗读 "返回"。
 * - 通过 [semantics] 声明 Button role，便于 TalkBack 给出正确的交互提示音。
 * - 使用 Theme.medium Shape，与全局 Shape scale 对齐。
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backDesc = stringResource(R.string.common_back)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(UiTouch.minTarget)
                .clip(MaterialTheme.shapes.medium)
                .background(UiColors.Home.navBarBg)
                .throttledClickable(onClick = onBack)
                .semantics {
                    role = Role.Button
                    contentDescription = backDesc
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_topbar_back),
                contentDescription = null, // 由父 Box 的 semantics 统一承担
                tint = UiColors.Home.title,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            textAlign = TextAlign.Center,
        )
        // 与返回按钮等宽的占位，保证标题视觉居中
        Spacer(modifier = Modifier.size(UiTouch.minTarget))
    }
}
