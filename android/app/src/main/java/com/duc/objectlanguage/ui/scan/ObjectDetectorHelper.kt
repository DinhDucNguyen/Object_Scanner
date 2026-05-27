package com.duc.objectlanguage.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val modelName: String = "best_float32.tflite",
)

sealed interface DetectionEvent {
    data class Success(
        val results: List<DetectionResult>,
        val imageWidth: Int,
        val imageHeight: Int,
    ) : DetectionEvent

    data class Failure(val message: String) : DetectionEvent
}

class ObjectDetectorHelper(
    private val context: Context,
    private val threshold: Float = 0.5f,
    private val maxResults: Int = 3,
    private val modelName: String = "best_float32.tflite",
    val onResult: (DetectionEvent) -> Unit,
) {
    private var interpreter: Interpreter? = null
    private val inputSize = 640
    private val labels = labelsForModel(modelName)

    init {
        setupDetector()
    }

    private fun setupDetector() {
        runCatching {
            val afd = context.assets.openFd(modelName)
            val model = FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength,
            )
            interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
        }.onFailure {
            onResult(DetectionEvent.Failure("Không load được model: ${it.message}"))
        }
    }

    fun detect(imageProxy: ImageProxy) {
        val interp = interpreter ?: return

        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888,
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

        val rotDeg = imageProxy.imageInfo.rotationDegrees.toFloat()
        val rotated = if (rotDeg != 0f) {
            val m = Matrix().apply { postRotate(rotDeg) }
            Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, m, true)
        } else bitmapBuffer

        val resized = Bitmap.createScaledBitmap(rotated, inputSize, inputSize, true)

        // Float32 input [1, 640, 640, 3] normalized [0, 1]
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (px in pixels) {
            inputBuffer.putFloat(((px shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((px shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((px and 0xFF) / 255f)
        }
        inputBuffer.rewind()

        // YOLOv10 output: [1, 300, 6] → [x1, y1, x2, y2, score, class_id]
        val outShape = interp.getOutputTensor(0).shape()
        val numDetections = outShape[1]
        val outputBuffer = Array(1) { Array(numDetections) { FloatArray(6) } }

        runCatching {
            interp.run(inputBuffer, outputBuffer)
        }.onFailure {
            onResult(DetectionEvent.Failure("Lỗi nhận diện: ${it.message}"))
            return
        }

        val results = mutableListOf<DetectionResult>()
        for (i in 0 until numDetections) {
            val score = outputBuffer[0][i][4]
            if (score < threshold) continue

            val classId = outputBuffer[0][i][5].toInt()
            val label = labels.getOrElse(classId) { "unknown" }

            // Coordinates are absolute (0–640), normalize to (0–1)
            val x1 = (outputBuffer[0][i][0] / inputSize).coerceIn(0f, 1f)
            val y1 = (outputBuffer[0][i][1] / inputSize).coerceIn(0f, 1f)
            val x2 = (outputBuffer[0][i][2] / inputSize).coerceIn(0f, 1f)
            val y2 = (outputBuffer[0][i][3] / inputSize).coerceIn(0f, 1f)

            results.add(
                DetectionResult(
                    label = label,
                    confidence = score,
                    boundingBox = RectF(x1, y1, x2, y2),
                    modelName = modelName,
                )
            )
        }

        onResult(
            DetectionEvent.Success(
                results.sortedByDescending { it.confidence }.take(maxResults),
                rotated.width,
                rotated.height,
            )
        )
    }

    fun detectBitmap(bitmap: Bitmap): DetectionResult? {
        val interp = interpreter ?: return null

        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (px in pixels) {
            inputBuffer.putFloat(((px shr 16) and 0xFF) / 255f)
            inputBuffer.putFloat(((px shr 8) and 0xFF) / 255f)
            inputBuffer.putFloat((px and 0xFF) / 255f)
        }
        inputBuffer.rewind()

        val outShape = interp.getOutputTensor(0).shape()
        val numDetections = outShape[1]
        val outputBuffer = Array(1) { Array(numDetections) { FloatArray(6) } }

        runCatching { interp.run(inputBuffer, outputBuffer) }.onFailure { return null }

        return (0 until numDetections)
            .map { i -> Pair(outputBuffer[0][i][4], i) }
            .filter { it.first >= threshold }
            .maxByOrNull { it.first }
            ?.let { (score, i) ->
                val classId = outputBuffer[0][i][5].toInt()
                DetectionResult(
                    label = labels.getOrElse(classId) { "unknown" },
                    confidence = score,
                    modelName = modelName,
                    boundingBox = RectF(
                        (outputBuffer[0][i][0] / inputSize).coerceIn(0f, 1f),
                        (outputBuffer[0][i][1] / inputSize).coerceIn(0f, 1f),
                        (outputBuffer[0][i][2] / inputSize).coerceIn(0f, 1f),
                        (outputBuffer[0][i][3] / inputSize).coerceIn(0f, 1f),
                    ),
                )
            }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        const val CUSTOM_MODEL = "best_float32.tflite"
        const val COCO_MODEL = "yolov10n_int8.tflite"

        val SCHOOL_SUPPLIES_LABELS = listOf(
            "backpack", "calculator", "eraser", "notebook", "pen",
            "pencil", "ruler", "scissors", "sharpener", "water_bottle",
        )

        val COCO_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
            "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
            "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
            "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
            "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
            "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
            "toothbrush",
        )

        fun labelsForModel(modelName: String): List<String> =
            if (modelName == COCO_MODEL) COCO_LABELS else SCHOOL_SUPPLIES_LABELS

        fun displayNameForModel(modelName: String): String =
            if (modelName == COCO_MODEL) "COCO YOLOv10n" else "YOLOv10 Custom"
    }
}
