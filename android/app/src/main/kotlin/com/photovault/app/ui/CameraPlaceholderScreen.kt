package com.photovault.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.photovault.app.ui.components.AppButton
import com.photovault.app.ui.components.AppButtonVariant
import com.photovault.app.ui.theme.UiColors
import com.photovault.app.ui.theme.UiTextSize
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
fun CameraPlaceholderScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) message = "未授予相机权限，无法拍照"
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(UiColors.Home.bgBottom)
            .safeDrawingPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Take a private Photo",
            color = UiColors.Home.title,
            fontSize = UiTextSize.homeTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(18.dp)),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PreviewView(viewContext).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            bindCameraUseCases(
                                context = viewContext,
                                lifecycleOwner = lifecycleOwner,
                                previewView = this,
                                onReady = { capture -> imageCapture = capture },
                            )
                        }
                    },
                )
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
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        AppButton(
            text = "拍照并保存到保险箱",
            onClick = {
                val capture = imageCapture ?: return@AppButton
                if (capturing) return@AppButton
                capturing = true
                scope.launch {
                    val ok = captureToVault(context, capture)
                    capturing = false
                    if (ok) onBack() else message = "拍照失败，请重试"
                }
            },
            enabled = hasCameraPermission && imageCapture != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )
        AppButton(
            text = "返回保险箱",
            onClick = onBack,
            variant = AppButtonVariant.SECONDARY,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    onReady: (ImageCapture) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener(
        {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
            onReady(imageCapture)
        },
        ContextCompat.getMainExecutor(context),
    )
}

private suspend fun captureToVault(
    context: Context,
    imageCapture: ImageCapture,
): Boolean = withContext(Dispatchers.IO) {
    val targetDir = File(context.filesDir, "vault_album")
    if (!targetDir.exists()) targetDir.mkdirs()
    val file = File(targetDir, "camera_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    suspendImageCapture(context, imageCapture, outputOptions)
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
