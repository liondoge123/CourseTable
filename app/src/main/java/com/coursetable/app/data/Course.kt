package com.coursetable.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

enum class WeekType(val code: Int, val label: String) {
    ALL(0, "每周"),
    ODD(1, "单周"),
    EVEN(2, "双周");

    companion object {
        fun from(code: Int): WeekType = entries.firstOrNull { it.code == code } ?: ALL
    }
}

@Entity(
    tableName = "courses",
    indices = [Index("timetableId")]
)
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 所属课表 id */
    val timetableId: Long = 0,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    /** 1=周一 ... 7=周日 */
    val dayOfWeek: Int,
    /** 起始节次（从 1 开始） */
    val startSection: Int,
    /** 连续节数 */
    val duration: Int,
    val startWeek: Int,
    val endWeek: Int,
    /** 对应 [WeekType.code] */
    val weekType: Int = WeekType.ALL.code,
    /** ARGB 颜色（Long） */
    val color: Long = 0xFF4B6EAF
) {
    fun weekTypeEnum(): WeekType = WeekType.from(weekType)

    fun visibleOnWeek(week: Int): Boolean {
        if (week < startWeek || week > endWeek) return false
        return when (weekTypeEnum()) {
            WeekType.ALL -> true
            WeekType.ODD -> week % 2 == 1
            WeekType.EVEN -> week % 2 == 0
        }
    }

    /** 最后一次实际上课的周（单双周按实际节次回退） */
    fun lastWeek(): Int {
        val last = when (weekTypeEnum()) {
            WeekType.ODD -> if (endWeek % 2 == 1) endWeek else endWeek - 1
            WeekType.EVEN -> if (endWeek % 2 == 0) endWeek else endWeek - 1
            WeekType.ALL -> endWeek
        }
        return last.coerceAtLeast(startWeek)
    }
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startSection, name")
    fun observeAll(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE timetableId = :timetableId ORDER BY dayOfWeek, startSection, name")
    fun observeByTimetable(timetableId: Long): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE timetableId = :timetableId")
    suspend fun byTimetableOnce(timetableId: Long): List<Course>

    @Query("SELECT * FROM courses")
    suspend fun allOnce(): List<Course>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun byId(id: Long): Course?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(course: Course): Long

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM courses")
    suspend fun deleteAll()

    @Query("DELETE FROM courses WHERE timetableId = :timetableId")
    suspend fun deleteByTimetable(timetableId: Long)

    @Query("UPDATE courses SET timetableId = :timetableId WHERE timetableId = 0")
    suspend fun assignTimetable(timetableId: Long)

    @Query("DELETE FROM timetables WHERE id = :id")
    suspend fun deleteTimetable(id: Long)
}

@Database(entities = [Course::class, Timetable::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2：新增 timetables 表，courses 增加 timetableId 列 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `timetables` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `semesterStartEpochDay` INTEGER NOT NULL,
                        `totalWeeks` INTEGER NOT NULL,
                        `periodsCsv` TEXT NOT NULL,
                        `periodDurationMinutes` INTEGER NOT NULL
                    )"""
                )
                db.execSQL("ALTER TABLE `courses` ADD COLUMN `timetableId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_courses_timetableId` ON `courses` (`timetableId`)")
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "coursetable.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}

class CourseRepository(
    private val dao: CourseDao,
    private val activeTimetableId: Flow<Long>
) {
    /** 当前激活课表的课程 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<Course>> = activeTimetableId.flatMapLatest { id ->
        if (id > 0) dao.observeByTimetable(id) else flowOf(emptyList())
    }

    suspend fun allOnce(): List<Course> = dao.allOnce()
    suspend fun byTimetableOnce(timetableId: Long): List<Course> = dao.byTimetableOnce(timetableId)

    /** 保存课程；timetableId 为 0 时归入当前激活课表 */
    suspend fun save(course: Course): Long = dao.upsert(course)
    suspend fun delete(id: Long) = dao.deleteById(id)
    suspend fun clear(timetableId: Long) = dao.deleteByTimetable(timetableId)
}
