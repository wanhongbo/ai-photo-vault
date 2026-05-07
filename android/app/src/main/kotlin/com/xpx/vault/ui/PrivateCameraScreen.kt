package com.xpx.vault.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import com.xpx.vault.R
import com.xpx.vault.ui.components.AppButton
import com.xpx.vault.ui.components.AppButtonVariant
import com.xpx.vault.ui.theme.UiColors
import com.xpx.vault.ui.theme.UiTextSize
import com.xpx.vault.ui.vault.DEFAULT_ALBUM_NAME
import com.xpx.vault.ui.vault.VaultStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private enum class FlashUiMode {
    OFF,
    ON,
    AUTO,
}

private enum class CameraCaptureMode {
    PHOTO,
    VIDEO,
}

private enum class CaptureErrorCode {
    PERMISSION,
    HARDWARE,
    IO,
    TIMEOUT,
    UNKNOWN,
}

private data class CaptureTempResult(
    val path: String? = null,
    val errorCode: CaptureErrorCode? = null,
)

private data class CaptureShotResult(
    val success: Boolean,
    val errorCode: CaptureErrorCode? = null,
)

@Composable
fun PrivateCameraScreen(
    onBack: () -> Unit,
    onViewMedia: (String) -> Unit = {},
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
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var captureMode by remember { mutableStateOf(CameraCaptureMode.PHOTO) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationMs by remember { mutableStateOf(0L) }
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

    var lastMediaPath by rememberSaveable { mutableStateOf<String?>(null) }
    var lastMediaPreview by remember { mutableStateOf<Bitmap?>(null) }
    var rebindTick by remember { mutableStateOf(0) }
    var bindRetryCount by remember { mutableStateOf(0) }
    var bindStartMs by remember { mutableStateOf(0L) }
    var firstFrameLogged by remember { mutableStateOf(false) }
    var captureAttempts by remember { mutableStateOf(0) }
    var captureSuccessCount by remember { mutableStateOf(0) }
    var captureFailureCount by remember { mutableStateOf(0) }
    var peakMemoryMb by remember { mutableStateOf(currentMemoryUsageMb()) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var videoResolution by remember { mutableStateOf("FHD") }
    var videoFps by remember { mutableStateOf("30") }

    val orientationListener = remember {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = rotation
            }
        }
    }
    DisposableEffect(Unit) {
        orientationListener.enable()
        onDispose { orientationListener.disable() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) message = "未授予相机权限，无法拍照"
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasCameraPermission) {
                rebindTick += 1
                Log.i(CAMERA_DIAG_TAG, "event=open_resume_rebind")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(previewViewRef) {
        val previewView = previewViewRef ?: return@DisposableEffect onDispose {}
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            if (state == PreviewView.StreamState.STREAMING && !firstFrameLogged && bindStartMs > 0L) {
                firstFrameLogged = true
                val elapsed = System.currentTimeMillis() - bindStartMs
                peakMemoryMb = updatePeakMemoryMb(peakMemoryMb)
                Log.i(
                    CAMERA_DIAG_TAG,
                    "event=preview_first_frame elapsed_ms=$elapsed peak_mem_mb=$peakMemoryMb",
                )
            }
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        onDispose { previewView.previewStreamState.removeObserver(streamObserver) }
    }

    DisposableEffect(cameraRef) {
        val camera = cameraRef ?: return@DisposableEffect onDispose {}
        val stateObserver = Observer<androidx.camera.core.CameraState> { cameraState ->
            val error = cameraState.error ?: return@Observer
            bindRetryCount += 1
            message = "相机暂时不可用，正在自动恢复..."
            Log.w(
                CAMERA_DIAG_TAG,
                "event=bind_camera_error code=${error.code} retry=$bindRetryCount",
                error.cause,
            )
            if (bindRetryCount <= 3) {
                rebindTick += 1
            }
        }
        camera.cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)
        onDispose { camera.cameraInfo.cameraState.removeObserver(stateObserver) }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording?.close()
            activeRecording = null
        }
    }

    LaunchedEffect(hasCameraPermission, previewViewRef, lensFacing, flashMode, lifecycleOwner, rebindTick) {
        val previewView = previewViewRef ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        bindStartMs = System.currentTimeMillis()
        firstFrameLogged = false
        peakMemoryMb = updatePeakMemoryMb(peakMemoryMb)
        Log.i(
            CAMERA_DIAG_TAG,
            "event=bind_start lens=$lensFacing flash=$flashMode model=${maskedDeviceModel()}",
        )
        bindCameraUseCases(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = lensFacing,
            flashMode = flashMode,
            onReady = { capture, video, camera, flashAvailable, minZoom, maxZoom, minExposure, maxExposure ->
                imageCapture = capture
                videoCapture = video
                cameraRef = camera
                hasFlashUnit = flashAvailable
                if (!flashAvailable) flashMode = FlashUiMode.OFF
                minZoomRatio = minZoom
                maxZoomRatio = maxZoom
                zoomRatio = zoomRatio.coerceIn(minZoom, maxZoom)
                exposureRange = minExposure..maxExposure
                exposureIndex = exposureIndex.coerceIn(minExposure, maxExposure)
                bindRetryCount = 0
                Log.i(
                    CAMERA_DIAG_TAG,
                    "event=bind_ready lens=$lensFacing flash=$flashAvailable zoom=[$minZoom,$maxZoom] exposure=[$minExposure,$maxExposure]",
                )
            },
            onFallbackLens = { fallbackLens ->
                lensFacing = fallbackLens
                message = "当前镜头不可用，已切换到可用镜头"
                Log.w(CAMERA_DIAG_TAG, "event=bind_lens_fallback to=$fallbackLens")
            },
            onBindFailed = { throwable ->
                bindRetryCount += 1
                message = "相机启动失败，正在尝试恢复..."
                Log.e(CAMERA_DIAG_TAG, "event=bind_failed retry=$bindRetryCount", throwable)
                if (bindRetryCount <= 3) rebindTick += 1
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

    LaunchedEffect(lastMediaPath) {
        val path = lastMediaPath
        if (path.isNullOrEmpty()) {
            lastMediaPreview = null
            return@LaunchedEffect
        }
        lastMediaPreview = if (isVideoPath(path)) {
            decodeVideoThumbnail(context, path)
        } else {
            decodePreviewBitmap(context, path, 1600)
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingDurationMs = 0L
            return@LaunchedEffect
        }
        while (isRecording) {
            delay(1000)
            recordingDurationMs += 1000
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            Box(Modifier.fillMaxSize().pointerInput(previewViewRef, cameraRef) {
                detectTapGestures { offset ->
                    val preview = previewViewRef ?: return@detectTapGestures
                    val camera = cameraRef ?: return@detectTapGestures
                    val point = preview.meteringPointFactory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(1500, TimeUnit.MILLISECONDS).build()
                    camera.cameraControl.startFocusAndMetering(action)
                    focusMarker = offset
                    scope.launch { delay(700); focusMarker = null }
                }
            }.pointerInput(cameraRef, minZoomRatio, maxZoomRatio) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    if (zoomChange != 1f) zoomRatio = (zoomRatio * zoomChange).coerceIn(minZoomRatio, maxZoomRatio)
                }
            }) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewViewRef = this
                    }
                })
                if (showGrid) CameraGridOverlay()
                focusMarker?.let { FocusMarker(marker = it) }
                if (shutterOverlay) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.16f)))
                countdownRemaining?.let { remaining ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = remaining.toString(), color = Color.White, fontSize = UiTextSize.homeTitle, fontWeight = FontWeight.Bold)
                    }
                }
                if (capturing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = UiColors.Home.navItemActive)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("等待相机权限授权…", color = UiColors.Home.subtitle, fontSize = UiTextSize.homeEmptyBody)
            }
        }

        if (showSettingsPanel) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showSettingsPanel = false },
                    ),
            )
        }
        IconButton(
            onClick = { showSettingsPanel = !showSettingsPanel },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 8.dp).size(40.dp).background(Color(0xCC1A1A1A), RoundedCornerShape(20.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camera_menu_dots),
                contentDescription = stringResource(R.string.camera_settings_title),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        AnimatedVisibility(
            visible = showSettingsPanel,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
        ) {
            CameraSettingsPanel(
                captureMode = captureMode,
                flashMode = flashMode,
                timerSeconds = timerSeconds,
                showGrid = showGrid,
                hasFlashUnit = hasFlashUnit,
                exposureRange = exposureRange,
                exposureIndex = exposureIndex,
                videoResolution = videoResolution,
                videoFps = videoFps,
                onFlashModeChange = { flashMode = it },
                onTimerSecondsChange = { timerSeconds = it },
                onShowGridChange = { showGrid = it },
                onExposureIndexChange = { exposureIndex = it },
                onVideoResolutionChange = { videoResolution = it },
                onVideoFpsChange = { videoFps = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        message?.let {
            Text(text = it, color = UiColors.Home.subtitle, fontSize = UiTextSize.homeNavLabel, modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp), textAlign = TextAlign.Center)
        }

        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasCameraPermission) {
                ZoomRail(minZoom = minZoomRatio, maxZoom = maxZoomRatio, zoomRatio = zoomRatio, onZoomRatioChanged = { zoomRatio = it.coerceIn(minZoomRatio, maxZoomRatio) }, onSelectPreset = { preset -> zoomRatio = preset.coerceIn(minZoomRatio, maxZoomRatio) })
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }, enabled = hasCameraPermission, modifier = Modifier.size(48.dp).background(Color(0x33FFFFFF), RoundedCornerShape(24.dp))) {
                    Icon(
                        painter = painterResource(R.drawable.ic_camera_switch),
                        contentDescription = stringResource(R.string.camera_flip_lens),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ShutterButton(enabled = hasCameraPermission && !capturing && if (captureMode == CameraCaptureMode.PHOTO) imageCapture != null else videoCapture != null, recording = isRecording, onClick = {
                        if (capturing) return@ShutterButton
                        if (captureMode == CameraCaptureMode.VIDEO) {
                            val video = videoCapture ?: return@ShutterButton
                            if (isRecording) {
                                activeRecording?.stop()
                                return@ShutterButton
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                if (timerSeconds > 0) {
                                    for (second in timerSeconds downTo 1) {
                                        countdownRemaining = second
                                        delay(1000)
                                    }
                                    countdownRemaining = null
                                }
                                val outputFile = VaultStore.reserveCameraTarget(context, DEFAULT_ALBUM_NAME, "mp4")
                                val output = FileOutputOptions.Builder(outputFile).build()
                                val pending: PendingRecording = video.output.prepareRecording(context, output)
                                message = null
                                isRecording = true
                                recordingDurationMs = 0L
                                Log.i(CAMERA_DIAG_TAG, "event=video_record_start path=${outputFile.name}")
                                activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                    when (event) {
                                        is VideoRecordEvent.Finalize -> {
                                            isRecording = false
                                            activeRecording?.close()
                                            activeRecording = null
                                            if (!event.hasError()) {
                                                // 录像落盘后紧接着把明文 temp 加密入库。
                                                scope.launch {
                                                    val vaultPath = VaultStore.finalizeCameraCapture(context, outputFile)
                                                    if (vaultPath != null) {
                                                        lastMediaPath = vaultPath
                                                        message = "视频已保存到保险箱"
                                                        Log.i(CAMERA_DIAG_TAG, "event=video_record_success duration_ms=$recordingDurationMs")
                                                        // 录像入库后触发一次增量 AI 扫描（视频当前仅用于 Cleanup/停止笖选，对分类影响小）。
                                                        com.xpx.vault.ai.AiScanEntryPoint.from(context).requestScan()
                                                    } else {
                                                        message = "视频入库失败，请重试"
                                                        Log.e(CAMERA_DIAG_TAG, "event=video_finalize_failed")
                                                    }
                                                }
                                            } else {
                                                outputFile.delete()
                                                message = "录像失败，请重试"
                                                Log.e(CAMERA_DIAG_TAG, "event=video_record_failed code=${event.error}")
                                            }
                                        }
                                    }
                                }
                            }
                            return@ShutterButton
                        }
                        val capture = imageCapture ?: return@ShutterButton
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        message = null
                        scope.launch {
                            captureAttempts += 1
                            val captureStart = System.currentTimeMillis()
                            Log.i(CAMERA_DIAG_TAG, "event=capture_start attempt=$captureAttempts")
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
                            val result = captureToTempFile(context, capture)
                            capturing = false
                            val elapsed = System.currentTimeMillis() - captureStart
                            peakMemoryMb = updatePeakMemoryMb(peakMemoryMb)
                            if (result.path != null) {
                                captureSuccessCount += 1
                                val vaultPath = savePendingToVault(context, result.path)
                                if (vaultPath != null) {
                                    lastMediaPath = vaultPath
                                    message = "已保存到保险箱"
                                    // 拍照入库后触发一次增量 AI 扫描。
                                    com.xpx.vault.ai.AiScanEntryPoint.from(context).requestScan()
                                } else {
                                    message = "保存失败（存储错误），请重试"
                                }
                                Log.i(CAMERA_DIAG_TAG, "event=capture_success elapsed_ms=$elapsed success=$captureSuccessCount fail=$captureFailureCount peak_mem_mb=$peakMemoryMb")
                            } else {
                                captureFailureCount += 1
                                val failRate = (captureFailureCount * 100f / captureAttempts).roundToInt()
                                val code = result.errorCode ?: CaptureErrorCode.UNKNOWN
                                message = captureErrorMessage(code)
                                Log.e(CAMERA_DIAG_TAG, "event=capture_failed code=$code elapsed_ms=$elapsed fail_rate_pct=$failRate success=$captureSuccessCount fail=$captureFailureCount")
                            }
                        }
                    })
                    if (isRecording) {
                        Text(text = formatRecordingDuration(recordingDurationMs), color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable(enabled = lastMediaPath != null) {
                            lastMediaPath?.let { onViewMedia(it) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    lastMediaPreview?.let { preview ->
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                    } ?: Box(modifier = Modifier.size(36.dp).background(Color(0x66FFFFFF), RoundedCornerShape(18.dp)))
                }
            }
            Spacer(Modifier.height(16.dp))
            val modeBarScroll = rememberScrollState()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.horizontalScroll(modeBarScroll),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(20.dp)).padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val modes = listOf(CameraCaptureMode.PHOTO to R.string.camera_mode_photo, CameraCaptureMode.VIDEO to R.string.camera_mode_video)
                        modes.forEach { (mode, labelRes) ->
                            val selected = captureMode == mode
                            Box(
                                Modifier
                                    .clickable { captureMode = mode }
                                    .background(if (selected) Color(0xFF4A9EFF) else Color.Transparent, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    color = if (selected) Color.White else Color(0xFFEAF1FF),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    lensFacing: Int,
    flashMode: FlashUiMode,
    onReady: (ImageCapture, VideoCapture<Recorder>, Camera, Boolean, Float, Float, Int, Int) -> Unit,
    onFallbackLens: (Int) -> Unit,
    onBindFailed: (Throwable) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
        {
            runCatching {
                val provider = providerFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(
                        when (flashMode) {
                            FlashUiMode.OFF -> ImageCapture.FLASH_MODE_OFF
                            FlashUiMode.ON -> ImageCapture.FLASH_MODE_ON
                            FlashUiMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                        },
                    )
                    .setTargetRotation(context.display?.rotation ?: Surface.ROTATION_0)
                    .build()
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                        ),
                    )
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                fun bindWithLens(targetLens: Int): Camera {
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    provider.unbindAll()
                    return provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.Builder().requireLensFacing(targetLens).build(),
                        preview,
                        imageCapture,
                        videoCapture,
                    )
                }
                var usedLens = lensFacing
                val camera = runCatching { bindWithLens(lensFacing) }.getOrElse {
                    val fallback = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                    usedLens = fallback
                    bindWithLens(fallback)
                }
                if (usedLens != lensFacing) onFallbackLens(usedLens)
                val zoomState = camera.cameraInfo.zoomState.value
                val exposureState = camera.cameraInfo.exposureState
                onReady(
                    imageCapture,
                    videoCapture,
                    camera,
                    camera.cameraInfo.hasFlashUnit(),
                    zoomState?.minZoomRatio ?: 1f,
                    zoomState?.maxZoomRatio ?: 1f,
                    exposureState.exposureCompensationRange.lower,
                    exposureState.exposureCompensationRange.upper,
                )
            }.onFailure(onBindFailed)
        },
        ContextCompat.getMainExecutor(context),
    )
}

private suspend fun captureToTempFile(
    context: Context,
    imageCapture: ImageCapture,
): CaptureTempResult = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "pv_capture_${System.currentTimeMillis()}.jpg")
    val shotResult = withTimeoutOrNull(10_000) {
        suspendImageCaptureToFile(context, imageCapture, tempFile)
    } ?: return@withContext CaptureTempResult(errorCode = CaptureErrorCode.TIMEOUT)
    if (shotResult.success) {
        CaptureTempResult(path = tempFile.absolutePath)
    } else {
        CaptureTempResult(errorCode = shotResult.errorCode ?: CaptureErrorCode.UNKNOWN)
    }
}

private fun writeJpegWithRotation(
    bytes: ByteArray,
    rotationDegrees: Int,
    target: File,
) {
    if (rotationDegrees == 0) {
        target.outputStream().use { it.write(bytes) }
        return
    }
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: run {
            target.outputStream().use { it.write(bytes) }
            return
        }
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = try {
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    } catch (e: OutOfMemoryError) {
        src.recycle()
        throw e
    }
    if (rotated !== src) src.recycle()
    target.outputStream().use { out ->
        rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
    }
    rotated.recycle()
}

private suspend fun savePendingToVault(
    context: Context,
    path: String,
): String? = withContext(Dispatchers.IO) {
    val source = File(path)
    if (!source.exists()) return@withContext null
    runCatching {
        // source 已是拍照生成的明文 temp jpg，直接走 finalize 加密入库即可。
        VaultStore.finalizeCameraCapture(context, source)
    }.getOrNull()
}

private suspend fun decodePreviewBitmap(
    context: Context,
    path: String,
    maxPx: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        // vault 下均为密文；先整体解密到 byte[]，再走 BitmapFactory.decodeByteArray 采样解码。
        val bytes = com.xpx.vault.data.crypto.VaultCipher.get(context).decryptToByteArray(File(path))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var inSampleSize = 1
        while (bounds.outWidth / inSampleSize > maxPx || bounds.outHeight / inSampleSize > maxPx) {
            inSampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()
}

private suspend fun suspendImageCaptureToFile(
    context: Context,
    imageCapture: ImageCapture,
    target: File,
): CaptureShotResult = suspendCancellableCoroutine { continuation ->
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotationDegrees = image.imageInfo.rotationDegrees
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                image.close()
                val result = runCatching {
                    writeJpegWithRotation(bytes, rotationDegrees, target)
                    CaptureShotResult(success = true)
                }.getOrElse {
                    Log.w(CAMERA_DIAG_TAG, "event=capture_write_failed msg=${it.message}")
                    CaptureShotResult(success = false, errorCode = CaptureErrorCode.IO)
                }
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onError(exception: ImageCaptureException) {
                if (continuation.isActive) {
                    val code = when (exception.imageCaptureError) {
                        ImageCapture.ERROR_FILE_IO -> CaptureErrorCode.IO
                        ImageCapture.ERROR_CAMERA_CLOSED,
                        ImageCapture.ERROR_CAPTURE_FAILED,
                        ImageCapture.ERROR_INVALID_CAMERA,
                        -> CaptureErrorCode.HARDWARE
                        ImageCapture.ERROR_UNKNOWN -> CaptureErrorCode.UNKNOWN
                        else -> CaptureErrorCode.UNKNOWN
                    }
                    continuation.resume(CaptureShotResult(success = false, errorCode = code))
                }
            }
        },
    )
}

@Composable
private fun ZoomRail(
    minZoom: Float,
    maxZoom: Float,
    zoomRatio: Float,
    onZoomRatioChanged: (Float) -> Unit,
    onSelectPreset: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 80.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomPresetButton(text = ".7", enabled = minZoom <= 0.7f, selected = kotlin.math.abs(zoomRatio - 0.7f) < 0.08f) {
            onSelectPreset(0.7f)
        }
        ZoomPresetButton(text = "1x", enabled = true, selected = kotlin.math.abs(zoomRatio - 1f) < 0.08f) {
            onSelectPreset(1f)
        }
        ZoomPresetButton(text = "2", enabled = maxZoom >= 2f, selected = kotlin.math.abs(zoomRatio - 2f) < 0.08f) {
            onSelectPreset(2f)
        }
    }
}

@Composable
private fun ZoomPresetButton(
    text: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .background(
                if (selected) Color(0xFF4A9EFF) else Color.Transparent,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (!enabled) Color(0x66EAF1FF) else if (selected) Color.White else Color(0x99EAF1FF),
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    recording: Boolean,
    onClick: () -> Unit,
) {
    val innerSize by animateFloatAsState(
        targetValue = if (recording) 38f else if (enabled) 54f else 48f,
        animationSpec = tween(220),
        label = "shutterInnerSize",
    )
    Box(
        modifier = Modifier
            .size(78.dp)
            .background(if (enabled) Color(0xF2FFFFFF) else Color(0x66FFFFFF), RoundedCornerShape(42.dp))
            .border(2.dp, Color(0xFFB5C8EE), RoundedCornerShape(42.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize.dp)
                .background(
                    if (recording) Color(0xFFE74C3C) else Color(0xFF1A2A40),
                    RoundedCornerShape(if (recording) 10.dp else 30.dp),
                ),
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
            AppButton(
                text = "重拍",
                onClick = onRetake,
                variant = AppButtonVariant.SECONDARY,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = if (saving) "保存中…" else "保存到保险箱",
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun formatZoom(value: Float): String {
    val rounded = ((value * 10f).roundToInt() / 10f)
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

private fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun captureErrorMessage(code: CaptureErrorCode): String = when (code) {
    CaptureErrorCode.PERMISSION -> "缺少相机权限，请重新授权"
    CaptureErrorCode.HARDWARE -> "拍照失败（相机忙或被占用），请重试"
    CaptureErrorCode.IO -> "拍照失败（存储异常），请重试"
    CaptureErrorCode.TIMEOUT -> "拍照超时，请检查设备状态后重试"
    CaptureErrorCode.UNKNOWN -> "拍照失败，请稍后重试"
}

private fun updatePeakMemoryMb(currentPeakMb: Long): Long = maxOf(currentPeakMb, currentMemoryUsageMb())

private fun currentMemoryUsageMb(): Long {
    val runtime = Runtime.getRuntime()
    val used = runtime.totalMemory() - runtime.freeMemory()
    return used / (1024L * 1024L)
}

private fun maskedDeviceModel(): String {
    val model = Build.MODEL.orEmpty().trim()
    if (model.isEmpty()) return "unknown"
    val prefix = model.take(3)
    return "$prefix***${model.length}"
}

@Composable
private fun CameraSettingsPanel(
    captureMode: CameraCaptureMode,
    flashMode: FlashUiMode,
    timerSeconds: Int,
    showGrid: Boolean,
    hasFlashUnit: Boolean,
    exposureRange: IntRange,
    exposureIndex: Int,
    videoResolution: String,
    videoFps: String,
    onFlashModeChange: (FlashUiMode) -> Unit,
    onTimerSecondsChange: (Int) -> Unit,
    onShowGridChange: (Boolean) -> Unit,
    onExposureIndexChange: (Int) -> Unit,
    onVideoResolutionChange: (String) -> Unit,
    onVideoFpsChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelScroll = rememberScrollState()
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.48f).dp
    Column(
        modifier = modifier
            .background(Color(0xCC1A1A1A), RoundedCornerShape(20.dp))
            .heightIn(max = maxPanelHeight)
            .verticalScroll(panelScroll)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.camera_settings_title),
            color = Color(0xFF8EA2C0),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (captureMode) {
            CameraCaptureMode.PHOTO -> {
                SettingsRow(label = stringResource(R.string.camera_settings_row_flash)) {
                    OptionPill(text = stringResource(R.string.camera_pill_off), selected = flashMode == FlashUiMode.OFF, enabled = hasFlashUnit, onClick = { onFlashModeChange(FlashUiMode.OFF) })
                    OptionPill(text = stringResource(R.string.camera_pill_auto), selected = flashMode == FlashUiMode.AUTO, enabled = hasFlashUnit, onClick = { onFlashModeChange(FlashUiMode.AUTO) })
                    OptionPill(text = stringResource(R.string.camera_pill_on), selected = flashMode == FlashUiMode.ON, enabled = hasFlashUnit, onClick = { onFlashModeChange(FlashUiMode.ON) })
                }
                SettingsRow(label = stringResource(R.string.camera_settings_row_timer)) {
                    OptionPill(text = stringResource(R.string.camera_pill_off), selected = timerSeconds == 0, onClick = { onTimerSecondsChange(0) })
                    OptionPill(text = stringResource(R.string.camera_timer_3s), selected = timerSeconds == 3, onClick = { onTimerSecondsChange(3) })
                    OptionPill(text = stringResource(R.string.camera_timer_10s), selected = timerSeconds == 10, onClick = { onTimerSecondsChange(10) })
                }
                SettingsRow(label = stringResource(R.string.camera_settings_row_grid)) {
                    OptionPill(text = stringResource(R.string.camera_pill_off), selected = !showGrid, onClick = { onShowGridChange(false) })
                    OptionPill(text = stringResource(R.string.camera_pill_on), selected = showGrid, onClick = { onShowGridChange(true) })
                }
                if (exposureRange.first != exposureRange.last) {
                    SettingsRow(label = stringResource(R.string.camera_settings_row_exposure), horizontalScrollOptions = false) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (exposureIndex > 0) "+$exposureIndex" else exposureIndex.toString(),
                                color = UiColors.Home.subtitle,
                                fontSize = 11.sp,
                                modifier = Modifier.widthIn(min = 28.dp),
                            )
                            Slider(
                                value = exposureIndex.toFloat(),
                                onValueChange = { onExposureIndexChange(it.roundToInt().coerceIn(exposureRange.first, exposureRange.last)) },
                                valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                                steps = (exposureRange.last - exposureRange.first - 1).coerceAtLeast(0),
                                modifier = Modifier.weight(1f).height(24.dp),
                            )
                            if (exposureIndex != 0) {
                                Text(
                                    text = stringResource(R.string.camera_control_exposure_auto),
                                    color = UiColors.Home.navItemActive,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 48.dp)
                                        .clickable { onExposureIndexChange(0) },
                                )
                            } else {
                                Spacer(Modifier.width(48.dp))
                            }
                        }
                    }
                }
            }
            CameraCaptureMode.VIDEO -> {
                SettingsRow(label = stringResource(R.string.camera_settings_row_flash)) {
                    OptionPill(text = stringResource(R.string.camera_pill_off), selected = flashMode == FlashUiMode.OFF, enabled = hasFlashUnit, onClick = { onFlashModeChange(FlashUiMode.OFF) })
                    OptionPill(text = stringResource(R.string.camera_pill_auto), selected = flashMode == FlashUiMode.AUTO, enabled = hasFlashUnit, onClick = { onFlashModeChange(FlashUiMode.AUTO) })
                    OptionPill(text = stringResource(R.string.camera_pill_on), selected = flashMode == FlashUiMode.ON, enabled = hasFlashUnit, onClick = { onFlashModeChange(FlashUiMode.ON) })
                }
                SettingsRow(label = stringResource(R.string.camera_settings_row_resolution)) {
                    OptionPill(text = stringResource(R.string.camera_resolution_fhd), selected = videoResolution == "FHD", onClick = { onVideoResolutionChange("FHD") })
                    OptionPill(text = stringResource(R.string.camera_resolution_4k), selected = videoResolution == "4K", onClick = { onVideoResolutionChange("4K") })
                }
                SettingsRow(label = stringResource(R.string.camera_settings_row_fps)) {
                    OptionPill(text = stringResource(R.string.camera_fps_30), selected = videoFps == "30", onClick = { onVideoFpsChange("30") })
                    OptionPill(text = stringResource(R.string.camera_fps_60), selected = videoFps == "60", onClick = { onVideoFpsChange("60") })
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    horizontalScrollOptions: Boolean = true,
    content: @Composable () -> Unit,
) {
    val rowScroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color(0xFFEAF1FF),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        if (horizontalScrollOptions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScroll),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun OptionPill(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 32.dp)
            .heightIn(min = 32.dp)
            .wrapContentWidth()
            .background(
                if (!enabled) Color(0x1FFFFFFF) else if (selected) Color(0xFF4A9EFF) else Color(0x33FFFFFF),
                RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (!enabled) Color(0x66EAF1FF) else if (selected) Color.White else Color(0xFFEAF1FF),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun isVideoPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.endsWith(".mp4") ||
        lower.endsWith(".m4v") ||
        lower.endsWith(".mov") ||
        lower.endsWith(".3gp") ||
        lower.endsWith(".webm") ||
        lower.endsWith(".mkv")
}

private fun decodeVideoThumbnail(context: Context, path: String): Bitmap? {
    return runCatching {
        // 视频密文无法直接给 MediaMetadataRetriever；先解密到 cache 临时文件，取完帧再删。
        val cacheDir = File(context.cacheDir, "camera_thumb").apply { mkdirs() }
        val tmp = com.xpx.vault.data.crypto.VaultCipher.get(context).decryptToTempFile(
            File(path),
            cacheDir,
            "thumb_${System.nanoTime()}.mp4",
        )
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(tmp.absolutePath)
            val bitmap = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            bitmap
        } finally {
            tmp.delete()
        }
    }.getOrNull()
}

private const val CAMERA_DIAG_TAG = "PrivateCameraDiag"
