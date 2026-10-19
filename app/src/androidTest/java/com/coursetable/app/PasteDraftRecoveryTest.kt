package com.coursetable.app

import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.importer.PasteDraftFile
import com.coursetable.app.ui.PasteDraftRepository
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PasteDraftRecoveryTest {
    @Test fun rapidEditsAndClearReachPrivateFileAndNewReaders(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PasteDraftRepository.get(context)
        val original = repository.load()
        val file = File(context.noBackupFilesDir, "paste-import-draft.txt")
        suspend fun awaitSaved(expected: String) = withTimeout(10_000) {
            while (runCatching { PasteDraftFile(file).read() }.getOrNull() != expected) delay(20)
        }
        try {
            repeat(20) { repository.save("草稿 $it") }
            val large = "课程数据\n".repeat(40_000)
            repository.save(large)
            awaitSaved(large)
            assertEquals(large, repository.load())
            assertEquals(large, PasteDraftFile(file).read())
            repository.save("")
            awaitSaved("")
            assertEquals("", repository.load())
        } finally {
            repository.save(original)
            awaitSaved(original)
        }
    }
}
