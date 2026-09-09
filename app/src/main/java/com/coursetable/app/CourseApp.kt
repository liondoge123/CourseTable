package com.coursetable.app

import android.app.Application
import com.coursetable.app.data.AppDatabase
import com.coursetable.app.data.CourseRepository
import com.coursetable.app.data.SettingsRepository
import com.coursetable.app.data.TimetableRepository
import com.coursetable.app.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class CourseApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // pdfbox-android 需要从 assets 加载字体度量(.afm)等资源，必须在使用前初始化
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
        } catch (_: Throwable) {
        }

        ReminderScheduler.ensureChannel(this)

        // 播种默认课表（含旧版本 DataStore 学期设置迁移），随后开启提醒自动重排
        applicationScope.launch {
            val legacy = settingsRepository.consumeLegacySemester()
            val activeId = timetableRepository.ensureSeeded(legacy)
            settingsRepository.ensureActiveTimetable(activeId)
            ReminderScheduler.reschedule(this@CourseApp)

            // 设置或课程变化时自动重排课前提醒（跳过首次触发，上面已排过一次）
            combine(
                settingsRepository.settings,
                courseRepository.observeAll()
            ) { s, c -> s to c }
                .distinctUntilChanged()
                .drop(1)
                .collect { ReminderScheduler.reschedule(this@CourseApp) }
        }
    }

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this, database.timetableDao())
    }
    val timetableRepository: TimetableRepository by lazy {
        TimetableRepository(database.timetableDao(), database.courseDao())
    }
    val courseRepository: CourseRepository by lazy {
        CourseRepository(database.courseDao(), settingsRepository.activeTimetableId)
    }
}
