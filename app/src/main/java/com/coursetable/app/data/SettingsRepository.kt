package com.coursetable.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class PeriodTime(val start: LocalTime, val end: LocalTime) {
    fun label(): String = "${start.format(TIME_FMT)}-${end.format(TIME_FMT)}"

    companion object {
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/**
 * 应用设置：学期相关字段来自当前激活课表（Room），
 * 外观/显示/提醒等全局字段来自 DataStore。
 */
data class AppSettings(
    val timetableId: Long = 0,
    val timetableName: String = "默认课表",
    val semesterStart: LocalDate = LocalDate.of(2026, 9, 1),
    val totalWeeks: Int = 18,
    val periods: List<PeriodTime> = defaultPeriods(),
    val periodDurationMinutes: Int = 45,
    val cardAlignLeft: Boolean = false,
    val showNonCurrentWeek: Boolean = true,
    val themeMode: String = "auto",
    val themeColor: String = "blue",
    val reminderEnabled: Boolean = false,
    val reminderMinutes: Int = 15
) {
    companion object {
        fun defaultPeriods(): List<PeriodTime> = listOf(
            PeriodTime(LocalTime.of(8, 0), LocalTime.of(8, 45)),
            PeriodTime(LocalTime.of(8, 55), LocalTime.of(9, 40)),
            PeriodTime(LocalTime.of(10, 0), LocalTime.of(10, 45)),
            PeriodTime(LocalTime.of(10, 55), LocalTime.of(11, 40)),
            PeriodTime(LocalTime.of(14, 0), LocalTime.of(14, 45)),
            PeriodTime(LocalTime.of(14, 55), LocalTime.of(15, 40)),
            PeriodTime(LocalTime.of(16, 0), LocalTime.of(16, 45)),
            PeriodTime(LocalTime.of(16, 55), LocalTime.of(17, 40)),
            PeriodTime(LocalTime.of(19, 0), LocalTime.of(19, 45)),
            PeriodTime(LocalTime.of(19, 55), LocalTime.of(20, 40)),
            PeriodTime(LocalTime.of(20, 50), LocalTime.of(21, 35)),
            PeriodTime(LocalTime.of(21, 45), LocalTime.of(22, 30))
        )
    }
}

class SettingsRepository(
    private val context: Context,
    private val timetableDao: TimetableDao
) {

    private object Keys {
        // 旧版本学期设置（迁移后仅用于播种默认课表）
        val LEGACY_SEMESTER_START = longPreferencesKey("semester_start_epoch_day")
        val LEGACY_TOTAL_WEEKS = intPreferencesKey("total_weeks")
        val LEGACY_PERIODS = stringPreferencesKey("periods_csv")
        val LEGACY_PERIOD_DURATION = intPreferencesKey("period_duration_minutes")

        val ACTIVE_TIMETABLE_ID = longPreferencesKey("active_timetable_id")
        val CARD_ALIGN_LEFT = intPreferencesKey("card_align_left")
        val SHOW_NON_CURRENT_WEEK = intPreferencesKey("show_non_current_week")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val REMINDER_ENABLED = intPreferencesKey("reminder_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    }

    /** 当前激活课表 id */
    val activeTimetableId: Flow<Long> = context.dataStore.data.map { it[Keys.ACTIVE_TIMETABLE_ID] ?: 0L }

    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: Flow<AppSettings> = context.dataStore.data.flatMapLatest { prefs ->
        val activeId = prefs[Keys.ACTIVE_TIMETABLE_ID] ?: 0L
        val timetableFlow = if (activeId > 0) timetableDao.observeById(activeId) else flowOf(null)
        timetableFlow.map { timetable -> buildSettings(prefs, timetable) }
    }

    private fun buildSettings(prefs: Preferences, timetable: Timetable?): AppSettings {
        val t = timetable ?: Timetable(id = 0, name = "默认课表")
        val periods = t.periods()
        val defaultDuration = if (t.periodsCsv.isBlank() && periods.isNotEmpty()) {
            val first = periods.first()
            val minutes = java.time.Duration.between(first.start, first.end).toMinutes().toInt()
            if (minutes > 0) minutes else 45
        } else t.periodDurationMinutes
        return AppSettings(
            timetableId = t.id,
            timetableName = t.name,
            semesterStart = t.semesterStart(),
            totalWeeks = t.totalWeeks,
            periods = periods,
            periodDurationMinutes = if (t.periodsCsv.isBlank()) defaultDuration else t.periodDurationMinutes,
            cardAlignLeft = (prefs[Keys.CARD_ALIGN_LEFT] ?: 0) == 1,
            showNonCurrentWeek = (prefs[Keys.SHOW_NON_CURRENT_WEEK] ?: 1) == 1,
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings().themeMode,
            themeColor = prefs[Keys.THEME_COLOR] ?: AppSettings().themeColor,
            reminderEnabled = (prefs[Keys.REMINDER_ENABLED] ?: 0) == 1,
            reminderMinutes = (prefs[Keys.REMINDER_MINUTES] ?: 15).coerceIn(5, 60)
        )
    }

    /**
     * 保存设置。学期相关字段写入当前激活课表（Room），其余写入 DataStore。
     */
    suspend fun save(
        semesterStart: LocalDate? = null,
        totalWeeks: Int? = null,
        periods: List<PeriodTime>? = null,
        cardAlignLeft: Boolean? = null,
        periodDurationMinutes: Int? = null,
        showNonCurrentWeek: Boolean? = null,
        themeMode: String? = null,
        themeColor: String? = null,
        reminderEnabled: Boolean? = null,
        reminderMinutes: Int? = null,
        activeTimetableId: Long? = null
    ) {
        // DataStore 部分
        context.dataStore.edit { prefs ->
            if (cardAlignLeft != null) prefs[Keys.CARD_ALIGN_LEFT] = if (cardAlignLeft) 1 else 0
            if (showNonCurrentWeek != null) prefs[Keys.SHOW_NON_CURRENT_WEEK] = if (showNonCurrentWeek) 1 else 0
            if (themeMode != null) prefs[Keys.THEME_MODE] = themeMode
            if (themeColor != null) prefs[Keys.THEME_COLOR] = themeColor
            if (reminderEnabled != null) prefs[Keys.REMINDER_ENABLED] = if (reminderEnabled) 1 else 0
            if (reminderMinutes != null) prefs[Keys.REMINDER_MINUTES] = reminderMinutes.coerceIn(5, 60)
            if (activeTimetableId != null) prefs[Keys.ACTIVE_TIMETABLE_ID] = activeTimetableId
        }
        // Room（当前课表）部分
        if (semesterStart != null || totalWeeks != null || periods != null || periodDurationMinutes != null) {
            val activeId = context.dataStore.data.first()[Keys.ACTIVE_TIMETABLE_ID] ?: 0L
            val current = timetableDao.byId(activeId) ?: return
            timetableDao.upsert(
                current.copy(
                    semesterStartEpochDay = semesterStart?.toEpochDay() ?: current.semesterStartEpochDay,
                    totalWeeks = totalWeeks ?: current.totalWeeks,
                    periodsCsv = periods?.let { Timetable.serializePeriods(it) } ?: current.periodsCsv,
                    periodDurationMinutes = periodDurationMinutes ?: current.periodDurationMinutes
                )
            )
        }
    }

    suspend fun saveAll(settings: AppSettings) = save(
        semesterStart = settings.semesterStart,
        totalWeeks = settings.totalWeeks,
        periods = settings.periods,
        cardAlignLeft = settings.cardAlignLeft,
        periodDurationMinutes = settings.periodDurationMinutes,
        showNonCurrentWeek = settings.showNonCurrentWeek,
        themeMode = settings.themeMode,
        themeColor = settings.themeColor,
        reminderEnabled = settings.reminderEnabled,
        reminderMinutes = settings.reminderMinutes
    )

    /** 读取旧版本存储在 DataStore 的学期设置（用于迁移播种），读取后清除 */
    suspend fun consumeLegacySemester(): Timetable? {
        var result: Timetable? = null
        context.dataStore.edit { prefs ->
            val hasLegacy = prefs.contains(Keys.LEGACY_SEMESTER_START) ||
                prefs.contains(Keys.LEGACY_TOTAL_WEEKS) ||
                prefs.contains(Keys.LEGACY_PERIODS)
            if (hasLegacy) {
                val start = prefs[Keys.LEGACY_SEMESTER_START]
                    ?.let { LocalDate.ofEpochDay(it) } ?: AppSettings().semesterStart
                val weeks = prefs[Keys.LEGACY_TOTAL_WEEKS] ?: AppSettings().totalWeeks
                val periodsCsv = prefs[Keys.LEGACY_PERIODS]
                    ?: Timetable.serializePeriods(AppSettings.defaultPeriods())
                val duration = prefs[Keys.LEGACY_PERIOD_DURATION] ?: 45
                result = Timetable(
                    id = 0,
                    name = "默认课表",
                    semesterStartEpochDay = start.toEpochDay(),
                    totalWeeks = weeks,
                    periodsCsv = periodsCsv,
                    periodDurationMinutes = duration
                )
                prefs.remove(Keys.LEGACY_SEMESTER_START)
                prefs.remove(Keys.LEGACY_TOTAL_WEEKS)
                prefs.remove(Keys.LEGACY_PERIODS)
                prefs.remove(Keys.LEGACY_PERIOD_DURATION)
            }
        }
        return result
    }

    suspend fun setActiveTimetable(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_TIMETABLE_ID] = id }
    }

    /** 仅在尚未设置激活课表时写入（应用启动播种用，避免覆盖用户选择） */
    suspend fun ensureActiveTimetable(id: Long) {
        context.dataStore.edit { prefs ->
            if (!prefs.contains(Keys.ACTIVE_TIMETABLE_ID)) {
                prefs[Keys.ACTIVE_TIMETABLE_ID] = id
            }
        }
    }
}

/** 课表仓库：课表的增删改查 */
class TimetableRepository(
    private val timetableDao: TimetableDao,
    private val courseDao: CourseDao
) {
    fun observeAll(): Flow<List<Timetable>> = timetableDao.observeAll()
    suspend fun allOnce(): List<Timetable> = timetableDao.allOnce()
    suspend fun byId(id: Long): Timetable? = timetableDao.byId(id)

    suspend fun create(name: String, base: Timetable? = null): Long {
        val t = (base ?: Timetable(id = 0, name = name)).copy(id = 0, name = name)
        return timetableDao.upsert(t)
    }

    suspend fun rename(id: Long, name: String) {
        timetableDao.byId(id)?.let { timetableDao.upsert(it.copy(name = name)) }
    }

    suspend fun update(timetable: Timetable) = timetableDao.upsert(timetable)

    /** 删除课表及其全部课程 */
    suspend fun delete(id: Long) {
        courseDao.deleteByTimetable(id)
        courseDao.deleteTimetable(id)
    }

    /** 首次运行/升级后播种默认课表，返回激活课表 id */
    suspend fun ensureSeeded(legacy: Timetable?): Long {
        if (timetableDao.count() > 0) {
            return timetableDao.allOnce().first().id
        }
        val seed = legacy ?: Timetable(
            id = 0,
            name = "默认课表",
            periodsCsv = Timetable.serializePeriods(AppSettings.defaultPeriods())
        )
        val id = timetableDao.upsert(seed)
        courseDao.assignTimetable(id)
        return id
    }

    /** 从备份导入一组课表，返回新建课表 id 列表 */
    suspend fun importTimetables(items: List<Pair<Timetable, List<Course>>>): List<Long> {
        val ids = mutableListOf<Long>()
        for ((timetable, courses) in items) {
            val newId = timetableDao.upsert(timetable.copy(id = 0))
            for (c in courses) {
                courseDao.upsert(c.copy(id = 0, timetableId = newId))
            }
            ids.add(newId)
        }
        return ids
    }
}
