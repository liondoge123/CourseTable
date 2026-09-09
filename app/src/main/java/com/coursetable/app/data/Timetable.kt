package com.coursetable.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

/** 课表：每个课表拥有独立的学期设置 */
@Entity(tableName = "timetables")
data class Timetable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 学期开始日期（epochDay） */
    val semesterStartEpochDay: Long = LocalDate.of(2026, 9, 1).toEpochDay(),
    val totalWeeks: Int = 18,
    /** 节次时间 CSV："08:00-08:45;08:55-09:40;..." */
    val periodsCsv: String = "",
    val periodDurationMinutes: Int = 45
) {
    fun semesterStart(): LocalDate = LocalDate.ofEpochDay(semesterStartEpochDay)

    fun periods(): List<PeriodTime> = parsePeriods(periodsCsv)

    companion object {
        fun serializePeriods(periods: List<PeriodTime>): String =
            periods.joinToString(";") { it.label() }

        fun parsePeriods(csv: String?): List<PeriodTime> {
            if (csv.isNullOrBlank()) return AppSettings.defaultPeriods()
            val result = mutableListOf<PeriodTime>()
            for (token in csv.split(";")) {
                if (token.isBlank()) continue
                val parts = token.trim().split("-")
                if (parts.size != 2) continue
                try {
                    val s = LocalTime.parse(parts[0].trim(), PeriodTime.TIME_FMT)
                    val e = LocalTime.parse(parts[1].trim(), PeriodTime.TIME_FMT)
                    result.add(PeriodTime(s, e))
                } catch (_: Exception) {
                }
            }
            return if (result.isEmpty()) AppSettings.defaultPeriods() else result
        }
    }
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetables ORDER BY id")
    fun observeAll(): Flow<List<Timetable>>

    @Query("SELECT * FROM timetables WHERE id = :id")
    fun observeById(id: Long): Flow<Timetable?>

    @Query("SELECT * FROM timetables WHERE id = :id")
    suspend fun byId(id: Long): Timetable?

    @Query("SELECT * FROM timetables ORDER BY id")
    suspend fun allOnce(): List<Timetable>

    @Query("SELECT COUNT(*) FROM timetables")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(timetable: Timetable): Long
}
