package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.*
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

internal class WallpaperMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    var failWrites = false
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
        if (failWrites) throw IOException("test write failure")
        transform(state.value).also { state.value = it }
    }
}

internal class WallpaperTestEnvironment : AutoCloseable {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val root = File(context.cacheDir, "wallpaper-test-${UUID.randomUUID()}").apply { mkdirs() }
    val directory = File(root, "images")
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    val preferences = WallpaperMemoryPreferences()
    val settings = SettingsRepository(context, database.timetableDao(), preferences)
    val repository = WallpaperRepository(context, settings, directory)
    fun source(width: Int = 80, height: Int = 40, jpeg: Boolean = false): File {
        val file = File(root, "${UUID.randomUUID()}.${if (jpeg) "jpg" else "png"}")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(35, 125, 210))
        file.outputStream().use { bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
    fun patternedSource(): File {
        val file = File(root, "pattern.png")
        val bitmap = Bitmap.createBitmap(1000, 1600, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 1000f, 1600f,
            intArrayOf(0xFFFFCE88.toInt(), 0xFF559BAA.toInt(), 0xFF241947.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1000f, 1600f, paint)
        paint.shader = null
        for (row in 0..15) for (column in 0..9) {
            paint.color = if ((row + column) % 2 == 0) 0x90FFFFFF.toInt() else 0x90141E44.toInt()
            canvas.drawCircle(column * 110f, row * 110f, 38f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
    override fun close() {
        database.close()
        check(root.parentFile!!.canonicalFile == context.cacheDir.canonicalFile && root.name.startsWith("wallpaper-test-"))
        root.deleteRecursively()
    }
}

class WallpaperRepositoryTest {
    @Test fun draftIsPrivateAndOnlyApplyCommitsAcrossRestartAndTableSwitches() = runBlocking {
        WallpaperTestEnvironment().use { env ->
            val source = env.source()
            val draft = env.repository.prepare(Uri.fromFile(source))
            assertNull(env.settings.wallpaper.first())
            source.delete()
            assertNotNull(env.repository.bitmap(draft, 400f, 800f))
            env.repository.apply(draft.copy(scale = 2f, mask = .6f, gridOpacity = .25f, cardOpacity = .4f))
            env.database.timetableDao().upsert(Timetable(id = 101, name = "秋季"))
            env.database.timetableDao().upsert(Timetable(id = 102, name = "春季"))
            env.settings.setActiveTimetable(101)
            env.settings.setActiveTimetable(102)
            val restarted = SettingsRepository(env.context, env.database.timetableDao(), env.preferences)
            assertEquals(draft.copy(scale = 2f, mask = .6f, gridOpacity = .25f, cardOpacity = .4f), restarted.settings.first().wallpaper)
            env.repository.discard(draft.imageId)
            assertTrue(File(env.directory, draft.imageId).exists())
            env.repository.apply(null)
            assertNull(restarted.wallpaper.first())
            assertFalse(File(env.directory, draft.imageId).exists())
        }
    }

    @Test fun failedCommitPreservesPreviousImageAndSuccessfulReplaceCleansIt() = runBlocking {
        WallpaperTestEnvironment().use { env ->
            val first = env.repository.prepare(Uri.fromFile(env.source()))
            env.repository.apply(first)
            val next = env.repository.prepare(Uri.fromFile(env.source()))
            env.preferences.failWrites = true
            assertTrue(runCatching { env.repository.apply(next) }.isFailure)
            assertEquals(first, env.settings.wallpaper.first())
            assertTrue(File(env.directory, first.imageId).exists())
            env.preferences.failWrites = false
            env.repository.apply(next)
            assertFalse(File(env.directory, first.imageId).exists())
            assertTrue(File(env.directory, next.imageId).exists())
        }
    }

    @Test fun startupCleansOnlyOwnedOrphansAndInvalidInputLeavesNoDraft() = runBlocking {
        WallpaperTestEnvironment().use { env ->
            val first = env.repository.prepare(Uri.fromFile(env.source()))
            env.repository.apply(first)
            val orphan = env.repository.prepare(Uri.fromFile(env.source()))
            val unrelated = File(env.directory, "unrelated.txt").apply { writeText("keep") }
            WallpaperRepository(env.context, env.settings, env.directory).initialize()
            assertFalse(File(env.directory, orphan.imageId).exists())
            assertTrue(File(env.directory, first.imageId).exists())
            assertTrue(unrelated.exists())
            val bad = File(env.root, "bad.png").apply { writeText("not an image") }
            assertTrue(runCatching { env.repository.prepare(Uri.fromFile(bad)) }.isFailure)
            assertEquals(setOf(first.imageId, unrelated.name), env.directory.list()!!.toSet())
            File(env.directory, first.imageId).delete()
            assertNull(env.repository.bitmap(first, 400f, 800f))
        }
    }

    @Test fun orientationDownsamplingAlphaAndBlurAreSupported() = runBlocking {
        WallpaperTestEnvironment().use { env ->
            val jpeg = env.source(jpeg = true)
            ExifInterface(jpeg).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
            val oriented = env.repository.prepare(Uri.fromFile(jpeg)).copy(blurDp = 0f)
            val image = env.repository.bitmap(oriented, 400f, 800f)!!
            assertEquals(40, image.width); assertEquals(80, image.height)
            val long = env.repository.prepare(Uri.fromFile(env.source(64, 4096))).copy(blurDp = 0f)
            assertTrue(env.repository.bitmap(long, 400f, 800f)!!.height <= 2048)
            val transparent = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888)
            transparent.eraseColor(0x803388CC.toInt())
            val blurred = WallpaperRepository.boxBlur(transparent, 8)
            assertEquals(Color.alpha(transparent.getPixel(10, 10)), Color.alpha(blurred.getPixel(10, 10)))
            for (shift in listOf(0, 8, 16)) assertTrue(kotlin.math.abs(
                ((transparent.getPixel(10, 10) ushr shift) and 255) - ((blurred.getPixel(10, 10) ushr shift) and 255)) <= 2)
            transparent.recycle(); blurred.recycle()
            val filtered = env.repository.bitmap(oriented.copy(blurDp = 8f), 400f, 800f)
            assertSame(filtered, env.repository.bitmap(oriented.copy(blurDp = 8f, mask = .7f, positionX = 0f), 400f, 800f))
        }
    }
}
