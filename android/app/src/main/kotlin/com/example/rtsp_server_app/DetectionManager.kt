package com.example.rtsp_server_app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class DetectionManager(private val context: Context) {
    companion object {
        private const val TAG = "DetectionManager"
        private const val MOTION_THRESHOLD = 0.02 // 2% pixel change
        private const val AI_CONFIDENCE_THRESHOLD = 0.4f
        private const val DETECTION_INTERVAL = 3 // Process every 3rd frame extracted
        private const val MAX_FRAME_DIMENSION = 640
    }

    private var previousGrayFrame: Mat? = null
    private var frameCounter = 0
    private var tfliteInterpreter: Interpreter? = null
    private val labels: List<String>
    private var isAiModelLoaded = false
    private var lastDetectionTime = 0L
    private val detectionCooldown = 1500L // 1.5 seconds

    private var currentAiState = AiState()

    data class AiState(
        var peopleAlarm: Int = 0,
        var vehicleAlarm: Int = 0,
        var dogCatAlarm: Int = 0,
        var faceAlarm: Int = 0,
        var hasMotion: Boolean = false
    )

    init {
        // Initialize OpenCV
        try {
            if (!OpenCVLoader.initDebug()) {
                Log.e(TAG, "OpenCV initialization failed")
            } else {
                Log.d(TAG, "OpenCV initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV init error", e)
        }

        labels = loadLabels()
        loadModel()
        testFrameProcessing()
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open("coco_labels.txt").bufferedReader().readLines()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels", e)
            listOf("person", "car", "dog", "cat", "bicycle", "motorcycle", "bus", "truck")
        }
    }

    private fun loadModel() {
        try {
            val assetList = context.assets.list("")
            if (assetList?.contains("yolov8n_int8.tflite") != true) {
                Log.e(TAG, "Model file not found in assets!")
                return
            }

            val modelBuffer = context.assets.openFd("yolov8n_int8.tflite")
            val model = FileInputStream(modelBuffer.fileDescriptor).use { inputStream ->
                inputStream.channel.map(
                    java.nio.channels.FileChannel.MapMode.READ_ONLY,
                    modelBuffer.startOffset,
                    modelBuffer.length
                )
            }

            tfliteInterpreter = Interpreter(model, Interpreter.Options().apply {
                setNumThreads(2)
            })

            isAiModelLoaded = true
            Log.d(TAG, "TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TFLite model", e)
            isAiModelLoaded = false
        }
    }

    fun testFrameProcessing() {
        try {
            val testBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(testBitmap)
            canvas.drawColor(android.graphics.Color.RED)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(320f, 240f, 100f, paint)

            val result = processBitmapFrame(testBitmap)
            Log.d(TAG, "Test result: motion=${result.hasMotion}")
            testBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Test failed", e)
        }
    }

    /**
     * Bitmap Frame များကို တိုက်ရိုက်လက်ခံပြီး high performance ဖြင့် စစ်ဆေးပေးမည့် ချက်မကြီးလုပ်ငန်းစဉ်
     */
    fun processBitmapFrame(bitmap: Bitmap?): AiState {
        if (bitmap == null) return currentAiState

        frameCounter++
        if (frameCounter % DETECTION_INTERVAL != 0) {
            return currentAiState
        }

        // Resize bitmap size ကို လိုအပ်မှသာ လုပ်ဆောင်ရန်
        val resizedBitmap = if (bitmap.width > MAX_FRAME_DIMENSION || bitmap.height > MAX_FRAME_DIMENSION) {
            val scaleFactor = min(MAX_FRAME_DIMENSION.toFloat() / bitmap.width, MAX_FRAME_DIMENSION.toFloat() / bitmap.height)
            val newWidth = (bitmap.width * scaleFactor).toInt()
            val newHeight = (bitmap.height * scaleFactor).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        try {
            // 1. Motion Detection အတွက် OpenCV Native Mat သို့ အမြန်ဆုံးနည်းလမ်းဖြင့် ပြောင်းလဲခြင်း
            val currentFrameMat = Mat()
            Utils.bitmapToMat(resizedBitmap, currentFrameMat)

            val hasMotion = detectMotion(currentFrameMat)
            currentFrameMat.release() // Useပြီးသား Mat ကို ချက်ချင်း memory ရှင်းလင်းရေးလုပ်ရန်

            if (hasMotion) {
                Log.d(TAG, "🎯 Motion detected!")
            }

            // 2. AI Object Detection (Motion ရှိပြီး Cooldown ကာလ ကျော်လွန်မှသာ စစ်ဆေးမည်)
            val currentTime = System.currentTimeMillis()
            if (hasMotion && isAiModelLoaded && (currentTime - lastDetectionTime > detectionCooldown)) {
                lastDetectionTime = currentTime
                try {
                    // Bitmap ကို တိုက်ရိုက် ပို့ပေးလိုက်ခြင်းဖြင့် Mat conversion ထပ်လုပ်စရာ မလိုတော့ပါ
                    val aiResult = detectObjects(resizedBitmap)
                    currentAiState = aiResult
                    currentAiState.hasMotion = true
                    Log.d(TAG, "All AI Result: ${aiResult}")
                    Log.d(TAG, "🤖 AI Result: people=${aiResult.peopleAlarm}, vehicle=${aiResult.vehicleAlarm}")
                } catch (e: Exception) {
                    Log.e(TAG, "AI detection failed", e)
                }
            } else if (hasMotion) {
                currentAiState.hasMotion = true
            } else {
                currentAiState.hasMotion = false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error", e)
        } finally {
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
        }

        return currentAiState
    }

    private fun detectMotion(currentFrame: Mat): Boolean {
        try {
            val currentGray = Mat()
            // Utils.bitmapToMat က RGBA ပေးတာကြောင့် COLOR_RGBA2GRAY ပြောင်းပေးရပါမယ်
            Imgproc.cvtColor(currentFrame, currentGray, Imgproc.COLOR_RGBA2GRAY)

            if (previousGrayFrame == null) {
                previousGrayFrame = currentGray.clone()
                currentGray.release()
                return false
            }

            if (previousGrayFrame!!.size() != currentGray.size()) {
                val resizedPrev = Mat()
                Imgproc.resize(previousGrayFrame, resizedPrev, currentGray.size())
                previousGrayFrame?.release()
                previousGrayFrame = resizedPrev
            }

            val diff = Mat()
            Core.absdiff(previousGrayFrame, currentGray, diff)

            val threshold = Mat()
            Imgproc.threshold(diff, threshold, 25.0, 255.0, Imgproc.THRESH_BINARY)

            val motionPixels = Core.countNonZero(threshold)
            val totalPixels = threshold.total()

            previousGrayFrame?.release()
            previousGrayFrame = currentGray.clone()

            diff.release()
            threshold.release()
            currentGray.release()

            return motionPixels > (totalPixels * MOTION_THRESHOLD)

        } catch (e: Exception) {
            Log.e(TAG, "Motion detection error", e)
            return false
        }
    }

    private fun detectObjects(bitmap: Bitmap): AiState {
        val result = AiState()
        try {
            val inputWidth = 320
            val inputHeight = 320

            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
                .build()

            // Bitmap မှ တိုက်ရိုက် TensorImage တည်ဆောက်ခြင်း
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processedImage = imageProcessor.process(tensorImage)

            val output = Array(1) { Array(84) { FloatArray(2100) } }
            val inputBuffer = convertBitmapToByteBuffer(processedImage, inputWidth, inputHeight)

            if (tfliteInterpreter != null) {
                tfliteInterpreter?.run(inputBuffer, output)

                val detectedObjects = parseYOLOOutput(output[0], bitmap.width, bitmap.height)

                for (obj in detectedObjects) {
                    if (obj.confidence > AI_CONFIDENCE_THRESHOLD) {
                        when (obj.label.lowercase()) {
                            "person" -> result.peopleAlarm = 1
                            "car", "motorcycle", "bus", "truck" -> result.vehicleAlarm = 1
                            "dog", "cat" -> result.dogCatAlarm = 1
                        }
                    }
                }
                Log.d(TAG, "🔍 Detected objects: ${detectedObjects.map { "${it.label}(${it.confidence})" }}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI inference failed", e)
        }
        return result
    }

    private fun convertBitmapToByteBuffer(tensorImage: TensorImage, width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * 3 * height * width * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = tensorImage.tensorBuffer.floatArray

        for (i in 0 until height * width) {
            val r = pixels[i * 3] / 255.0f
            val g = pixels[i * 3 + 1] / 255.0f
            val b = pixels[i * 3 + 2] / 255.0f
            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        buffer.rewind()
        return buffer
    }

    private fun parseYOLOOutput(output: Array<FloatArray>, imageWidth: Int, imageHeight: Int): List<DetectedObject> {
        val results = mutableListOf<DetectedObject>()
        val confThreshold = AI_CONFIDENCE_THRESHOLD

        val numElements = 84  // Rows (0~3: Bounding box, 4~83: Class Scores)
        val numBoxes = 2100   // Columns (Candidate Boxes စုစုပေါင်း)

        // Columns တွေကို Loop ပတ်ပြီး Box တစ်ခုချင်းစီကို စစ်ဆေးခြင်း
        for (i in 0 until numBoxes) {
            var maxScore = 0f
            var maxClass = -1

            // Index 4 မှ 83 အထိရှိသော Class scores များထဲမှ အမှတ်အများဆုံး class ကို ရှာဖွေခြင်း
            for (j in 4 until numElements) {
                val score = output[j][i]
                if (score > maxScore) {
                    maxScore = score
                    maxClass = j - 4
                }
            }

            if (maxScore > confThreshold && maxClass >= 0 && maxClass < labels.size) {
                // YOLOv8 output coordinate များသည် Model Input (320px) အပေါ် အခြေခံထားသဖြင့် 320f ဖြင့် စားပြီးမှ မူလပုံရိပ် size သို့ scale လုပ်ပေးရပါသည်
                val xCenter = output[0][i] / 320f
                val yCenter = output[1][i] / 320f
                val wBox = output[2][i] / 320f
                val hBox = output[3][i] / 320f

                val x1 = (xCenter - wBox / 2f) * imageWidth
                val y1 = (yCenter - hBox / 2f) * imageHeight
                val x2 = (xCenter + wBox / 2f) * imageWidth
                val y2 = (yCenter + hBox / 2f) * imageHeight

                results.add(DetectedObject(
                    label = labels[maxClass],
                    confidence = maxScore,
                    x1 = max(0f, x1),
                    y1 = max(0f, y1),
                    x2 = min(imageWidth.toFloat(), x2),
                    y2 = min(imageHeight.toFloat(), y2)
                ))
            }
        }

        return nonMaxSuppression(results, 0.4f)
    }

    private fun nonMaxSuppression(boxes: List<DetectedObject>, iouThreshold: Float): List<DetectedObject> {
        if (boxes.isEmpty()) return boxes

        val sorted = boxes.sortedByDescending { it.confidence }
        val selected = mutableListOf<DetectedObject>()
        val suppressed = BooleanArray(sorted.size) { false }

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            selected.add(sorted[i])

            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                val iou = calculateIOU(sorted[i], sorted[j])
                if (iou > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return selected
    }

    private fun calculateIOU(box1: DetectedObject, box2: DetectedObject): Float {
        val x1 = max(box1.x1, box2.x1)
        val y1 = max(box1.y1, box2.y1)
        val x2 = min(box1.x2, box2.x2)
        val y2 = min(box1.y2, box2.y2)

        if (x2 < x1 || y2 < y1) return 0f

        val intersection = (x2 - x1) * (y2 - y1)
        val area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val union = area1 + area2 - intersection

        return if (union > 0) intersection / union else 0f
    }

    data class DetectedObject(
        val label: String,
        val confidence: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    )

    fun getMotionState(): Int = if (currentAiState.hasMotion) 1 else 0
    fun getPeopleState(): Int = currentAiState.peopleAlarm
    fun getVehicleState(): Int = currentAiState.vehicleAlarm
    fun getDogCatState(): Int = currentAiState.dogCatAlarm
    fun getFaceState(): Int = currentAiState.faceAlarm

    fun cleanup() {
        try {
            previousGrayFrame?.release()
            tfliteInterpreter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }
}