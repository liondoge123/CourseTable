package com.coursetable.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.*
import com.coursetable.app.importer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

class ImportIndexRegressionTest {
    @Test fun newTimetableSkipsSourceRowsAndPreservesMetadataAndIsolation(): Unit = runBlocking {
        for (explicitMetadata in listOf(false, true)) {
            val reads = AtomicInteger()
            val db = database(reads)
            try {
                val source = Timetable(id = 1, name = "原课表", totalWeeks = 24, periodDurationMinutes = 50)
                db.timetableDao().upsert(source)
                val dao = db.courseDao()
                val original = Course(timetableId = 1, name = "数学", teacher = "张三", dayOfWeek = 1,
                    startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
                dao.upsert(original)
                val reviewed = dao.byTimetableOnce(1)
                dao.upsert(reviewed.single().copy(location = "原课表已更新"))
                val sourceSnapshot = dao.byTimetableOnce(1)
                val metadata = if (explicitMetadata) source.copy(id = 99, name = "导入元数据", totalWeeks = 30) else null
                val candidate = CandidateCourse("数学", teacher = "张三", dayOfWeek = 1, startSection = 1,
                    duration = 2, startWeek = 1, endWeek = 18, weekType = 0)
                reads.set(0)
                val result = db.commitImport(ImportRequest(ImportMode.NEW, listOf(candidate), reviewed, metadata), AppSettings(timetableId = 1))
                assertEquals(0, reads.get())
                assertEquals(1, result.courses)
                assertEquals(1, result.sessions)
                assertEquals((metadata ?: source).copy(id = result.timetableId), db.timetableDao().byId(result.timetableId))
                assertEquals(sourceSnapshot, dao.byTimetableOnce(1))
                val added = dao.byTimetableOnce(result.timetableId).single()
                assertNotEquals(sourceSnapshot.single().courseId, added.courseId)
                assertEquals("", added.location)
            } finally { db.close() }
        }
    }
    private fun database(reads: AtomicInteger) = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
        .setQueryCallback({ sql, _ ->
            val query = sql.lowercase(java.util.Locale.ROOT)
            if (query.contains("join course_sessions s") && query.contains("where c.timetableid")) reads.incrementAndGet()
        }, Executor { it.run() }).build()

    private fun input(size: Int, settings: AppSettings) = (0 until size).map { i ->
        val week = i / (7 * settings.periods.size) + 1
        CandidateCourse("课程$i", teacher = "教师${i % 30}", dayOfWeek = i % 7 + 1,
            startSection = i / 7 % settings.periods.size + 1, duration = 1, startWeek = week, endWeek = week, weekType = 0)
    }

    @Test fun failedAppendAndOverwriteRestoreTheSnapshotAndDoNotLeakIndexState(): Unit = runBlocking {
        val db = database(AtomicInteger())
        try {
            db.timetableDao().upsert(Timetable(id = 1, name = "回滚验证"))
            val dao = db.courseDao()
            dao.upsert(Course(timetableId = 1, name = "original", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18))
            val snapshot = dao.byTimetableOnce(1)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_index_import BEFORE INSERT ON courses WHEN NEW.name = 'reject' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            val good = CandidateCourse("new", dayOfWeek = 2, startSection = 1, duration = 2, startWeek = 1, endWeek = 18, weekType = 0)
            for (mode in listOf(ImportMode.APPEND, ImportMode.OVERWRITE)) {
                try {
                    db.commitImport(ImportRequest(mode, listOf(good, good.copy(name = "reject", dayOfWeek = 3)), snapshot), AppSettings(timetableId = 1))
                    fail("failed batch must roll back")
                } catch (_: android.database.sqlite.SQLiteException) {
                    assertEquals(snapshot, dao.byTimetableOnce(1))
                }
            }
            val result = db.commitImport(ImportRequest(ImportMode.APPEND, listOf(good), snapshot), AppSettings(timetableId = 1))
            assertEquals(1, result.sessions)
            assertEquals(0, result.skipped)
            assertEquals(setOf("original", "new"), dao.byTimetableOnce(1).map { it.name }.toSet())
        } finally { db.close() }
    }

    @Test fun bulkCommitMatchesPreviousResultsWithOneFullRead(): Unit = runBlocking {
        val settings = AppSettings(timetableId = 1, totalWeeks = 30)
        val candidates = input(400, settings)
        val results = mutableListOf<Pair<ImportSummary, List<Course>>>()
        for (reference in listOf(true, false)) {
            val reads = AtomicInteger()
            val db = database(reads)
            try {
                db.timetableDao().upsert(Timetable(id = 1, name = "批量测试", totalWeeks = 30))
                reads.set(0)
                val started = System.nanoTime()
                val request = ImportRequest(ImportMode.APPEND, candidates, emptyList())
                val summary = if (reference) db.commitImportReference(request, settings) else db.commitImport(request, settings)
                val millis = (System.nanoTime() - started) / 1_000_000
                val count = reads.get()
                assertEquals(if (reference) candidates.size + 1 else 1, count)
                assertEquals(candidates.size, summary.sessions)
                results += summary to db.courseDao().byTimetableOnce(1)
                android.util.Log.i("ImportIndexBenchmark", "{\"reference\":$reference,\"records\":${candidates.size},\"fullReads\":$count,\"milliseconds\":$millis}")
            } finally { db.close() }
        }
        assertEquals(results[0], results[1])
    }

    @Test fun twoThousandRecordsStillReadTheTimetableOnlyOnce(): Unit = runBlocking {
        val reads = AtomicInteger()
        val db = database(reads)
        try {
            val settings = AppSettings(timetableId = 1, totalWeeks = 30)
            db.timetableDao().upsert(Timetable(id = 1, name = "大批量测试", totalWeeks = 30))
            reads.set(0)
            val started = System.nanoTime()
            val result = db.commitImport(ImportRequest(ImportMode.APPEND, input(2000, settings), emptyList()), settings)
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(1, reads.get())
            assertEquals(2000, result.courses)
            assertEquals(2000, result.sessions)
            assertEquals(2000, db.courseDao().byTimetableOnce(1).size)
            android.util.Log.i("ImportIndexBenchmark", "{\"reference\":false,\"records\":2000,\"fullReads\":1,\"milliseconds\":$millis}")
        } finally { db.close() }
    }

    @Test fun updatedSharedTeacherIsVisibleToTheNextImportDecision(): Unit = runBlocking {
        for (duplicateUpdate in listOf(false, true)) {
            val db = database(AtomicInteger())
            try {
                db.timetableDao().upsert(Timetable(id = 1, name = "共享字段"))
                val dao = db.courseDao()
                val original = Course(timetableId = 1, name = "数学", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
                dao.upsert(original)
                val first = CandidateCourse("数学", teacher = "张三", dayOfWeek = if (duplicateUpdate) 1 else 2,
                    startSection = 1, duration = 2, startWeek = 1, endWeek = 18, weekType = 0,
                    included = true, updateDuplicate = duplicateUpdate)
                val second = first.copy(teacher = "李四", dayOfWeek = 3, updateDuplicate = false)
                val result = db.commitImport(ImportRequest(ImportMode.APPEND, listOf(first, second), dao.byTimetableOnce(1)), AppSettings(timetableId = 1))
                assertEquals(1, result.courses)
                val rows = dao.byTimetableOnce(1)
                assertEquals("张三", rows.single { it.dayOfWeek == 1 }.teacher)
                assertEquals("李四", rows.single { it.dayOfWeek == 3 }.teacher)
                assertNotEquals(rows.first().courseId, rows.single { it.dayOfWeek == 3 }.courseId)
            } finally { db.close() }
        }
    }

    @Test fun canonicalParentNamesExplicitGroupsAndUpdatesMatchThePreviousTransaction(): Unit = runBlocking {
        val results = mutableListOf<Pair<ImportSummary, List<Course>>>()
        for (reference in listOf(true, false)) {
            val db = database(AtomicInteger())
            try {
                db.timetableDao().upsert(Timetable(id = 1, name = "分组回归"))
                val dao = db.courseDao()
                dao.upsert(Course(timetableId = 1, name = "ＡＩ课程", dayOfWeek = 7, startSection = 1, duration = 1, startWeek = 1, endWeek = 18))
                val first = CandidateCourse("ＡＩ课程", teacher = "张三", location = "A301", dayOfWeek = 1, startSection = 1,
                    duration = 2, startWeek = 1, endWeek = 18, weekType = 0, groupKey = "one", included = true)
                val rows = listOf(first,
                    first.copy(name = "另一个名称", dayOfWeek = 2, location = "B202"),
                    first.copy(dayOfWeek = 2),
                    first.copy(groupKey = "two"),
                    first.copy(groupKey = null, dayOfWeek = 3, teacher = "李四"),
                    first.copy(groupKey = null, dayOfWeek = 7, duration = 1, teacher = "王五", targetCourseId = 1, updateDuplicate = true),
                    first.copy(groupKey = null, dayOfWeek = 4, teacher = "王五"))
                val request = ImportRequest(ImportMode.APPEND, rows, dao.byTimetableOnce(1))
                val summary = if (reference) db.commitImportReference(request, AppSettings(timetableId = 1)) else db.commitImport(request, AppSettings(timetableId = 1))
                results += summary to dao.byTimetableOnce(1)
            } finally { db.close() }
        }
        assertEquals(results[0], results[1])
        assertEquals(1, results[1].first.updated)
        assertEquals(1, results[1].first.skipped)
    }
}
