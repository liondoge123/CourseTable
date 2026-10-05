package com.coursetable.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CourseWeeksMigrationTest {
    @Test fun migratesLegacyCourseAndPersistsIrregularSelection() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "course-weeks-migration-${System.nanoTime()}.db"
            val file = context.getDatabasePath(name)
            file.parentFile!!.mkdirs()
            try {
                SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                    old.execSQL("CREATE TABLE IF NOT EXISTS `courses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timetableId` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, `location` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, `startSection` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `startWeek` INTEGER NOT NULL, `endWeek` INTEGER NOT NULL, `weekType` INTEGER NOT NULL, `color` INTEGER NOT NULL)")
                    old.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_timetableId` ON `courses` (`timetableId`)")
                    old.execSQL("CREATE TABLE IF NOT EXISTS `timetables` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `semesterStartEpochDay` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL, `periodsCsv` TEXT NOT NULL, `periodDurationMinutes` INTEGER NOT NULL)")
                    old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'fe103c3290539659906b7cc6dead3ed4')")
                    old.execSQL("INSERT INTO courses VALUES (1, 0, '数学', '', '', 1, 1, 1, 2, 8, 1, 4283133615)")
                    old.version = 2
                }
                val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(AppDatabase.MIGRATION_2_3).build()
                try {
                    val legacy = database.courseDao().byId(1)!!
                    assertEquals("", legacy.selectedWeeksCsv)
                    assertEquals(listOf(3, 5, 7), legacy.scheduledWeeks())
                    database.courseDao().upsert(legacy.withScheduledWeeks(listOf(1, 4, 8)))
                    assertEquals(listOf(1, 4, 8), database.courseDao().byId(1)!!.scheduledWeeks())
                } finally { database.close() }
            } finally { context.deleteDatabase(name) }
        }
    }
}
