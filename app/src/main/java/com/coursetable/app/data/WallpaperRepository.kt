package com.coursetable.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Owns only files in its private directory. A draft is never published before settings commit. */
class WallpaperRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val directory: File = File(context.filesDir, "wallpapers")
) {
    private val mutex = Mutex()
    private val imageMutex = Mutex()
    private var initialized = false
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        mutex.withLock { initializeLocked() }
    }

    private suspend fun initializeLocked() {
        if (initialized) return
        check(directory.mkdirs() || directory.isDirectory)
        val active = settings.wallpaper.first()?.imageId
        directory.listFiles()?.filter { it.name != active && owned(it) }?.forEach { it.delete() }
        initialized = true
    }

    private fun owned(file: File) = file.parentFile?.canonicalFile == directory.canonicalFile &&
        (WallpaperConfig.validId(file.name) || file.name.matches(Regex("source-[a-f0-9-]{36}\\.tmp")))

    private fun imageFile(id: String): File {
        require(WallpaperConfig.validId(id))
        return File(directory, id)
    }

    suspend fun prepare(uri: Uri): WallpaperConfig = withContext(Dispatchers.IO) {
        mutex.withLock {
            initializeLocked()
            val source = File(directory, "source-${UUID.randomUUID()}.tmp")
            val image = imageFile("${UUID.randomUUID()}.png")
            var success = false
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    source.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 64L * 1024 * 1024) { "图片过大，请选择小于 64 MB 的图片" }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("无法读取图片，请重新选择")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别这张图片" }
                require(bounds.outWidth.toLong() * bounds.outHeight <= 200_000_000L) { "图片尺寸过大" }
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
                val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?: error("图片解码失败")
                try {
                    val orientation = runCatching { ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
                    val matrix = orientationMatrix(orientation)
                    val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    try {
                        image.outputStream().use { check(oriented.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { if (oriented !== decoded) oriented.recycle() }
                } finally { decoded.recycle() }
                coroutineContext.ensureActive()
                success = true
                WallpaperConfig(image.name)
            } finally {
                source.delete()
                if (!success) image.delete()
            }
        }
    }

    suspend fun apply(config: WallpaperConfig?) = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            initializeLocked()
            if (config != null) require(imageFile(config.imageId).isFile) { "壁纸文件不存在，请重新选择" }
            val old = settings.wallpaper.first()
            // Never delete the old image if the DataStore transaction fails.
            if (config == null) settings.clearWallpaper() else settings.saveWallpaper(config.normalized())
            if (old != null && old.imageId != config?.imageId) imageFile(old.imageId).delete()
        }
    }

    suspend fun discard(id: String) = withContext(Dispatchers.IO + NonCancellable) {
        mutex.withLock {
            if (settings.wallpaper.first()?.imageId != id) imageFile(id).delete()
        }
    }

    /** All decode/filter work happens off the render thread; cached images are never recycled in use. */
    suspend fun bitmap(config: WallpaperConfig, widthDp: Float, heightDp: Float): Bitmap? = withContext(Dispatchers.Default) {
        imageMutex.withLock {
            val file = imageFile(config.imageId)
            if (!file.isFile) return@withContext null
            val original = cache.get(config.imageId) ?: withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(file.path)?.also { cache.put(config.imageId, it) }
            } ?: return@withContext null
            if (config.blurDp <= 0f) return@withContext original
            val ratio = minOf(1f, 768f / max(original.width, original.height))
            val renderScale = max(widthDp / original.width, heightDp / original.height) * config.scale
            val radius = (config.blurDp / renderScale.coerceAtLeast(.01f) * ratio).roundToInt().coerceIn(1, 64)
            val key = "${config.imageId}:$radius"
            cache.get(key) ?: run {
                val small = Bitmap.createScaledBitmap(original, max(1, (original.width * ratio).roundToInt()),
                    max(1, (original.height * ratio).roundToInt()), true)
                val activeContext = coroutineContext
                val result = try { boxBlur(small, radius) { activeContext.ensureActive() } }
                    finally { if (small !== original) small.recycle() }
                cache.put(key, result)
                result
            }
        }
    }

    companion object {
        internal fun orientationMatrix(orientation: Int) = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }

        /** Separable two-pass box blur, including alpha, available on Android 26+. */
        internal fun boxBlur(bitmap: Bitmap, radius: Int, checkCancelled: () -> Unit = {}): Bitmap {
            val w = bitmap.width; val h = bitmap.height
            var pixels = IntArray(w * h)
            var scratch = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            // Filter premultiplied channels so transparent source pixels cannot create fringes.
            for (i in pixels.indices) {
                val p = pixels[i]; val a = p ushr 24
                var color = a shl 24
                for (channel in 0..2) {
                    val shift = channel * 8
                    color = color or (((((p ushr shift) and 255) * a + 127) / 255) shl shift)
                }
                pixels[i] = color
            }
            val r = radius.coerceIn(1, 64)
            repeat(2) {
                for (horizontal in listOf(true, false)) {
                    val length = if (horizontal) w else h
                    val lines = if (horizontal) h else w
                    fun index(line: Int, pos: Int) = if (horizontal) line * w + pos else pos * w + line
                    for (line in 0 until lines) {
                        checkCancelled()
                        val sums = LongArray(4)
                        fun add(pos: Int, sign: Int) {
                            val p = pixels[index(line, pos.coerceIn(0, length - 1))]
                            for (channel in 0..3) sums[channel] += ((p ushr (channel * 8)) and 255) * sign
                        }
                        for (i in -r..r) add(i, 1)
                        for (pos in 0 until length) {
                            var color = 0
                            for (channel in 0..3) color = color or ((sums[channel] / (2 * r + 1)).toInt() shl (channel * 8))
                            scratch[index(line, pos)] = color
                            add(pos - r, -1); add(pos + r + 1, 1)
                        }
                    }
                    val swap = pixels; pixels = scratch; scratch = swap
                }
            }
            for (i in pixels.indices) {
                val p = pixels[i]; val a = p ushr 24
                var color = a shl 24
                if (a > 0) for (channel in 0..2) {
                    val shift = channel * 8
                    color = color or (((((p ushr shift) and 255) * 255 + a / 2) / a).coerceAtMost(255) shl shift)
                }
                pixels[i] = color
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
    }
}
