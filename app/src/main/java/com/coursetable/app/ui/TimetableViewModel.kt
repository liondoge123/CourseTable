package com.coursetable.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursetable.app.CourseApp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.CourseRepository
import com.coursetable.app.data.SettingsRepository
import com.coursetable.app.data.Timetable
import com.coursetable.app.data.TimetableRepository
import com.coursetable.app.util.WeekUtils
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WeekFilter { ALL, ODD, EVEN }

class TimetableViewModel(app: CourseApp) : ViewModel() {

    private val courseRepo: CourseRepository = app.courseRepository
    private val settingsRepo: SettingsRepository = app.settingsRepository
    private val timetableRepo: TimetableRepository = app.timetableRepository

    val courses: StateFlow<List<Course>> = courseRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val timetables: StateFlow<List<Timetable>> = timetableRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var latestSettings by mutableStateOf(AppSettings())
        private set

    init {
        viewModelScope.launch {
            settings.collect { latestSettings = it }
        }
    }

    var currentWeek by mutableIntStateOf(1)
        private set

    var weekFilter by mutableStateOf(WeekFilter.ALL)
        private set

    var detailCourse by mutableStateOf<Course?>(null)
        private set

    var editingCourse by mutableStateOf<Course?>(null)
        private set

    var editorOpen by mutableStateOf(false)
        private set

    private var initialized = false
    private var initializedTimetableId: Long? = null

    fun todayWeekNumber(): Int = WeekUtils.clampWeek(
        WeekUtils.weekOf(latestSettings.semesterStart, LocalDate.now()),
        latestSettings.totalWeeks
    )

    fun displayedWeek(): Int {
        if (!initialized || initializedTimetableId != latestSettings.timetableId) {
            initialized = true
            initializedTimetableId = latestSettings.timetableId
            currentWeek = todayWeekNumber()
        }
        return currentWeek.coerceIn(1, latestSettings.totalWeeks)
    }

    fun shiftWeek(delta: Int) {
        setWeek(displayedWeek() + delta)
    }

    fun setWeek(week: Int) {
        currentWeek = WeekUtils.clampWeek(week, latestSettings.totalWeeks)
    }

    fun resetWeek() {
        setWeek(todayWeekNumber())
    }

    fun setFilter(filter: WeekFilter) {
        weekFilter = filter
    }

    fun openCellEditor(dayOfWeek: Int, startSection: Int) {
        editingCourse = Course(
            id = 0,
            name = "",
            dayOfWeek = dayOfWeek,
            startSection = startSection,
            duration = 2,
            startWeek = displayedWeek(),
            endWeek = latestSettings.totalWeeks,
            weekType = 0,
            color = 0xFF4B6EAF
        )
        editorOpen = true
    }

    fun openCourseEditor(course: Course) {
        editingCourse = course
        editorOpen = true
        detailCourse = null
    }

    fun closeEditor() {
        editorOpen = false
        editingCourse = null
    }

    fun showDetail(course: Course) {
        detailCourse = course
    }

    fun closeDetail() {
        detailCourse = null
    }

    fun saveCourse(course: Course) {
        viewModelScope.launch {
            val withTable = if (course.timetableId == 0L) {
                course.copy(timetableId = latestSettings.timetableId)
            } else course
            courseRepo.save(withTable)
            closeEditor()
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            courseRepo.delete(course.id)
            closeDetail()
        }
    }

    fun clearAll() {
        viewModelScope.launch { courseRepo.clear(latestSettings.timetableId) }
    }

    fun switchTimetable(id: Long) {
        if (id == latestSettings.timetableId) return
        viewModelScope.launch { settingsRepo.setActiveTimetable(id) }
    }
}
