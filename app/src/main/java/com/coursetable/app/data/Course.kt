package com.coursetable.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Transaction
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

/** A timetable occurrence. id identifies the session, courseId its shared course. */
data class Course(
    val id: Long = 0,
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
    val color: Long = 0xFF0A84FF,
    /** Explicit non-contiguous weeks; blank uses startWeek/endWeek/weekType. */
    val selectedWeeksCsv: String = "",
    val courseId: Long = 0
) {
    /** null edits only this session; a list atomically replaces the course's sessions. */
    @androidx.room.Ignore var additionalSessions: List<Course>? = null
    @androidx.room.Ignore var reviewedSnapshot: List<Course>? = null
    fun weekTypeEnum(): WeekType = WeekType.from(weekType)

    fun visibleOnWeek(week: Int): Boolean {
        if (selectedWeeksCsv.isNotBlank()) return week in scheduledWeeks()
        if (week < startWeek || week > endWeek) return false
        return when (weekTypeEnum()) {
            WeekType.ALL -> true
            WeekType.ODD -> week % 2 == 1
            WeekType.EVEN -> week % 2 == 0
        }
    }

    /** 最后一次实际上课的周（单双周按实际节次回退） */
    fun lastWeek(): Int {
        if (selectedWeeksCsv.isNotBlank()) return scheduledWeeks().lastOrNull() ?: startWeek
        val last = when (weekTypeEnum()) {
            WeekType.ODD -> if (endWeek % 2 == 1) endWeek else endWeek - 1
            WeekType.EVEN -> if (endWeek % 2 == 0) endWeek else endWeek - 1
            WeekType.ALL -> endWeek
        }
        return last.coerceAtLeast(startWeek)
    }
}

@Entity(tableName = "courses", indices = [Index("timetableId")])
data class CourseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long,
    val name: String,
    val teacher: String = "",
    val color: Long = 0xFF0A84FF
)

@Entity(tableName = "course_sessions", indices = [Index("courseId")], foreignKeys = [
    androidx.room.ForeignKey(entity = CourseRecord::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = androidx.room.ForeignKey.CASCADE)
])
data class CourseSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val location: String = "",
    val dayOfWeek: Int,
    val startSection: Int,
    val duration: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "''") val selectedWeeksCsv: String = ""
)

data class CourseWithSessions(
    @androidx.room.Embedded val course: CourseRecord,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "courseId") val sessions: List<CourseSession>
)

private const val OCCURRENCES = "SELECT s.id, c.id AS courseId, c.timetableId, c.name, c.teacher, c.color, s.location, s.dayOfWeek, s.startSection, s.duration, s.startWeek, s.endWeek, s.weekType, s.selectedWeeksCsv FROM courses c JOIN course_sessions s ON s.courseId = c.id"

@Dao
interface CourseDao {
    @Query(OCCURRENCES + " ORDER BY dayOfWeek, startSection, name")
    fun observeAll(): Flow<List<Course>>

    @Query(OCCURRENCES + " WHERE c.timetableId = :timetableId ORDER BY dayOfWeek, startSection, name")
    fun observeByTimetable(timetableId: Long): Flow<List<Course>>

    @Query(OCCURRENCES + " WHERE c.timetableId = :timetableId ORDER BY c.id, s.id")
    suspend fun byTimetableOnce(timetableId: Long): List<Course>

    @Query(OCCURRENCES)
    suspend fun allOnce(): List<Course>

    @Query(OCCURRENCES + " WHERE s.id = :id")
    suspend fun byId(id: Long): Course?

    @Insert
    suspend fun insertCourse(course: CourseRecord): Long

    @Insert
    suspend fun insertTimetable(timetable: Timetable): Long

    @Transaction
    suspend fun restoreTimetables(items: List<Pair<Timetable, List<Course>>>): List<Long> = items.map { (table, sessions) ->
        val tableId = insertTimetable(table.copy(id = 0))
        val parents = mutableMapOf<Long, Long>()
        sessions.forEachIndexed { index, s ->
            val key = s.courseId.takeIf { it > 0 } ?: -(index + 1L)
            val parent = parents.getOrPut(key) { 0 }
            val savedId = upsert(s.copy(id = 0, courseId = parent, timetableId = tableId))
            parents[key] = byId(savedId)!!.courseId
        }
        tableId
    }

    @androidx.room.Update
    suspend fun updateCourse(course: CourseRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CourseSession): Long

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun courseRecord(id: Long): CourseRecord?

    @Transaction
    suspend fun upsert(course: Course): Long {
        course.reviewedSnapshot?.let { snapshot ->
            require(byTimetableOnce(course.timetableId).sortedBy { it.id } == snapshot.sortedBy { it.id }) { "课表数据已变化，请重新检查重复和冲突" }
        }
        val original = if (course.id > 0) byId(course.id) else null
        val requestedId = course.courseId.takeIf { it > 0 } ?: original?.courseId ?: 0
        val parent = requestedId.takeIf { courseRecord(it) != null } ?: insertCourse(CourseRecord(timetableId = course.timetableId, name = course.name, teacher = course.teacher, color = course.color))
        require(courseRecord(parent)?.timetableId == course.timetableId) { "课程不属于目标课表" }
        updateCourse(CourseRecord(parent, course.timetableId, course.name, course.teacher, course.color))
        val id = insertSession(CourseSession(course.id, parent, course.location, course.dayOfWeek, course.startSection, course.duration, course.startWeek, course.endWeek, course.weekType, course.selectedWeeksCsv))
        course.additionalSessions?.let { others ->
            val ids = mutableListOf(id)
            for (s in others) {
                require(s.id == 0L || byId(s.id)?.courseId == parent) { "时间段不属于此课程" }
                ids += insertSession(CourseSession(s.id, parent, s.location, s.dayOfWeek, s.startSection, s.duration, s.startWeek, s.endWeek, s.weekType, s.selectedWeeksCsv))
            }
            removeOtherSessions(parent, ids)
        }
        return id
    }

    @Query("DELETE FROM course_sessions WHERE courseId = :courseId AND id NOT IN (:ids)")
    suspend fun removeOtherSessions(courseId: Long, ids: List<Long>)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourse(courseId: Long)

    @Query("DELETE FROM course_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM courses WHERE id = :courseId AND NOT EXISTS (SELECT 1 FROM course_sessions WHERE courseId = :courseId)")
    suspend fun deleteCourseIfEmpty(courseId: Long)

    @Transaction
    suspend fun deleteById(id: Long) {
        val parent = byId(id)?.courseId ?: return
        deleteSession(id)
        deleteCourseIfEmpty(parent)
    }

    @Transaction
    suspend fun importCourses(timetableId: Long, courses: List<Course>, overwrite: Boolean) {
        require(courses.all { it.timetableId == timetableId })
        if (overwrite) deleteByTimetable(timetableId)
        courses.forEach { upsert(it) }
    }

    @Query("DELETE FROM courses")
    suspend fun deleteAll()

    @Query("DELETE FROM courses WHERE timetableId = :timetableId")
    suspend fun deleteByTimetable(timetableId: Long)

    @Query("UPDATE courses SET timetableId = :timetableId WHERE timetableId = 0")
    suspend fun assignTimetable(timetableId: Long)

    @Query("DELETE FROM timetables WHERE id = :id")
    suspend fun deleteTimetable(id: Long)
}

@Database(entities = [CourseRecord::class, CourseSession::class, Timetable::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE courses_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timetableId INTEGER NOT NULL, name TEXT NOT NULL, teacher TEXT NOT NULL, color INTEGER NOT NULL)")
                db.execSQL("INSERT INTO courses_new SELECT id, timetableId, name, teacher, color FROM courses")
                db.execSQL("CREATE TABLE sessions_copy (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER NOT NULL, location TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, startSection INTEGER NOT NULL, duration INTEGER NOT NULL, startWeek INTEGER NOT NULL, endWeek INTEGER NOT NULL, weekType INTEGER NOT NULL, selectedWeeksCsv TEXT NOT NULL DEFAULT '')")
                db.execSQL("INSERT INTO sessions_copy SELECT id, id, location, dayOfWeek, startSection, duration, startWeek, endWeek, weekType, selectedWeeksCsv FROM courses")
                db.execSQL("DROP TABLE courses")
                db.execSQL("ALTER TABLE courses_new RENAME TO courses")
                db.execSQL("CREATE INDEX index_courses_timetableId ON courses(timetableId)")
                db.execSQL("CREATE TABLE course_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, courseId INTEGER NOT NULL, location TEXT NOT NULL, dayOfWeek INTEGER NOT NULL, startSection INTEGER NOT NULL, duration INTEGER NOT NULL, startWeek INTEGER NOT NULL, endWeek INTEGER NOT NULL, weekType INTEGER NOT NULL, selectedWeeksCsv TEXT NOT NULL DEFAULT '', FOREIGN KEY(courseId) REFERENCES courses(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO course_sessions SELECT * FROM sessions_copy")
                db.execSQL("DROP TABLE sessions_copy")
                db.execSQL("CREATE INDEX index_course_sessions_courseId ON course_sessions(courseId)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `timetables` ADD COLUMN `periodSchemeId` TEXT DEFAULT NULL")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `courses` ADD COLUMN `selectedWeeksCsv` TEXT NOT NULL DEFAULT ''")
            }
        }

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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
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
    suspend fun deleteCourse(courseId: Long) = dao.deleteCourse(courseId)
    suspend fun clear(timetableId: Long) = dao.deleteByTimetable(timetableId)
    suspend fun importCourses(timetableId: Long, courses: List<Course>, overwrite: Boolean) =
        dao.importCourses(timetableId, courses, overwrite)
}
