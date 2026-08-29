package com.riviets.freelock // ПЕРЕВІР СВІЙ ПАКЕТ!

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlin.math.abs
import kotlin.math.atan2

data class PoseState(val pose: Pose, val imageWidth: Int, val imageHeight: Int)

class CameraActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraScreen { finish() }
                }
            }
        }
    }
}

@Composable
fun CameraScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsManager(context) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var pushupCount by remember { mutableIntStateOf(0) }
    var currentPoseState by remember { mutableStateOf<PoseState?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            CameraPreview(
                zoomRatio = zoomRatio,
                onPushup = {
                    pushupCount++
                    val secondsEarned = (settings.getUnlockTime() * 60).toLong()
                    settings.addEarnedTime(secondsEarned)
                },
                onPoseDetected = { poseState ->
                    currentPoseState = poseState
                }
            )

            currentPoseState?.let { poseState ->
                DrawSkeleton(poseState)
            }

            // Інтерфейс лічильника (змістив трохи вліво для горизонтального режиму)
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(32.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Віджимань: $pushupCount",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.Green
                )
                Text(
                    text = "Зароблено: ${pushupCount * (settings.getUnlockTime() * 60).toInt()} сек",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            // Блок управління зумом (праворуч)
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf(0.6f, 1f, 2f).forEach { zoom ->
                    Button(
                        onClick = { zoomRatio = zoom },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (zoomRatio == zoom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.size(64.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("${zoom}x")
                    }
                }
            }

            Button(
                onClick = onExit,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).width(300.dp)
            ) {
                Text("Завершити тренування")
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Потрібен дозвіл на камеру")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Надати") }
        }
    }
}

@Composable
fun DrawSkeleton(poseState: PoseState) {
    val pose = poseState.pose

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        fun scaleX(x: Float): Float = canvasWidth - (x * canvasWidth / poseState.imageWidth)
        fun scaleY(y: Float): Float = y * canvasHeight / poseState.imageHeight

        fun drawBone(start: PoseLandmark?, end: PoseLandmark?, color: Color) {
            // Знизили поріг малювання до 0.3, щоб скелет менше блимав
            if (start != null && end != null && start.inFrameLikelihood > 0.3f && end.inFrameLikelihood > 0.3f) {
                drawLine(
                    color = color,
                    start = Offset(scaleX(start.position.x), scaleY(start.position.y)),
                    end = Offset(scaleX(end.position.x), scaleY(end.position.y)),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
            }
        }

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        // Руки
        drawBone(leftShoulder, leftElbow, Color.Red)
        drawBone(leftElbow, leftWrist, Color.Red)
        drawBone(rightShoulder, rightElbow, Color.Blue)
        drawBone(rightElbow, rightWrist, Color.Blue)

        // Корпус
        drawBone(leftShoulder, rightShoulder, Color.Green)
        drawBone(leftShoulder, leftHip, Color.Green)
        drawBone(rightShoulder, rightHip, Color.Green)
        drawBone(leftHip, rightHip, Color.Green)

        // Ноги
        drawBone(leftHip, leftKnee, Color.Yellow)
        drawBone(leftKnee, leftAnkle, Color.Yellow)
        drawBone(rightHip, rightKnee, Color.Magenta)
        drawBone(rightKnee, rightAnkle, Color.Magenta)
    }
}

@Composable
fun CameraPreview(zoomRatio: Float, onPushup: () -> Unit, onPoseDetected: (PoseState) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraControl = remember { mutableStateOf<CameraControl?>(null) }

    LaunchedEffect(zoomRatio) {
        cameraControl.value?.setZoomRatio(zoomRatio)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor, PushupAnalyzer(onPushup, onPoseDetected))
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                    cameraControl.value = camera.cameraControl
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

class PushupAnalyzer(
    private val onPushupCompleted: () -> Unit,
    private val onPoseDetected: (PoseState) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = AccuratePoseDetectorOptions.Builder().setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE).build()
    private val poseDetector = PoseDetection.getClient(options)

    private var isDown = false
    private var lastPushupTime = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            val isPortrait = imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270
            val imageWidth = if (isPortrait) imageProxy.height else imageProxy.width
            val imageHeight = if (isPortrait) imageProxy.width else imageProxy.height

            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    onPoseDetected(PoseState(pose, imageWidth, imageHeight))

                    // Завантажуємо точки
                    val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                    val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
                    val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
                    val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
                    val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
                    val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

                    val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                    val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
                    val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
                    val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
                    val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
                    val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

                    // Рахуємо сумарну "видимість" лівої та правої сторони
                    val leftVisibility = (leftShoulder?.inFrameLikelihood ?: 0f) + (leftAnkle?.inFrameLikelihood ?: 0f)
                    val rightVisibility = (rightShoulder?.inFrameLikelihood ?: 0f) + (rightAnkle?.inFrameLikelihood ?: 0f)

                    // Обираємо ту сторону, яку краще видно камері (профіль)
                    val useLeft = leftVisibility > rightVisibility

                    val shoulder = if (useLeft) leftShoulder else rightShoulder
                    val elbow = if (useLeft) leftElbow else rightElbow
                    val wrist = if (useLeft) leftWrist else rightWrist
                    val hip = if (useLeft) leftHip else rightHip
                    val knee = if (useLeft) leftKnee else rightKnee
                    val ankle = if (useLeft) leftAnkle else rightAnkle

                    // Працюємо тільки з обраною стороною. Поріг для ніг знижено до 0.3
                    if (shoulder != null && elbow != null && wrist != null && hip != null && knee != null && ankle != null &&
                        shoulder.inFrameLikelihood > 0.5f && ankle.inFrameLikelihood > 0.3f) {

                        val torsoYDiff = abs(shoulder.position.y - hip.position.y)
                        val torsoXDiff = abs(shoulder.position.x - hip.position.x)

                        val kneeAngle = getAngle(hip, knee, ankle)
                        val armAngle = getAngle(shoulder, elbow, wrist)

                        // torsoXDiff > torsoYDiff означає, що людина лежить (розтягнута по горизонталі), а не стоїть
                        if (kneeAngle > 140 && torsoYDiff < torsoXDiff * 1.5) {

                            if (armAngle < 110) {
                                isDown = true
                            }
                            else if (armAngle > 140 && isDown) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastPushupTime > 1000) {
                                    isDown = false
                                    lastPushupTime = currentTime
                                    onPushupCompleted()
                                }
                            }
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun getAngle(firstPoint: PoseLandmark, midPoint: PoseLandmark, lastPoint: PoseLandmark): Double {
        val result = Math.toDegrees(
            atan2(lastPoint.position.y - midPoint.position.y, lastPoint.position.x - midPoint.position.x).toDouble() -
                    atan2(firstPoint.position.y - midPoint.position.y, firstPoint.position.x - midPoint.position.x).toDouble()
        )
        var angle = abs(result)
        if (angle > 180) angle = 360.0 - angle
        return angle
    }
}