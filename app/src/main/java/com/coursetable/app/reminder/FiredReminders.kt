package com.coursetable.app.reminder

import java.time.LocalDate

/** Split at the last separator: event keys themselves contain @. */
internal fun recentFiredReminders(records: Set<String>, today: LocalDate): Map<String, LocalDate> =
    records.mapNotNull { record ->
        val separator = record.lastIndexOf('@')
        if (separator <= 0) return@mapNotNull null
        val date = runCatching { LocalDate.parse(record.substring(separator + 1)) }.getOrNull()
            ?: return@mapNotNull null
        if (date.isBefore(today.minusDays(7)) || date.isAfter(today)) return@mapNotNull null
        record.substring(0, separator) to date
    }.toMap()
