package com.sovereignai.tbdetect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.MappedByteBuffer
import kotlin.math.max
import kotlin.math.min

class ImageClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var gradCamInterpreter: Interpreter? = null
    private val TAG = "ImageClassifier"

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0.0f, 255.0f))
        .build()

    init {
        try {
            val options = Interpreter.Options().apply { setNumThreads(4) }

            // Load main classifier
            val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, "model.tflite")
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "Classifier model loaded")

            // Load Grad-CAM model (optional - won't crash if missing)
            try {
                val gradCamBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, "gradcam_model.tflite")
                gradCamInterpreter = Interpreter(gradCamBuffer, options)
                Log.d(TAG, "Grad-CAM model loaded")
            } catch (e: Exception) {
                Log.w(TAG, "Grad-CAM model not found, heatmap disabled: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    fun classify(bitmap: Bitmap): ClassificationResult {
        val currentInterpreter = interpreter
            ?: return ClassificationResult(0f, "Error: Model not loaded", null)

        return try {
            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val outputBuffer = Array(1) { FloatArray(1) }
            currentInterpreter.run(tensorImage.buffer, outputBuffer)

            val tbProbability = outputBuffer[0][0]
            val status = if (tbProbability > 0.5f) "At Risk" else "Healthy"

            // Generate heatmap only if TB detected and gradcam model is available
            val heatmap = if (tbProbability > 0.5f && gradCamInterpreter != null) {
                generateHeatmap(bitmap)
            } else null

            ClassificationResult(tbProbability, status, heatmap)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            ClassificationResult(0f, "Error: Inference failed", null)
        }
    }

    private fun generateHeatmap(bitmap: Bitmap): Bitmap? {
        val gcInterpreter = gradCamInterpreter ?: return null

        return try {
            // Prepare input
            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            // gradcam_model has single output: feature maps [1, H, W, C]
            val featureShape = gcInterpreter.getOutputTensor(0).shape()
            Log.d(TAG, "Grad-CAM output shape: ${featureShape.contentToString()}")
            val fH = featureShape[1]
            val fW = featureShape[2]
            val fC = featureShape[3]

            val featureMaps = Array(1) { Array(fH) { Array(fW) { FloatArray(fC) } } }
            gcInterpreter.run(tensorImage.buffer, featureMaps)

            // Grad-CAM: average feature maps across channels (simplified CAM)
            // For each spatial position, sum across all channels
            val cam = Array(fH) { FloatArray(fW) }
            for (h in 0 until fH) {
                for (w in 0 until fW) {
                    var sum = 0f
                    for (c in 0 until fC) {
                        sum += max(0f, featureMaps[0][h][w][c]) // ReLU
                    }
                    cam[h][w] = sum
                }
            }

            // Normalize CAM to [0, 1]
            var camMin = Float.MAX_VALUE
            var camMax = Float.MIN_VALUE
            for (h in 0 until fH) for (w in 0 until fW) {
                camMin = min(camMin, cam[h][w])
                camMax = max(camMax, cam[h][w])
            }
            val camRange = camMax - camMin + 1e-7f
            for (h in 0 until fH) for (w in 0 until fW) {
                cam[h][w] = (cam[h][w] - camMin) / camRange
            }

            // Upscale CAM to original image size using bilinear interpolation
            val origW = bitmap.width
            val origH = bitmap.height
            val heatmapBitmap = Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888)

            for (y in 0 until origH) {
                for (x in 0 until origW) {
                    // Map pixel to CAM coordinates
                    val camX = x.toFloat() * (fW - 1) / (origW - 1)
                    val camY = y.toFloat() * (fH - 1) / (origH - 1)

                    val x0 = camX.toInt().coerceIn(0, fW - 1)
                    val x1 = (x0 + 1).coerceIn(0, fW - 1)
                    val y0 = camY.toInt().coerceIn(0, fH - 1)
                    val y1 = (y0 + 1).coerceIn(0, fH - 1)

                    val dx = camX - x0
                    val dy = camY - y0

                    // Bilinear interpolation
                    val value = cam[y0][x0] * (1 - dx) * (1 - dy) +
                                cam[y0][x1] * dx * (1 - dy) +
                                cam[y1][x0] * (1 - dx) * dy +
                                cam[y1][x1] * dx * dy

                    heatmapBitmap.setPixel(x, y, valueToHeatmapColor(value))
                }
            }

            // Blend heatmap over original image
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            val paint = Paint().apply { alpha = 160 } // 63% opacity overlay
            canvas.drawBitmap(heatmapBitmap, 0f, 0f, paint)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Grad-CAM error", e)
            null
        }
    }

    /**
     * Maps a value [0,1] to a heatmap color:
     * 0.0 = blue (cool, low activation)
     * 0.5 = green/yellow
     * 1.0 = red (hot, high activation = TB region)
     */
    private fun valueToHeatmapColor(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val r: Int
        val g: Int
        val b: Int
        when {
            v < 0.25f -> { // blue → cyan
                val t = v / 0.25f
                r = 0; g = (255 * t).toInt(); b = 255
            }
            v < 0.5f -> { // cyan → green
                val t = (v - 0.25f) / 0.25f
                r = 0; g = 255; b = (255 * (1 - t)).toInt()
            }
            v < 0.75f -> { // green → yellow
                val t = (v - 0.5f) / 0.25f
                r = (255 * t).toInt(); g = 255; b = 0
            }
            else -> { // yellow → red
                val t = (v - 0.75f) / 0.25f
                r = 255; g = (255 * (1 - t)).toInt(); b = 0
            }
        }
        return Color.argb(255, r, g, b)
    }

    fun close() {
        interpreter?.close()
        gradCamInterpreter?.close()
    }
}

data class ClassificationResult(
    val tbProbability: Float,
    val status: String,
    val heatmapBitmap: Bitmap?  // null if healthy or gradcam model not available
)
