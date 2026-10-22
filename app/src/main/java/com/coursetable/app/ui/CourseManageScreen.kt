package com.coursetable.app.ui

import com.coursetable.app.data.weeksLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.coursetable.app.CourseApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.Course
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.Icon
import com.coursetable.app.ui.liquid.IconButton
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.liquid.Button
import com.coursetable.app.ui.liquid.ListItemSurface
import com.coursetable.app.ui.liquid.ListItemDefaults
import com.coursetable.app.ui.liquid.ListItemIcon
import com.coursetable.app.ui.liquid.LiquidPageTopBar
import com.coursetable.app.ui.liquid.LiquidScrollablePage
import com.coursetable.app.ui.liquid.pagePadding
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.coursetable.app.ui.theme.fromStoredLong
import com.coursetable.app.ui.theme.nextUnusedCourseColor

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
    val listState = rememberLazyListState()
    val app = LocalContext.current.applicationContext as CourseApp
    val scope = rememberCoroutineScope()
    val groupedCourses = remember(courses) { groupedManagedCourses(courses) }
    val courseCount = groupedCourses.values.sumOf { it.size }
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val addCourse = {
        editingCourse = Course(
            id = 0, name = "", dayOfWeek = 1, startSection = 1, duration = 2,
            startWeek = 1, endWeek = totalWeeks, weekType = 0,
            color = nextUnusedCourseColor(courses)
        )
    }

    LiquidScrollablePage(topBar = if (rootMode) null else ({
        LiquidPageTopBar("课程管理（共 ${courseCount} 门）", onBack) {
            IconButton(onClick = addCourse) {
                Icon(Icons.Filled.Add, contentDescription = "添加课程")
            }
        }
    })) { pageInsets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("courses-list"),
            state = listState,
            contentPadding = pageInsets.pagePadding(extraBottom = bottomContentPadding)
        ) {
            if (rootMode) item(key = "page-title") {
                PageHeader("课程（${courseCount} 门）", contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            if (courses.isEmpty()) item(key = "empty") {
                EmptyCourseList(onAdd = addCourse)
            } else {
                groupedCourses.forEach { (day, dayCourses) ->
                    item(key = "header-$day") {
                        Text(
                            WEEKDAY_SHORT2[day - 1],
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    itemsIndexed(
                        dayCourses,
                        key = { _, course -> "course-${course.courseId.takeIf { it > 0 } ?: -course.id}" },
                        contentType = { _, _ -> "course" }
                    ) { _, course ->
                        ListItemSurface(
                            Modifier.fillMaxWidth().testTag("managed-course-${course.id}")
                                .clip(ListItemDefaults.Shape)
                                .clickable(onClickLabel = "编辑课程") { editingCourse = course }
                        ) {
                            if (largeFont) {
                                Column(
                                    Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color.fromStoredLong(course.color)))
                                        Text(course.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                    }
                                    CourseListDetails(course)
                                }
                            } else {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 64.dp)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ListItemIcon(Icons.Filled.BookOpen, tint = Color.fromStoredLong(course.color), size = 24.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            course.name,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        CourseListDetails(course)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
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
            onDelete = if (course.id > 0 || course.courseId > 0) ({
                scope.launch { if(course.courseId > 0) app.courseRepository.deleteCourse(course.courseId) else onDelete(course); editingCourse = null }
            }) else null,
            onSaveSuspend = { saved ->
                val tableId = saved.timetableId.takeIf { it > 0 } ?: app.settingsRepository.settings.first().timetableId
                val assigned = saved.copy(timetableId = tableId).also { it.additionalSessions = saved.additionalSessions; it.reviewedSnapshot = saved.reviewedSnapshot }
                app.courseRepository.save(assigned); editingCourse = null
            },
            onSave = { saved ->
                onSave(saved)
                editingCourse = null
            }
        )
    }
}

@Composable
private fun CourseListDetails(course: Course, modifier: Modifier = Modifier) {
    val sessions = listOf(course) + course.additionalSessions.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (course.teacher.isNotBlank() || sessions.size > 1) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (course.teacher.isNotBlank()) {
                    Text(
                        "教师：${course.teacher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (sessions.size > 1) {
                    Text(
                        "${sessions.size} 个上课时段",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        sessions.forEach { session ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val end = session.startSection + session.duration - 1
                val sections = if (session.duration == 1) "第${session.startSection}节" else "第${session.startSection}–${end}节"
                Text(
                    "${WEEKDAY_SHORT2.getOrNull(session.dayOfWeek - 1).orEmpty()} · $sections",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    listOf(session.weeksLabel(), session.location).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyCourseList(onAdd: () -> Unit) {
    SectionFrame(Modifier.padding(top = 12.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.School, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("还没有课程", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onAdd, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("添加课程")
            }
        }
    }
}
