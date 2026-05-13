package com.xpx.vault.ui.settings

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.theme.UiColors

/**
 * 通用 WebView 页面，用于展示隐私政策、服务条款等本地 HTML 内容。
 *
 * @param titleResId 顶栏标题的字符串资源 ID
 * @param rawResId   res/raw/ 下的 HTML 资源 ID（R.raw.xxx）
 * @param onBack     返回回调
 */
@Composable
fun LegalWebViewScreen(
    titleResId: Int,
    rawResId: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val bgColor = UiColors.Home.bgBottom

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = stringResource(titleResId), onBack = onBack)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = LegalWebViewClient()
                    settings.defaultTextEncodingName = "UTF-8"
                    setBackgroundColor(Color.TRANSPARENT)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            update = { webView ->
                val html = context.resources.openRawResource(rawResId)
                    .bufferedReader().use { it.readText() }
                webView.loadDataWithBaseURL(
                    /* baseUrl = */ null,
                    /* data = */ injectDarkModeCss(html, bgColor),
                    /* mimeType = */ "text/html",
                    /* encoding = */ "UTF-8",
                    /* historyUrl = */ null,
                )
            },
        )
    }
}

/**
 * 为 HTML 注入暗色模式适配 CSS，使页面背景与应用主题一致。
 */
private fun injectDarkModeCss(html: String, bgColor: androidx.compose.ui.graphics.Color): String {
    val r = (bgColor.red * 255).toInt()
    val g = (bgColor.green * 255).toInt()
    val b = (bgColor.blue * 255).toInt()
    val hex = String.format("#%02X%02X%02X", r, g, b)
    val darkCss = """
        <style>
            body { background-color: $hex !important; }
            a { color: #5B9BF5 !important; }
        </style>
    """.trimIndent()
    return html.replace("</head>", "$darkCss\n</head>")
}

/**
 * 自定义 WebViewClient：拦截外部链接跳转到系统浏览器，仅加载本地内容。
 */
private class LegalWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        // 仅允许加载本地 data；外部 http/https 链接交给系统浏览器
        if (url.startsWith("http://") || url.startsWith("https://")) {
            view?.context?.let { ctx ->
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                )
                ctx.startActivity(intent)
            }
            return true
        }
        return false
    }
}
