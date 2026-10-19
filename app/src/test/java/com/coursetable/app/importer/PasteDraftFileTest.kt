package com.coursetable.app.importer

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PasteDraftFileTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun survivesNewStoreAndClearingAndIgnoresInterruptedTemporaryWrite() {
        val file = File(temporary.root, "draft.txt")
        val store = PasteDraftFile(file)
        assertEquals("", store.read())
        val text = "高等数学Ⅱ\n".repeat(30_000)
        store.write(text)
        File(temporary.root, "draft.txt.tmp").writeText("interrupted")
        assertEquals(text, PasteDraftFile(file).read())
        store.write("")
        assertEquals("", PasteDraftFile(file).read())
        assertFalse(File(temporary.root, "draft.txt.tmp").exists())
    }

    @Test fun rejectsOversizedWriteWithoutReplacingSavedDraft() {
        val store = PasteDraftFile(File(temporary.root, "draft.txt"))
        store.write("original")
        assertThrows(IllegalArgumentException::class.java) { store.write("x".repeat(PasteTimetableImporter.MAX_CHARACTERS + 1)) }
        assertEquals("original", store.read())
    }
}
