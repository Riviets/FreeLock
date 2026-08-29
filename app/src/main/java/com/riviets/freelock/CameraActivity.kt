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

// Клас для передачі даних про скелет в інтерфейс
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
    var currentPoseState by remember { mutableStateOf<PoseState?>(null) } // Зберігаємо стан скелета

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

            // Камера та Аналізатор
            CameraPreview(
                onPushup = {
                    pushupCount++
                    val secondsEarned = (settings.getUnlockTime() * 60).toLong()
                    settings.addEarnedTime(secondsEarned)
                },
                onPoseDetected = { poseState ->
                    currentPoseState = poseState // Оновлюємо скелет
                }
            )

            // Малюємо скелет поверх відео
            currentPoseState?.let { poseState ->
                DrawSkeleton(poseState)
            }

            // Інтерфейс лічильника
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Віджимань: $pushupCount",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.Green // Зробив зеленим, щоб краще було видно на тлі камери
                )
                Text(
                    text = "Зароблено: ${pushupCount * (settings.getUnlockTime() * 60).toInt()} сек",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            Button(
                onClick = onExit,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).fillMaxWidth()
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

// Компонент малювання ліній по кістках
@Composable
fun DrawSkeleton(poseState: PoseState) {
    val pose = poseState.pose

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Функції для переведення координат ML Kit у координати екрана смартфона
        fun scaleX(x: Float): Float {
            val scaled = x * canvasWidth / poseState.imageWidth
            return canvasWidth - scaled // Дзеркалимо по осі X для фронтальної камери
        }

        fun scaleY(y: Float): Float {
            return y * canvasHeight / poseState.imageHeight
        }

        fun drawBone(start: PoseLandmark?, end: PoseLandmark?, color: Color = Color.Cyan) {
            // Малюємо лінію тільки якщо AI впевнений у цих точках більше ніж на 50%
            if (start != null && end != null && start.inFrameLikelihood > 0.5f && end.inFrameLikelihood > 0.5f) {
                drawLine(
                    color = color,
                    start = Offset(scaleX(start.position.x), scaleY(start.position.y)),
                    end = Offset(scaleX(end.position.x), scaleY(end.position.y)),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
            }
        }

        // Отримуємо всі потрібні точки
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        // Малюємо руки
        drawBone(leftShoulder, leftElbow, Color.Red) // Ліве плече -> лікоть
        drawBone(leftElbow, leftWrist, Color.Red)    // Лівий лікоть -> зап'ястя

        drawBone(rightShoulder, rightElbow, Color.Blue) // Праве плече -> лікоть
        drawBone(rightElbow, rightWrist, Color.Blue)    // Правий лікоть -> зап'ястя

        // Малюємо тулуб
        drawBone(leftShoulder, rightShoulder, Color.Green) // Між плечима
        drawBone(leftShoulder, leftHip, Color.Green)       // Лівий бік
        drawBone(rightShoulder, rightHip, Color.Green)     // Правий бік
        drawBone(leftHip, rightHip, Color.Green)           // Між стегнами
    }
}

@Composable
fun CameraPreview(onPushup: () -> Unit, onPoseDetected: (PoseState) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

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
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
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

            // Визначаємо правильні розміри зображення для масштабування (враховуючи поворот камери)
            val isPortrait = imageProxy.imageInfo.rotationDegrees == 90 || imageProxy.imageInfo.rotationDegrees == 270
            val imageWidth = if (isPortrait) imageProxy.height else imageProxy.width
            val imageHeight = if (isPortrait) imageProxy.width else imageProxy.height

            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    // Відправляємо скелет в інтерфейс для малювання
                    onPoseDetected(PoseState(pose, imageWidth, imageHeight))

                    val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                    val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
                    val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)

                    val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                    val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
                    val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

                    // Перевіряємо чи існують точки і чи камера в них впевнена (> 0.5)
                    if (leftShoulder != null && leftElbow != null && leftWrist != null &&
                        rightShoulder != null && rightElbow != null && rightWrist != null &&
                        leftShoulder.inFrameLikelihood > 0.5f && leftWrist.inFrameLikelihood > 0.5f) {

                        // ПЕРЕВІРКА АНТИ-ЧІТ: Зап'ястя мають бути фізично нижче за плечі (більше значення по осі Y)
                        // Це виключає накрутку, коли людина сидить і махає руками
                        if (leftWrist.position.y > leftShoulder.position.y && rightWrist.position.y > rightShoulder.position.y) {

                            val leftAngle = getAngle(leftShoulder, leftElbow, leftWrist)
                            val rightAngle = getAngle(rightShoulder, rightElbow, rightWrist)

                            // Нижня точка віджимання (пом'якшено до 110 через перспективу з підлоги)
                            if (leftAngle < 110 && rightAngle < 110) {
                                isDown = true
                            }
                            // Верхня точка (випрямлення рук, пом'якшено до 140)
                            else if (leftAngle > 140 && rightAngle > 140 && isDown) {
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastPushupTime > 1000) { // 1 секунда між віджиманнями мінімум
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