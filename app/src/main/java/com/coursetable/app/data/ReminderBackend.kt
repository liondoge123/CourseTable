package com.coursetable.app.data

enum class ReminderBackend(val key: String, val label: String) {
    APP("app", "应用提醒"),
    SYSTEM_CALENDAR("system_calendar", "系统日历提醒");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: APP
    }
}
