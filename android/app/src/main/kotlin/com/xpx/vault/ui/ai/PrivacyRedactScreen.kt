package com.xpx.vault.ui.ai

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xpx.vault.R
import com.xpx.vault.ai.privacy.RedactionRegion
import com.xpx.vault.ai.privacy.RedactionStyle
import com.xpx.vault.ui.components.AppTopBar
import com.xpx.vault.ui.feedback.pressFeedback
import com.xpx.vault.ui.feedback.rememberFeedbackInteractionSource
import com.xpx.vault.ui.feedback.throttledClickable
import com.xpx.vault.ui.theme.UiColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 隐私脱敏页：进入后自动解密原图 + ML Kit 检测敏感区域，提供三种样式预览与导出。
 *
 * 导出走 [com.xpx.vault.ui.export.MediaExporter.exportRedactedBitmap]，落到系统相册
 * Pictures/AIPhotoVault/Redacted，不覆盖原图。
 */
@Composable
fun PrivacyRedactScreen(
    path: String,
    onBack: () -> Unit,
    viewModel: PrivacyRedactViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(path) { viewModel.load(path) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding(),
    ) {
        AppTopBar(title = "\u9690\u79c1\u8131\u654f", onBack = onBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = UiColors.Ai.execBtnBg)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "\u6b63\u5728\u89e3\u5bc6\u539f\u56fe\u5e76\u68c0\u6d4b\u654f\u611f\u533a\u57df\u2026",
                            color = Color(0xFFB0B0B8),
                            fontSize = 13.sp,
                        )
                    }
                }
                state.errorMessage != null -> {
                    Text(
                        text = "\u6253\u5f00\u5931\u8d25\uff1a${state.errorMessage}",
                        color = Color(0xFFF07878),
                        fontSize = 13.sp,
                    )
                }
                state.preview != null -> {
                    PreviewWithManualOverlay(
                        preview = state.preview!!,
                        manualMode = state.manualMode,
                        manualRegions = state.manualRegions,
                        onAddManual = { l, t, r, b -> viewModel.addManualRegion(l, t, r, b) },
                    )
                }
            }
        }

        // 状态行：左侧检测摘要 / 右侧手动切换，两行合为一行
        StatusLine(
            state = state,
            enabled = state.ready && state.preview != null,
            onToggleManual = { viewModel.toggleManualMode() },
        )
        // 手动区域存在时才出现的窄行：计数 + 撤销 + 清空
        if (state.manualRegions.isNotEmpty()) {
            ManualOpsRow(
                count = state.manualRegions.size,
                onUndo = { viewModel.undoManualRegion() },
                onClear = { viewModel.clearManualRegions() },
            )
        }

        StylePicker(
            current = state.style,
            // 有任一类区域（自动或手动）就允许切换样式。
            enabled = state.ready && (state.regionCount + state.manualRegions.size) > 0,
            onSelect = { viewModel.selectStyle(it) },
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 主按钮：保存到安全相册（加密入库，与原图共存）
        PrimaryActionButton(
            iconRes = R.drawable.ic_ai_shield,
            label = if (state.saving) "保存中…" else "保存到安全相册",
            enabled = state.ready && state.preview != null && !state.saving && !state.exporting && !state.sharing,
            onClick = {
                viewModel.saveToVault { success, msg ->
                    val text = if (success) "已保存到安全相册" else "保存失败：$msg"
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            },
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 次行：导出到系统相册 / 分享，等宽并排
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryActionButton(
                iconRes = R.drawable.ic_photo_save,
                label = if (state.exporting) "导出中…" else "导出系统相册",
                enabled = state.ready && state.preview != null && !state.exporting && !state.saving && !state.sharing,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.exportRedacted { success, msg ->
                        val text = if (success) {
                            "已导出到系统相册 Redacted 目录：$msg"
                        } else {
                            "导出失败：$msg"
                        }
                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            SecondaryActionButton(
                iconRes = R.drawable.ic_photo_share,
                label = if (state.sharing) "分享中…" else "分享",
                enabled = state.ready && state.preview != null && !state.sharing && !state.saving && !state.exporting,
                modifier = Modifier.weight(1f),
                onClick = {
                    viewModel.shareRedacted(chooserTitle = "分享脱敏后的图片") { success, msg ->
                        if (!success) {
                            Toast.makeText(context, "分享失败：$msg", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun StatusLine(
    state: PrivacyRedactUiState,
    enabled: Boolean,
    onToggleManual: () -> Unit,
) {
    // 统一精简文案，能在一行里与“+ 手动” chip 共存不溢行。
    val totalCount = state.regionCount + state.manualRegions.size
    val desc = when {
        state.loading -> "检测中…"
        !state.mlKitReady -> "Play 服务不可用，可手动框选"
        totalCount == 0 -> "未检测到敏感区域，可手动框选"
        state.manualRegions.isEmpty() -> "已识别 ${state.regionCount} 个敏感区域"
        state.regionCount == 0 -> "手动标记 ${state.manualRegions.size} 个区域"
        else -> "自动 ${state.regionCount} · 手动 ${state.manualRegions.size}"
    }
    val dot = when {
        state.loading -> Color(0xFF707078)
        totalCount == 0 -> Color(0xFFFFB547)
        else -> UiColors.Ai.execBtnBg
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧摘要卡，占剩余宽度
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(UiColors.Ai.featureCardBg)
                .border(1.dp, UiColors.Ai.featureCardStroke, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dot),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = desc,
                color = Color(0xFFC8C8CE),
                fontSize = 11.sp,
            )
        }
        // 右侧：手动框选开关
        ManualChip(
            label = if (state.manualMode) "完成" else "+ 手动",
            active = state.manualMode,
            enabled = enabled,
            onClick = onToggleManual,
        )
    }
}

/** 有手动区域时出现的窄行：计数 + 撤销 + 清空。 */
@Composable
private fun ManualOpsRow(
    count: Int,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已添加 $count",
            color = Color(0xFFB0B0B8),
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        ManualChip(label = "撤销", active = false, enabled = true, onClick = onUndo)
        ManualChip(label = "清空", active = false, enabled = true, onClick = onClear)
    }
}

@Composable
private fun StylePicker(
    current: RedactionStyle,
    enabled: Boolean,
    onSelect: (RedactionStyle) -> Unit,
) {
    // 5 种样式：一行排不下，改为 3+2 两行 Grid，保持列宽一致。
    // 第二行的空位用 weight(1f) + 空 Row 占位，避免最后一个按钮横拉宽。
    val styles = RedactionStyle.values().toList()
    val rows = styles.chunked(3)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { rowStyles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowStyles.forEach { style ->
                    StylePill(
                        style = style,
                        isSelected = current == style,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(style) },
                    )
                }
                // 空位占位，第 2 行 2 个按钮时补 1 个空 weight 保持对齐
                repeat(3 - rowStyles.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StylePill(
    style: RedactionStyle,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val label = when (style) {
        RedactionStyle.MOSAIC -> "马赛克"
        RedactionStyle.BLUR -> "高斯模糊"
        RedactionStyle.BAR -> "黑条"
        RedactionStyle.WHITE_BAR -> "白条"
        RedactionStyle.OVAL_BLUR -> "椭圆模糊"
        RedactionStyle.EMOJI -> "Emoji"
    }
    val bg = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardBg
    val borderColor = if (isSelected) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardStroke
    val fg = if (isSelected) UiColors.Ai.execBtnText else Color(0xFFB0B0B8)
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = modifier
            .height(34.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PrimaryActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    val bg = if (enabled) UiColors.Ai.execBtnBg else Color(0xFF2B2B33)
    val fg = if (enabled) UiColors.Ai.execBtnText else Color(0xFF707078)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = rememberFeedbackInteractionSource()
    val bg = UiColors.Ai.featureCardBg
    val stroke = if (enabled) UiColors.Ai.featureCardStroke else Color(0xFF1E1E22)
    val fg = if (enabled) Color(0xFFD8DAE0) else Color(0xFF555559)
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(12.dp))
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 预览图 + 手动区域叠加层。
 *
 * BoxWithConstraints 计算出 bitmap 以 ContentScale.Fit 在容器内的实际显示矩形
 * （fitW x fitH），再在这个精确矩形上重套 Image + 手势 Canvas，
 * 这样手势坐标 -> bitmap 像素坐标的换算只需等比例缩放。
 */
@Composable
private fun PreviewWithManualOverlay(
    preview: Bitmap,
    manualMode: Boolean,
    manualRegions: List<RedactionRegion>,
    onAddManual: (Int, Int, Int, Int) -> Unit,
) {
    val bmpW = preview.width
    val bmpH = preview.height
    if (bmpW <= 0 || bmpH <= 0) return
    val ratio = bmpW.toFloat() / bmpH.toFloat()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val containerRatio = cw / ch
        val fitW: Float
        val fitH: Float
        if (containerRatio > ratio) {
            // 容器比例宽：高到顶，两侧留空
            fitH = ch
            fitW = ch * ratio
        } else {
            // 容器比例窤：宽到满，上下留空
            fitW = cw
            fitH = cw / ratio
        }
        val density = LocalDensity.current
        val fitWDp = with(density) { fitW.toDp() }
        val fitHDp = with(density) { fitH.toDp() }
        Box(modifier = Modifier.size(fitWDp, fitHDp)) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "脱敏预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            // 手动区域叠加层：manualMode = true 时拦截拖动手势；归 false 只显示已录入的区域轮廓。
            if (manualMode || manualRegions.isNotEmpty()) {
                ManualOverlay(
                    bitmapW = bmpW,
                    bitmapH = bmpH,
                    manualRegions = manualRegions,
                    dragEnabled = manualMode,
                    onAdd = onAddManual,
                )
            }
            if (manualMode) {
                // 左上角提示：拖动进行框选
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC1A1A1F))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "拖动框选敏感区域",
                        color = Color(0xFFEAEAF0),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualOverlay(
    bitmapW: Int,
    bitmapH: Int,
    manualRegions: List<RedactionRegion>,
    dragEnabled: Boolean,
    onAdd: (Int, Int, Int, Int) -> Unit,
) {
    // Compose 坐标（px）
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    val gestureModifier = if (dragEnabled) {
        Modifier.pointerInput(bitmapW, bitmapH) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragStart = offset
                    dragCurrent = offset
                },
                onDrag = { change, _ ->
                    change.consume()
                    dragCurrent = change.position
                },
                onDragEnd = {
                    val s = dragStart
                    val c = dragCurrent
                    if (s != null && c != null) {
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val sx = bitmapW / w
                        val sy = bitmapH / h
                        val l = (min(s.x, c.x) * sx).toInt()
                        val t = (min(s.y, c.y) * sy).toInt()
                        val r = (max(s.x, c.x) * sx).toInt()
                        val b = (max(s.y, c.y) * sy).toInt()
                        onAdd(l, t, r, b)
                    }
                    dragStart = null
                    dragCurrent = null
                },
                onDragCancel = {
                    dragStart = null
                    dragCurrent = null
                },
            )
        }
    } else {
        Modifier
    }
    Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
        val sx = size.width / bitmapW.toFloat()
        val sy = size.height / bitmapH.toFloat()
        // 已录入的手动区域：半透明红色实线框
        manualRegions.forEach { reg ->
            drawRect(
                color = Color(0xFFFF5A5A),
                topLeft = Offset(reg.left * sx, reg.top * sy),
                size = Size(
                    width = (reg.right - reg.left) * sx,
                    height = (reg.bottom - reg.top) * sy,
                ),
                style = Stroke(width = 2.5f),
            )
        }
        // 当前拖动中的矩形：白色虚线实时预览
        val s = dragStart
        val c = dragCurrent
        if (s != null && c != null) {
            val left = min(s.x, c.x)
            val top = min(s.y, c.y)
            val w = abs(c.x - s.x)
            val h = abs(c.y - s.y)
            drawRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(w, h),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f),
                ),
            )
        }
    }
}

@Composable
private fun ManualChip(
    label: String,
    active: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (active) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardBg
    val stroke = if (active) UiColors.Ai.execBtnBg else UiColors.Ai.featureCardStroke
    val fg = when {
        active -> UiColors.Ai.execBtnText
        enabled -> Color(0xFFC8C8CE)
        else -> Color(0xFF555559)
    }
    val interaction = rememberFeedbackInteractionSource()
    Row(
        modifier = modifier
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(10.dp))
            .pressFeedback(interaction)
            .throttledClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
