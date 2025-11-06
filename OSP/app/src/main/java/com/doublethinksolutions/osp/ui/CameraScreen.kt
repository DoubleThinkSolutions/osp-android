package com.doublethinksolutions.osp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoRecordEvent.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doublethinksolutions.osp.R
import com.doublethinksolutions.osp.data.PhotoMetadata
import com.doublethinksolutions.osp.data.SerializablePhotoMetadata
import com.doublethinksolutions.osp.tasks.LocationProvider
import com.doublethinksolutions.osp.tasks.MetadataCollectionTask
import com.doublethinksolutions.osp.tasks.OrientationProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private enum class CaptureMode {
    PHOTO, VIDEO
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(uploadViewModel: UploadViewModel = viewModel()) {
    val context = LocalContext.current
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    DisposableEffect(Unit) {
        OrientationProvider.start(context.applicationContext)
        LocationProvider.start(context.applicationContext)
        onDispose {
            OrientationProvider.stop()
            LocationProvider.stop()
        }
    }

    if (permissionsState.allPermissionsGranted) {
        CameraView(context = context, viewModel = uploadViewModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This app needs access to your camera, microphone, and location to take photos, record videos, and tag them with your current position.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Grant Permissions")
            }
        }
    }
}

@SuppressLint("MissingPermission") // Permissions are checked in the parent composable
@Composable
private fun CameraView(context: Context, viewModel: UploadViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(flashMode)
            .build()
    }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        VideoCapture.withOutput(recorder)
    }

    val previewView = remember { PreviewView(context) }
    val uploadQueue by viewModel.uploadQueue.collectAsState()
    val uploadHistory by viewModel.uploadHistory.collectAsState()

    LaunchedEffect(cameraSelector, lifecycleOwner, captureMode, flashMode) {
        val cameraProvider = getCameraProvider(context)
        try {
            cameraProvider.unbindAll()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // Update flash mode on the use case
            imageCapture.flashMode = flashMode

            // Bind the correct use case based on the selected mode
            if (captureMode == CaptureMode.PHOTO) {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            } else { // VIDEO
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, videoCapture
                )
            }
        } catch (exc: Exception) {
            Log.e("CameraScreen", "Use case binding failed", exc)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Main UI Overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Upload Status and Flash Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Add some space between items
            ) {
                // The indicator takes up all available space, preventing overlap
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    UploadStatusIndicator(
                        uploadQueue = uploadQueue,
                        uploadHistory = uploadHistory
                    )
                }

                // Flash control (only show in photo mode)
                if (captureMode == CaptureMode.PHOTO) {
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        },
                        // This pins the button to the top of the Row's bounds
                        modifier = Modifier.align(Alignment.Top)
                    ) {
                        val flashIcon = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                            else -> Icons.Default.FlashAuto
                        }
                        Icon(imageVector = flashIcon, contentDescription = "Toggle Flash", tint = Color.White)
                    }
                } else {
                    // Spacer to keep layout from jumping when switching modes
                    Spacer(
                        modifier = Modifier
                            .size(48.dp) // Same size as IconButton
                            .align(Alignment.Top)
                    )
                }
            }

            // Bottom Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Mode Switch Button
                IconButton(
                    onClick = {
                        captureMode = if (captureMode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
                    },
                    enabled = !isRecording,
                    modifier = Modifier.size(48.dp)
                ) {
                    val icon = if (captureMode == CaptureMode.PHOTO) Icons.Default.Videocam else Icons.Default.PhotoCamera
                    Icon(imageVector = icon, contentDescription = "Switch Mode", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                // Capture Button
                CaptureButton(
                    mode = captureMode,
                    isRecording = isRecording,
                    onClick = {
                        coroutineScope.launch {
                            if (captureMode == CaptureMode.PHOTO) {
                                takePhoto(context, imageCapture, viewModel)
                            } else {
                                if (isRecording) {
                                    activeRecording?.stop()
                                    activeRecording = null
                                    // isRecording will be set to false in the recording listener
                                } else {
                                    activeRecording = startRecording(context, videoCapture, viewModel) { recordingStarted ->
                                        isRecording = recordingStarted
                                    }
                                }
                            }
                        }
                    }
                )

                // Camera Switch Button
                IconButton(
                    onClick = {
                        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                    },
                    enabled = !isRecording,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(
    mode: CaptureMode,
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRecording) Color.Red else Color.White,
        animationSpec = tween(200)
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(buttonColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                // Show a square for "stop"
                Box(modifier = Modifier.size(20.dp).background(Color.White))
            } else if (mode == CaptureMode.VIDEO) {
                // Show a red circle for "start recording"
                Box(modifier = Modifier.size(28.dp).background(Color.Red, CircleShape))
            }
        }
    }
}


private suspend fun getCameraProvider(context: Context): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(context).also { future ->
        future.addListener({
            continuation.resume(future.get())
        }, ContextCompat.getMainExecutor(context))
    }
}

private suspend fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    viewModel: UploadViewModel
) {
    val metadata: SerializablePhotoMetadata? = try {
        MetadataCollectionTask().collect()
    } catch (e: Exception) {
        Log.e("CameraScreen", "Metadata collection failed", e)
        null
    }

    val photoFile = File(context.cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
            Log.d("CameraScreen", "Photo saved: ${photoFile.absolutePath}")
            viewModel.startUpload(context, photoFile, metadata)
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraScreen", "Photo capture failed: $exception")
        }
    })
}

@SuppressLint("MissingPermission") // Permissions are checked before calling
private suspend fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    viewModel: UploadViewModel,
    onRecordingStarted: (Boolean) -> Unit
): Recording {
    val metadata: SerializablePhotoMetadata? = try {
        MetadataCollectionTask().collect()
    } catch (e: Exception) {
        Log.e("CameraScreen", "Metadata collection failed", e)
        null
    }

    val videoFile = File(context.cacheDir, "VID_${System.currentTimeMillis()}.mp4")
    val outputOptions = FileOutputOptions.Builder(videoFile).build()

    val recordingListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is Start -> {
                Log.d("CameraScreen", "Recording started.")
                onRecordingStarted(true)
            }
            is Finalize -> {
                if (!event.hasError()) {
                    Log.d("CameraScreen", "Video saved: ${videoFile.absolutePath}")
                    viewModel.startUpload(context, videoFile, metadata)
                } else {
                    Log.e("CameraScreen", "Video capture failed with error: ${event.error} - ${event.cause?.message}")
                    videoFile.delete() // Clean up failed file
                }
                onRecordingStarted(false)
            }
        }
    }

    // Start the recording
    return videoCapture.output
        .prepareRecording(context, outputOptions)
        .withAudioEnabled()
        .start(ContextCompat.getMainExecutor(context), recordingListener)
}
