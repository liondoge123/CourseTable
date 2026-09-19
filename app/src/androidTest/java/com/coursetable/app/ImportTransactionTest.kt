package com.coursetable.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.AppDatabase
import com.coursetable.app.data.Course
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ImportTransactionTest {
    @Test fun appendAndOverwriteRollbackEntireBatchOnInsertFailure() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.courseDao()
            fun course(name: String) = Course(timetableId = 1, name = name, dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
            dao.upsert(course("original"))
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_import BEFORE INSERT ON courses WHEN NEW.name = 'reject' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            for (overwrite in listOf(false, true)) {
                try {
                    dao.importCourses(1, listOf(course("new"), course("reject")), overwrite)
                    fail("Expected insertion failure")
                } catch (e: android.database.sqlite.SQLiteException) {
                    assertEquals(listOf("original"), dao.byTimetableOnce(1).map { it.name })
                }
            }
            dao.importCourses(1, listOf(course("new")), false)
            assertEquals(setOf("original", "new"), dao.byTimetableOnce(1).map { it.name }.toSet())
            dao.importCourses(1, listOf(course("replacement")), true)
            assertEquals(listOf("replacement"), dao.byTimetableOnce(1).map { it.name })
        } finally { db.close() }
    }
}
