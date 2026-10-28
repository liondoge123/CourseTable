package com.coursetable.app

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.coursetable.app.ui.rememberWallpaperImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WallpaperImageReuseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reenteredPagesReceiveTheReadyImageOnTheirFirstComposition() = runBlocking {
        WallpaperTestEnvironment().use { environment ->
            val config = environment.repository.prepare(Uri.fromFile(environment.patternedSource()))
            val ready = AtomicReference<Bitmap?>()
            val observations = mutableListOf<Pair<Bitmap?, Boolean>>()
            var page by mutableIntStateOf(-1)
            compose.setContent {
                run {
                    val shared = rememberWallpaperImage(config, environment.repository)
                    SideEffect { ready.set(shared.bitmap) }
                    if (page >= 0) key(page) {
                        val image = rememberWallpaperImage(config, environment.repository)
                        SideEffect { observations += image.bitmap to image.loading }
                    }
                }
            }
            compose.waitUntil(15_000) { ready.get() != null }
            val expected = ready.get()
            repeat(4) { destination ->
                compose.runOnIdle { page = destination }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertTrue("The newly mounted page must have rendered", observations.isNotEmpty())
                    observations.forEach { (bitmap, loading) ->
                        assertSame("Entering a page must not replace a ready wallpaper with an empty frame", expected, bitmap)
                        assertFalse("Entering a page must not restart image loading", loading)
                    }
                    observations.clear()
                    page = -1
                }
                compose.waitForIdle()
            }
        }
    }

    @Test fun aDifferentRepositoryDoesNotReuseAnotherRepositorysImage() = runBlocking {
        WallpaperTestEnvironment().use { sharedEnvironment ->
            WallpaperTestEnvironment().use { draftEnvironment ->
                val config = sharedEnvironment.repository.prepare(Uri.fromFile(sharedEnvironment.source()))
                val ready = AtomicBoolean(false)
                val isolated = AtomicBoolean(false)
                compose.setContent {
                    run {
                        val shared = rememberWallpaperImage(config, sharedEnvironment.repository)
                        val draft = rememberWallpaperImage(config, draftEnvironment.repository)
                        SideEffect {
                            ready.set(shared.bitmap != null)
                            isolated.set(draft.bitmap == null && !draft.loading)
                        }
                    }
                }
                compose.waitUntil(15_000) { ready.get() && isolated.get() }
            }
        }
    }
}
