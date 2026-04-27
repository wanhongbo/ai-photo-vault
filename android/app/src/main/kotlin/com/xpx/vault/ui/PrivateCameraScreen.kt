package com.xpx.vault.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.xpx.vault.ui.components.AppTopBar
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

    var pendingCapturePath by remember { mutableStateOf<String?>(null) }
    var pendingPreview by remember { mutableStateOf<Bitmap?>(null) }
    var savingPending by remember { mutableStateOf(false) }
    var rebindTick by remember { mutableStateOf(0) }
    var bindRetryCount by remember { mutableStateOf(0) }
    var bindStartMs by remember { mutableStateOf(0L) }
    var firstFrameLogged by remember { mutableStateOf(false) }
    var captureAttempts by remember { mutableStateOf(0) }
    var captureSuccessCount by remember { mutableStateOf(0) }
    var captureFailureCount by remember { mutableStateOf(0) }
    var peakMemoryMb by remember { mutableStateOf(currentMemoryUsageMb()) }

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

    LaunchedEffect(pendingCapturePath) {
        val path = pendingCapturePath
        if (path.isNullOrEmpty()) {
            pendingPreview = null
            return@LaunchedEffect
        }
        pendingPreview = decodePreviewBitmap(path, 1600)
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
                        val saveStart = System.currentTimeMillis()
                        val saved = savePendingToVault(context, path)
                        savingPending = false
                        peakMemoryMb = updatePeakMemoryMb(peakMemoryMb)
                        Log.i(
                            CAMERA_DIAG_TAG,
                            "event=save_result ok=$saved elapsed_ms=${System.currentTimeMillis() - saveStart} peak_mem_mb=$peakMemoryMb",
                        )
                        if (saved) {
                            message = "已保存到保险箱"
                            pendingCapturePath = null
                            pendingPreview = null
                            delay(220)
                            onBack()
                        } else {
                            message = "保存失败（存储错误），请重试"
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
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraActionChip(
                enabled = hasCameraPermission && !isRecording,
                onClick = {
                    captureMode = if (captureMode == CameraCaptureMode.PHOTO) {
                        CameraCaptureMode.VIDEO
                    } else {
                        CameraCaptureMode.PHOTO
                    }
                },
                label = if (captureMode == CameraCaptureMode.PHOTO) {
                    stringResource(R.string.camera_control_mode_photo)
                } else {
                    stringResource(R.string.camera_control_mode_video)
                },
            )
            CameraActionChip(
                enabled = hasCameraPermission,
                onClick = {
                    timerSeconds = when (timerSeconds) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                },
                label = if (timerSeconds == 0) {
                    stringResource(R.string.camera_control_timer_off)
                } else {
                    stringResource(R.string.camera_control_timer_seconds, timerSeconds)
                },
            )
            CameraActionChip(
                enabled = hasCameraPermission && hasFlashUnit,
                onClick = {
                    flashMode = when (flashMode) {
                        FlashUiMode.OFF -> FlashUiMode.ON
                        FlashUiMode.ON -> FlashUiMode.AUTO
                        FlashUiMode.AUTO -> FlashUiMode.OFF
                    }
                },
                label = when (flashMode) {
                    FlashUiMode.OFF -> stringResource(R.string.camera_control_flash_off)
                    FlashUiMode.ON -> stringResource(R.string.camera_control_flash_on)
                    FlashUiMode.AUTO -> stringResource(R.string.camera_control_flash_auto)
                },
            )
            CameraActionChip(
                enabled = hasCameraPermission,
                onClick = { showGrid = !showGrid },
                label = if (showGrid) {
                    stringResource(R.string.camera_control_grid_on)
                } else {
                    stringResource(R.string.camera_control_grid_off)
                },
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
                label = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    stringResource(R.string.camera_control_lens_rear)
                } else {
                    stringResource(R.string.camera_control_lens_front)
                },
            )
            Text(
                text = if (isRecording) "REC ${formatRecordingDuration(recordingDurationMs)}" else "${formatZoom(zoomRatio)}x",
                color = if (isRecording) Color(0xFFFF6B6B) else Color(0xFFEAF1FF),
                fontSize = UiTextSize.homeNavLabel,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        if (hasCameraPermission) {
            ZoomRail(
                minZoom = minZoomRatio,
                maxZoom = maxZoomRatio,
                zoomRatio = zoomRatio,
                onZoomRatioChanged = { zoomRatio = it.coerceIn(minZoomRatio, maxZoomRatio) },
                onSelectPreset = { preset -> zoomRatio = preset.coerceIn(minZoomRatio, maxZoomRatio) },
            )
        }

        if (exposureRange.first != exposureRange.last) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.camera_control_exposure_value,
                        if (exposureIndex > 0) "+$exposureIndex" else exposureIndex.toString(),
                    ),
                    color = UiColors.Home.subtitle,
                    fontSize = 11.sp,
                )
                Slider(
                    value = exposureIndex.toFloat(),
                    onValueChange = { exposureIndex = it.roundToInt().coerceIn(exposureRange.first, exposureRange.last) },
                    valueRange = exposureRange.first.toFloat()..exposureRange.last.toFloat(),
                    steps = (exposureRange.last - exposureRange.first - 1).coerceAtLeast(0),
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp),
                )
                if (exposureIndex != 0) {
                    Text(
                        text = stringResource(R.string.camera_control_exposure_auto),
                        color = UiColors.Home.navItemActive,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { exposureIndex = 0 },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShutterButton(
                enabled = hasCameraPermission &&
                    !capturing &&
                    if (captureMode == CameraCaptureMode.PHOTO) imageCapture != null else videoCapture != null,
                recording = isRecording,
                onClick = {
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
                            val outputFile = VaultStore.reserveCameraTarget(
                                context = context,
                                albumName = DEFAULT_ALBUM_NAME,
                                extension = "mp4",
                            )
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
                                            message = "视频已保存到保险箱"
                                            Log.i(CAMERA_DIAG_TAG, "event=video_record_success duration_ms=$recordingDurationMs")
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
                            pendingCapturePath = result.path
                            Log.i(
                                CAMERA_DIAG_TAG,
                                "event=capture_success elapsed_ms=$elapsed success=$captureSuccessCount fail=$captureFailureCount peak_mem_mb=$peakMemoryMb",
                            )
                        } else {
                            captureFailureCount += 1
                            val failRate = (captureFailureCount * 100f / captureAttempts).roundToInt()
                            val code = result.errorCode ?: CaptureErrorCode.UNKNOWN
                            message = captureErrorMessage(code)
                            Log.e(
                                CAMERA_DIAG_TAG,
                                "event=capture_failed code=$code elapsed_ms=$elapsed fail_rate_pct=$failRate success=$captureSuccessCount fail=$captureFailureCount",
                            )
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
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
    val shotResult = withTimeoutOrNull(10_000) {
        suspendImageCapture(context, imageCapture, outputOptions)
    } ?: return@withContext CaptureTempResult(errorCode = CaptureErrorCode.TIMEOUT)
    if (shotResult.success) {
        CaptureTempResult(path = tempFile.absolutePath)
    } else {
        CaptureTempResult(errorCode = shotResult.errorCode ?: CaptureErrorCode.UNKNOWN)
    }
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
): CaptureShotResult = suspendCancellableCoroutine { continuation ->
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (continuation.isActive) continuation.resume(CaptureShotResult(success = true))
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
private fun CameraActionChip(
    enabled: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    val background = if (enabled) Color(0xB3223144) else Color(0x66223144)
    Row(
        modifier = Modifier
            .background(background, RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x33EAF1FF), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = Color(0xFFEAF1FF),
            fontSize = UiTextSize.homeNavLabel,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ZoomRail(
    minZoom: Float,
    maxZoom: Float,
    zoomRatio: Float,
    onZoomRatioChanged: (Float) -> Unit,
    onSelectPreset: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
    ) {
        Slider(
            value = zoomRatio.coerceIn(minZoom, maxZoom),
            onValueChange = onZoomRatioChanged,
            valueRange = minZoom..maxZoom,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZoomPresetText(text = "0.6x", enabled = minZoom <= 0.6f, selected = kotlin.math.abs(zoomRatio - 0.6f) < 0.08f) {
                onSelectPreset(0.6f)
            }
            ZoomPresetText(text = "1x", enabled = true, selected = kotlin.math.abs(zoomRatio - 1f) < 0.08f) {
                onSelectPreset(1f)
            }
            ZoomPresetText(text = "2x", enabled = maxZoom >= 2f, selected = kotlin.math.abs(zoomRatio - 2f) < 0.08f) {
                onSelectPreset(2f)
            }
        }
    }
}

@Composable
private fun ZoomPresetText(
    text: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (!enabled) Color(0x66EAF1FF) else if (selected) Color(0xFFEAF1FF) else Color(0x99EAF1FF),
        fontSize = UiTextSize.homeNavLabel,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
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

private const val CAMERA_DIAG_TAG = "PrivateCameraDiag"
