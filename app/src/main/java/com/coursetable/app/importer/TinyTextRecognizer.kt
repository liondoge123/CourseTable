package com.coursetable.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.ceil

/** PP-OCRv6 Tiny recognition only. ML Kit supplies boxes; no OpenCV/detection model is bundled.
 * BGR/CHW normalization and CTC decoding follow Apache-2.0 PaddleOCR/RapidOCR examples.
 */
object TinyTextRecognizer {
    data class Prediction(val text: String, val confidence: Float)
    private var context: Context? = null
    private var session: OrtSession? = null
    private var characters = emptyList<String>()
    fun initialize(context: Context) { this.context = context.applicationContext }

    @Synchronized
    fun recognize(bitmap: Bitmap): Prediction? {
        val context = context ?: return null
        val environment = OrtEnvironment.getEnvironment()
        val active = session ?: run {
            val bytes = context.assets.open("ocr/PP-OCRv6_rec_tiny.onnx").use { it.readBytes() }
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                environment.createSession(bytes, options)
            }.also {
                characters = listOf("") + it.metadata.customMetadata["character"].orEmpty().split('\n') + " "
                require(characters.size == 6906) { "OCR character dictionary does not match model" }
                session = it
            }
        }
        val height = 48
        val resizedWidth = ceil(height.toDouble() * bitmap.width / bitmap.height).toInt().coerceIn(1, 2048)
        val width = maxOf(320, resizedWidth)
        val resized = Bitmap.createScaledBitmap(bitmap, resizedWidth, height, true)
        val values = FloatArray(3 * height * width)
        try {
            val pixels = IntArray(resizedWidth * height)
            resized.getPixels(pixels, 0, resizedWidth, 0, 0, resizedWidth, height)
            for (y in 0 until height) for (x in 0 until resizedWidth) {
                val pixel = pixels[y * resizedWidth + x]; val index = y * width + x
                values[index] = Color.blue(pixel) / 127.5f - 1f
                values[height * width + index] = Color.green(pixel) / 127.5f - 1f
                values[2 * height * width + index] = Color.red(pixel) / 127.5f - 1f
            }
        } finally { if (resized !== bitmap) resized.recycle() }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), longArrayOf(1, 3, height.toLong(), width.toLong())).use { tensor ->
            active.run(mapOf(active.inputNames.first() to tensor)).use { output ->
                @Suppress("UNCHECKED_CAST")
                val steps = (output[0].value as Array<Array<FloatArray>>)[0]
                var previous = -1; var sum = 0f; var count = 0
                val result = StringBuilder()
                for (scores in steps) {
                    var best = 0
                    for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
                    if (best != 0 && best != previous) {
                        result.append(characters[best]); sum += scores[best]; count++
                    }
                    previous = best
                }
                return if (count == 0) null else Prediction(result.toString().trim(), sum / count)
            }
        }
    }

    @Synchronized
    fun release() { session?.close(); session = null; characters = emptyList() }
}
