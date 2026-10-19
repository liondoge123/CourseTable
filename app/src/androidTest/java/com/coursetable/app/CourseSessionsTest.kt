package com.coursetable.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.*
import com.coursetable.app.importer.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CourseSessionsTest {
    @Test fun deletingSessionsChecksOnlyTheirParentAndRollsBackOnFailure() = runBlocking {
        val queries = java.util.Collections.synchronizedList(mutableListOf<String>())
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries.add(sql.lowercase(java.util.Locale.ROOT)) }, java.util.concurrent.Executor { it.run() }).build()
        try {
            val dao = db.courseDao()
            val first = dao.upsert(sample())
            val course = dao.byId(first)!!
            val second = dao.upsert(course.copy(id = 0, dayOfWeek = 3))
            val unrelated = dao.upsert(sample("英语").copy(timetableId = 2))
            queries.clear()
            dao.deleteById(first)
            assertNotNull(dao.courseRecord(course.courseId))
            assertNotNull(dao.byId(second))
            assertTrue(queries.any { it.contains("not exists (select 1 from course_sessions where courseid") })
            assertFalse(queries.any { it.contains("join course_sessions") && !it.contains("where") })

            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_empty_course_delete BEFORE DELETE ON courses BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try {
                dao.deleteById(second)
                fail("session removal must roll back when parent deletion fails")
            } catch (_: android.database.sqlite.SQLiteException) {
                assertNotNull(dao.byId(second))
                assertNotNull(dao.courseRecord(course.courseId))
            }
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_empty_course_delete")
            dao.deleteById(second)
            assertNull(dao.byId(second))
            assertNull(dao.courseRecord(course.courseId))
            dao.deleteById(second) // Repeated deletion is a no-op.
            assertNotNull(dao.byId(unrelated))
            assertEquals(1, dao.allOnce().size)
        } finally { db.close() }
    }
    private fun sample(name: String = "数学") = Course(timetableId = 1, name = name, teacher = "张三", location = "A301", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18)
    @Test fun sharedMetadataSessionDeletionAndAutomaticImportGroupingAreAtomic() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val dao = db.courseDao()
            val first = dao.upsert(sample()); val course = dao.byId(first)!!
            val second = dao.upsert(course.copy(id = 0, dayOfWeek = 3, location = "B301"))
            dao.upsert(course.copy(name = "高等数学", color = 123))
            assertEquals(setOf("高等数学"), dao.allOnce().map { it.name }.toSet())
            assertEquals(setOf("A301", "B301"), dao.allOnce().map { it.location }.toSet())
            assertTrue(dao.allOnce().all { it.color == 123L })
            val candidate = CandidateCourse("高等数学", teacher = "张三", location = "C301", dayOfWeek = 5, startSection = 1, duration = 2, startWeek = 1, endWeek = 18, weekType = 0)
            val result = db.commitImport(ImportRequest(ImportMode.APPEND,listOf(candidate),dao.allOnce()),AppSettings(timetableId=1))
            assertEquals(0,result.courses); assertEquals(1,result.sessions)
            assertEquals(3, dao.allOnce().size); assertEquals(1, dao.allOnce().map { it.courseId }.distinct().size)
            dao.deleteById(second); assertEquals(2, dao.allOnce().size)
            dao.deleteCourse(course.courseId); assertTrue(dao.allOnce().isEmpty())
        } finally { db.close() }
    }
    @Test fun importAppendDedupAndStaleSnapshotRejection() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            db.timetableDao().upsert(Timetable(id = 1, name = "秋季"))
            val dao = db.courseDao(); dao.upsert(sample())
            val settings = AppSettings(timetableId = 1)
            val candidate = CandidateCourse("数学", teacher = "张三", dayOfWeek = 3, startSection = 1, duration = 2, startWeek = 1, endWeek = 18, weekType = 0)
            val first = db.commitImport(ImportRequest(ImportMode.APPEND, listOf(candidate), dao.allOnce()), settings)
            assertEquals(0, first.courses); assertEquals(1, first.sessions)
            val second = db.commitImport(ImportRequest(ImportMode.APPEND, listOf(candidate), dao.allOnce()), settings)
            assertEquals(1, second.skipped); assertEquals(2, dao.allOnce().size)
            val stale = dao.allOnce(); dao.upsert(sample("英语"))
            try { db.commitImport(ImportRequest(ImportMode.OVERWRITE, listOf(candidate), stale), settings); fail("stale request must fail") }
            catch (_: ImportChangedException) { assertEquals(3, dao.allOnce().size) }
        } finally { db.close() }
    }
    @Test fun newImportGroupsDifferentDaysAndSkipsRepeatedSessionsByDefault() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val first = CandidateCourse("通信原理",teacher="郑召文,朱轶",location="A301",dayOfWeek=2,startSection=1,duration=2,startWeek=1,endWeek=16,weekType=0)
            val second = first.copy(dayOfWeek=5,startSection=3,teacher="朱轶、郑召文",location="B301")
            val result = db.commitImport(ImportRequest(ImportMode.NEW,listOf(first,second,first),emptyList(),Timetable(name="秋季")),AppSettings())
            assertEquals(1,result.courses); assertEquals(2,result.sessions); assertEquals(1,result.skipped)
            val sessions = db.courseDao().allOnce()
            assertEquals(1,sessions.map { it.courseId }.distinct().size)
            assertEquals(setOf("A301","B301"),sessions.map { it.location }.toSet())
        } finally { db.close() }
    }
    @Test fun appendUsesTheUniqueCompatibleCourseAndPreservesItsColor() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val dao=db.courseDao()
            val existingId=dao.upsert(sample().copy(color=123))
            val target=dao.byId(existingId)!!
            dao.upsert(sample().copy(teacher="李四",color=456))
            val incoming=CandidateCourse("数学",teacher="张三",dayOfWeek=3,startSection=1,duration=2,startWeek=1,endWeek=18,weekType=0)
            val result=db.commitImport(ImportRequest(ImportMode.APPEND,listOf(incoming),dao.allOnce()),AppSettings(timetableId=1))
            assertEquals(0,result.courses)
            val added=dao.allOnce().single { it.dayOfWeek==3 }
            assertEquals(target.courseId,added.courseId); assertEquals(123L,added.color)
            assertEquals(456L,dao.allOnce().single { it.teacher=="李四" }.color)
        } finally { db.close() }
    }
    @Test fun legacyBackupUsesDefaultGroupingButExplicitBackupRelationsArePreserved() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        try {
            val legacy = BackupManager.parse("""{"app":"CourseTable","version":3,"timetables":[{"name":"旧课表","semesterStart":"2026-09-01","courses":[{"name":"数学","teacher":"张三","dayOfWeek":1,"startSection":1,"duration":2,"startWeek":1,"endWeek":18},{"name":"数学","teacher":"张三","dayOfWeek":3,"startSection":1,"duration":2,"startWeek":1,"endWeek":18}]}]}""")
            val draft=com.coursetable.app.ui.ImportPreview.Backup(legacy).imports.single()
            assertTrue(draft.candidates.all { it.groupKey==null })
            val grouped=db.commitImport(ImportRequest(ImportMode.NEW,draft.candidates,emptyList(),draft.timetable),AppSettings())
            assertEquals(1,grouped.courses); assertEquals(2,grouped.sessions)
            val separate=BackupManager.parse(BackupManager.export(listOf(TimetableBackup(Timetable(name="独立课程"),listOf(sample().copy(courseId=10),sample().copy(courseId=20,dayOfWeek=3))))))
            val preserved=com.coursetable.app.ui.ImportPreview.Backup(separate).imports.single()
            assertEquals(2,preserved.candidates.map { it.groupKey }.distinct().size)
            val restored=db.commitImport(ImportRequest(ImportMode.NEW,preserved.candidates,emptyList(),preserved.timetable),AppSettings())
            assertEquals(2,restored.courses)
        } finally { db.close() }
    }
    @Test fun failedNewTimetableImportRollsBackItsParentAndSessions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_session BEFORE INSERT ON course_sessions WHEN NEW.dayOfWeek = 3 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            val candidate = CandidateCourse("数学", dayOfWeek = 1, startSection = 1, duration = 2, startWeek = 1, endWeek = 18, weekType = 0)
            try { db.commitImport(ImportRequest(ImportMode.NEW, listOf(candidate, candidate.copy(dayOfWeek = 3)), emptyList(), Timetable(name = "新课表")), AppSettings()); fail("insertion must fail") }
            catch (_: android.database.sqlite.SQLiteException) {
                assertTrue(db.timetableDao().allOnce().isEmpty()); assertTrue(db.courseDao().allOnce().isEmpty())
                assertEquals(0, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM courses").use { it.moveToFirst(); it.getInt(0) })
            }
        } finally { db.close() }
    }
    @Test fun version4MigrationPreservesEveryIndependentRecordAndId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sessions-migration-${System.nanoTime()}.db"
        try {
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
                old.execSQL("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timetableId INTEGER NOT NULL, name TEXT NOT NULL, teacher TEXT NOT NULL, location TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, startSection INTEGER NOT NULL, duration INTEGER NOT NULL, startWeek INTEGER NOT NULL, endWeek INTEGER NOT NULL, weekType INTEGER NOT NULL, color INTEGER NOT NULL, selectedWeeksCsv TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE INDEX index_courses_timetableId ON courses(timetableId)")
                old.execSQL("CREATE TABLE timetables (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, semesterStartEpochDay INTEGER NOT NULL, totalWeeks INTEGER NOT NULL, periodsCsv TEXT NOT NULL, periodDurationMinutes INTEGER NOT NULL, periodSchemeId TEXT DEFAULT NULL)")
                old.execSQL("INSERT INTO courses VALUES(7, 1, '数学', '张三', 'A301', 1, 1, 2, 1, 18, 0, 123, '1,4,8')")
                old.execSQL("INSERT INTO courses VALUES(9, 1, '数学', '张三', 'B301', 3, 3, 2, 1, 18, 1, 456, '')")
                old.version = 4
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_4_5).build()
            try { runBlocking {
                val records = db.courseDao().allOnce().sortedBy { it.id }
                assertEquals(listOf(7L, 9L), records.map { it.id }); assertEquals(listOf(7L, 9L), records.map { it.courseId })
                assertEquals(listOf(123L, 456L), records.map { it.color }); assertEquals(listOf(1, 4, 8), records.first().scheduledWeeks())
            } } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
