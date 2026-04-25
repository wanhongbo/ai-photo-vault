package com.photovault.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.photovault.app.ui.components.AppTopBar
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiTextSize
import com.photovault.app.ui.vault.DEFAULT_ALBUM_NAME
import com.photovault.app.ui.vault.VaultStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private enum class FlashUiMode {
    OFF,
    ON,
    AUTO,
}

@Composable
fun PrivateCameraScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(FlashUiMode.OFF) }
    var showGrid by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var shutterOverlay by remember { mutableStateOf(false) }
    var focusMarker by remember { mutableStateOf<Offset?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var timerSeconds by remember { mutableStateOf(0) }
    var countdownRemaining by remember { mutableStateOf<Int?>(null) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var exposureRange by remember { mutableStateOf(IntRange(0, 0)) }
    var exposureIndex by remember { mutableStateOf(0) }

    var pendingCapturePath by remember { mutableStateOf<String?>(null) }
    var pendingPreview by remember { mutableStateOf<Bitmap?>(null) }
    var savingPending by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) message = "未授予相机权限，无法拍照"
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(hasCameraPermission, previewViewRef, lensFacing, flashMode, lifecycleOwner) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        bindCameraUseCases(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = lensFacing,
            flashMode = flashMode,
            onReady = { capture, camera, flashAvailable, minZoom, maxZoom, minExposure, maxExposure ->
                imageCapture = capture
                cameraRef = camera
                hasFlashUnit = flashAvailable
                if (!flashAvailable) flashMode = FlashUiMode.OFF
                minZoomRatio = minZoom
                maxZoomRatio = maxZoom
                zoomRatio = zoomRatio.coerceIn(minZoom, maxZoom)
                exposureRange = minExposure..maxExposure
                exposureIndex = exposureIndex.coerceIn(minExposure, maxExposure)
            },
        )
    }

    LaunchedEffect(cameraRef, zoomRatio) {
        cameraRef?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(minZoomRatio, maxZoomRatio))
    }

    LaunchedEffect(cameraRef, exposureIndex) {
        if (exposureRange.first == exposureRange.last) return@LaunchedEffect
        cameraRef?.cameraControl?.setExposureCompensationIndex(exposureIndex)
    }

    LaunchedEffect(pendingCapturePath) {
        val path = pendingCapturePath
        if (path.isNullOrEmpty()) {
            pendingPreview = null
            return@LaunchedEffect
        }
        pendingPreview = decodePreviewBitmap(path, 1600)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTopBar(title = "隐私相机", onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))

        if (pendingCapturePath != null) {
            PendingCapturePreview(
                bitmap = pendingPreview,
                saving = savingPending,
                onRetake = {
                    pendingCapturePath?.let { File(it).delete() }
                    pendingCapturePath = null
                    pendingPreview = null
                    message = "已取消，返回取景"
                },
                onSave = {
                    val path = pendingCapturePath ?: return@PendingCapturePreview
                    if (savingPending) return@PendingCapturePreview
                    savingPending = true
                    scope.launch {
                        val saved = savePendingToVault(context, path)
                        savingPending = false
                        if (saved) {
                            message = "已保存到保险箱"
                            pendingCapturePath = null
                            pendingPreview = null
                            delay(220)
                            onBack()
                        } else {
                            message = "保存失败，请重试"
                        }
                    }
                },
            )
            return@Column
        }

        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(18.dp))
                    .pointerInput(previewViewRef, cameraRef) {
                        detectTapGestures { offset ->
                            val preview = previewViewRef ?: return@detectTapGestures
                            val camera = cameraRef ?: return@detectTapGestures
                            val point = preview.meteringPointFactory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
                            )
                                .setAutoCancelDuration(1500, TimeUnit.MILLISECONDS)
                                .build()
                            camera.cameraControl.startFocusAndMetering(action)
                            focusMarker = offset
                            scope.launch {
                                delay(700)
                                focusMarker = null
                            }
                        }
                    }
                    .pointerInput(cameraRef, minZoomRatio, maxZoomRatio) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            if (zoomChange != 1f) {
                                zoomRatio = (zoomRatio * zoomChange).coerceIn(minZoomRatio, maxZoomRatio)
                            }
                        }
                    },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewViewRef = this
                        }
                    },
                )

                if (showGrid) CameraGridOverlay()
                focusMarker?.let { FocusMarker(marker = it) }

                if (shutterOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.16f)),
                    )
                }

                countdownRemaining?.let { remaining ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = remaining.toString(),
                            color = Color.White,
                            fontSize = UiTextSize.homeTitle,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (capturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = UiColors.Home.navItemActive,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(UiColors.Home.emptyCardBg, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "等待相机权限授权…",
                    color = UiColors.Home.subtitle,
                    fontSize = UiTextSize.homeEmptyBody,
                )
            }
        }

        message?.let {
            Text(
                text = it,
                color = UiColors.Home.subtitle,
                fontSize = UiTextSize.homeNavLabel,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraActionChip(
                enabled = hasCameraPermission && hasFlashUnit,
                onClick = {
                    flashMode = when (flashMode) {
                        FlashUiMode.OFF -> FlashUiMode.ON
                        FlashUiMode.ON -> FlashUiMode.AUTO
                        FlashUiMode.AUTO -> FlashUiMode.OFF
                    }
                },
                prefix = "闪",
                label = when (flashMode) {
                    FlashUiMode.OFF -> "闪光灯关"
                    FlashUiMode.ON -> "闪光灯开"
                    FlashUiMode.AUTO -> "闪光灯自动"
                },
            )
            CameraActionChip(
                enabled = hasCameraPermission,
                onClick = { showGrid = !showGrid },
                prefix = "宫",
                label = if (showGrid) "网格开" else "网格关",
            )
            CameraActionChip(
                enabled = hasCameraPermission,
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                prefix = "切",
                label = if (lensFacing == CameraSelector.LENS_FACING_BACK) "后摄" else "前摄",
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraActionChip(
                enabled = hasCameraPermission,
                onClick = {
                    timerSeconds = when (timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                },
                prefix = "时",
                label = if (timerSeconds == 0) "定时关" else "定时${timerSeconds}s",
            )
            ZoomPresetChip(zoom = 0.6f, currentZoom = zoomRatio, enabled = hasCameraPermission && minZoomRatio <= 0.6f) {
                zoomRatio = 0.6f.coerceIn(minZoomRatio, maxZoomRatio)
            }
            ZoomPresetChip(zoom = 1f, currentZoom = zoomRatio, enabled = hasCameraPermission) {
                zoomRatio = 1f.coerceIn(minZoomRatio, maxZoomRatio)
            }
            ZoomPresetChip(zoom = 2f, currentZoom = zoomRatio, enabled = hasCameraPermission && maxZoomRatio >= 2f) {
                zoomRatio = 2f.coerceIn(minZoomRatio, maxZoomRatio)
            }
        }

        if (exposureRange.first != exposureRange.last) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "曝光 ${if (exposureIndex > 0) "+" else ""}$exposureIndex",
                    color = UiColors.Home.subtitle,
                    fontSize = UiTextSize.homeNavLabel,
                )
                Slider(
                    value = exposureIndex.toFloat(),
                    onValueChange = { exposureIndex = it.roundToInt().coerceIn(exposureRange.first, exposureRange.last) },
                    valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                    steps = (exposureRange.last - exposureRange.first - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShutterButton(
                enabled = hasCameraPermission && imageCapture != null && !capturing,
                onClick = {
                    val capture = imageCapture ?: return@ShutterButton
                    if (capturing) return@ShutterButton
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    message = null
                    scope.launch {
                        if (timerSeconds > 0) {
                            for (second in timerSeconds downTo 1) {
                                countdownRemaining = second
                                delay(1000)
                            }
                            countdownRemaining = null
                        }
                        shutterOverlay = true
                        delay(90)
                        shutterOverlay = false
                        capturing = true
                        val tempPath = captureToTempFile(context, capture)
                        capturing = false
                        if (tempPath != null) {
                            pendingCapturePath = tempPath
                        } else {
                            message = "拍照失败，请重试"
                        }
                    }
                },
            )
        }
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    lensFacing: Int,
    flashMode: FlashUiMode,
    onReady: (ImageCapture, Camera, Boolean, Float, Float, Int, Int) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
        {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(
                    when (flashMode) {
                        FlashUiMode.OFF -> ImageCapture.FLASH_MODE_OFF
                        FlashUiMode.ON -> ImageCapture.FLASH_MODE_ON
                        FlashUiMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    },
                )
                .build()
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                imageCapture,
            )
            val zoomState = camera.cameraInfo.zoomState.value
            val exposureState = camera.cameraInfo.exposureState
            onReady(
                imageCapture,
                camera,
                camera.cameraInfo.hasFlashUnit(),
                zoomState?.minZoomRatio ?: 1f,
                zoomState?.maxZoomRatio ?: 1f,
                exposureState.exposureCompensationRange.lower,
                exposureState.exposureCompensationRange.upper,
            )
        },
        ContextCompat.getMainExecutor(context),
    )
}

private suspend fun captureToTempFile(
    context: Context,
    imageCapture: ImageCapture,
): String? = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "pv_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
    val ok = suspendImageCapture(context, imageCapture, outputOptions)
    if (ok) tempFile.absolutePath else null
}

private suspend fun savePendingToVault(
    context: Context,
    path: String,
): Boolean = withContext(Dispatchers.IO) {
    val source = File(path)
    if (!source.exists()) return@withContext false
    runCatching {
        val target = VaultStore.reserveCameraTarget(context, DEFAULT_ALBUM_NAME)
        source.copyTo(target, overwrite = true)
        source.delete()
    }.isSuccess
}

private suspend fun decodePreviewBitmap(
    path: String,
    maxPx: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var inSampleSize = 1
    while (bounds.outWidth / inSampleSize > maxPx || bounds.outHeight / inSampleSize > maxPx) {
        inSampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        this.inSampleSize = inSampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeFile(path, options)
}

private suspend fun suspendImageCapture(
    context: Context,
    imageCapture: ImageCapture,
    outputOptions: ImageCapture.OutputFileOptions,
): Boolean = suspendCancellableCoroutine { continuation ->
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onError(exception: ImageCaptureException) {
                if (continuation.isActive) continuation.resume(false)
            }
        },
    )
}

@Composable
private fun CameraActionChip(
    enabled: Boolean,
    onClick: () -> Unit,
    prefix: String,
    label: String,
) {
    val background = if (enabled) Color(0xB3223144) else Color(0x66223144)
    Row(
        modifier = Modifier
            .background(background, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x33EAF1FF), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = prefix,
            color = Color(0xFFEAF1FF),
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color(0xFFEAF1FF),
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ZoomPresetChip(
    zoom: Float,
    currentZoom: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val selected = enabled && kotlin.math.abs(currentZoom - zoom) < 0.08f
    val bg = if (selected) Color(0xFFEAF1FF) else Color(0x55223144)
    val content = if (selected) Color(0xFF1A2A40) else Color(0xFFEAF1FF)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x44EAF1FF), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${zoom}x",
            color = content,
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val innerSize by animateFloatAsState(
        targetValue = if (enabled) 58f else 52f,
        animationSpec = tween(220),
        label = "shutterInnerSize",
    )
    Box(
        modifier = Modifier
            .size(84.dp)
            .background(if (enabled) Color(0xF2FFFFFF) else Color(0x66FFFFFF), RoundedCornerShape(42.dp))
            .border(2.dp, Color(0xFFB5C8EE), RoundedCornerShape(42.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize.dp)
                .background(Color(0xFF1A2A40), RoundedCornerShape(30.dp)),
        )
    }
}

@Composable
private fun CameraGridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        val stroke = 1.dp.toPx()
        drawLine(Color(0x55FFFFFF), Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = stroke)
        drawLine(Color(0x55FFFFFF), Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = stroke)
        drawLine(Color(0x55FFFFFF), Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = stroke)
        drawLine(Color(0x55FFFFFF), Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = stroke)
    }
}

@Composable
private fun FocusMarker(marker: Offset) {
    var visible by remember(marker) { mutableStateOf(true) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(280),
        label = "focusAlpha",
    )
    LaunchedEffect(marker) {
        delay(420)
        visible = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = (marker.x - 22f).dp, top = (marker.y - 22f).dp)
                .size(44.dp)
                .border(1.6.dp, Color(0xCCF4FAFF).copy(alpha = alpha), RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun PendingCapturePreview(
    bitmap: Bitmap?,
    saving: Boolean,
    onRetake: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(color = UiColors.Home.navItemActive)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CameraActionChip(
                enabled = !saving,
                onClick = onRetake,
                prefix = "重",
                label = "重拍",
            )
            CameraActionChip(
                enabled = !saving,
                onClick = onSave,
                prefix = if (saving) "…" else "存",
                label = if (saving) "保存中" else "保存到保险箱",
            )
        }
    }
}
