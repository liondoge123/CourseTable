package com.coursetable.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.HorizontalDivider
import com.coursetable.app.ui.liquid.Icon
import com.coursetable.app.ui.liquid.IconButton
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.coursetable.app.ui.theme.fromStoredLong
import kotlinx.coroutines.delay

private val WEEKDAY_SHORT2 = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun CourseManageScreen(
    courses: List<Course>,
    totalWeeks: Int,
    periodCount: Int,
    onBack: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    rootMode: Boolean = false,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(deleteConfirmId) {
        if (deleteConfirmId != null) {
            delay(5_000)
            deleteConfirmId = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!rootMode) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            Text(
                if (rootMode) "课程" else "课程管理（共 ${courses.size} 门）",
                style = if (rootMode) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (!rootMode) {
                IconButton(onClick = {
                    editingCourse = Course(
                        id = 0, name = "", dayOfWeek = 1, startSection = 1, duration = 2,
                        startWeek = 1, endWeek = totalWeeks, weekType = 0, color = 0xFF0A84FF
                    )
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加课程")
                }
            }
        }

        if (courses.isEmpty()) {
            Text(
                if (rootMode) "暂无课程，使用右下角 + 添加。" else "暂无课程，点右上角 + 添加。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val groupedCourses = courses.sortedWith(compareBy<Course> { it.dayOfWeek }.thenBy { it.startSection })
                .groupBy { it.dayOfWeek.coerceIn(1, 7) }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomContentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedCourses.forEach { (day, dayCourses) ->
                    item(key = "header-$day") {
                        Text(
                            WEEKDAY_SHORT2[day - 1],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        )
                    }
                    item(key = "group-$day") {
                        SectionFrame {
                            Column(Modifier.padding(horizontal = 4.dp)) {
                                dayCourses.forEach { course ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editingCourse = course }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .padding(end = 10.dp)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.fromStoredLong(course.color))
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        course.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        courseSummary2(course),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                InlineDeleteAction(
                                    armed = deleteConfirmId == course.id,
                                    onArm = { deleteConfirmId = course.id },
                                    onConfirm = {
                                        onDelete(course)
                                        deleteConfirmId = null
                                    },
                                    compact = true
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingCourse?.let { course ->
        CourseEditorDialog(
            course = course,
            totalWeeks = totalWeeks,
            periodCount = periodCount,
            onDismiss = { editingCourse = null },
            onSave = { saved ->
                onSave(saved)
                editingCourse = null
            }
        )
    }
}

private fun courseSummary2(course: Course): String {
    val day = WEEKDAY_SHORT2.getOrNull(course.dayOfWeek - 1) ?: ""
    val type = when (course.weekType) {
        1 -> "单周"
        2 -> "双周"
        else -> ""
    }
    val sec = "第${course.startSection}-${course.startSection + course.duration - 1}节"
    val weeks = "第${course.startWeek}-${course.endWeek}周$type"
    val loc = if (course.location.isNotBlank()) " · ${course.location}" else ""
    val teacher = if (course.teacher.isNotBlank()) " · ${course.teacher}" else ""
    return "$day $sec $weeks$loc$teacher"
}
