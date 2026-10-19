package com.coursetable.app.ui

import androidx.compose.runtime.*
import com.coursetable.app.importer.*
import com.coursetable.app.data.AppSettings

@Composable
internal fun BackupImportReview(preview: ImportPreview.Backup, saving: Boolean, onDismiss: () -> Unit, onConfirm: (List<ImportRequest>) -> Unit) {
    var index by remember(preview) { mutableIntStateOf(0) }
    var requests by remember(preview) { mutableStateOf(emptyList<ImportRequest>()) }
    var candidates by remember(preview, index) { mutableStateOf(preview.imports[index].candidates.map { it.copy(draftId = java.util.UUID.randomUUID().toString()) }) }
    var dirty by remember(preview, index) { mutableStateOf(false) }
    val table = preview.data.timetables[index].timetable
    key(index) { UnifiedImportReview(null, AppSettings(semesterStart = table.semesterStart(), totalWeeks = table.totalWeeks, periods = table.periods()), candidates,
        "备份 ${index + 1}/${preview.data.timetables.size} · ${table.name}", preview.data.warnings, { candidates = it; dirty = true }, onDismiss, {}, saving, dirty, null,
        initialMode = ImportMode.NEW, initialTimetable = table, fixedNew = true, onRequest = { request ->
            val next = requests + request
            if (index == preview.data.timetables.lastIndex) onConfirm(next) else { requests = next; index++ }
        }) }
}
