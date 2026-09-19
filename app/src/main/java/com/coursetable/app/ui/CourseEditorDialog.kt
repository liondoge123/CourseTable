package com.coursetable.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coursetable.app.data.Course
import com.coursetable.app.data.WeekType
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.CourseColorPalette
import com.coursetable.app.ui.theme.fromStoredLong
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.coursetable.app.ui.theme.toArgbLong

private val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseEditorDialog(
    course: Course,
    totalWeeks: Int,
    periodCount: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    title: String? = null,
    sourceContent: (@Composable () -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showColorPicker: Boolean = true,
    pinSourceContent: Boolean = false,
    embedded: Boolean = false,
    saveLabel: String? = null,
    protectEdits: Boolean = false,
    reviewHints: List<String> = emptyList()
) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.name) }
    var teacher by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.teacher) }
    var location by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.location) }
    var day by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.dayOfWeek.coerceIn(1, 7)) }
    var startSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.startSection.coerceIn(1, periodCount)) }
    var duration by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.duration.coerceIn(1, (periodCount - course.startSection.coerceIn(1, periodCount) + 1).coerceAtLeast(1))) }
    var weekType by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(WeekType.from(course.weekType)) }
    var startWeek by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.startWeek.coerceIn(1, totalWeeks)) }
    var endWeek by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.endWeek.coerceIn(startWeek, totalWeeks)) }
    var color by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(course.color) }
    var saving by remember { mutableStateOf(false) }

    val maxDuration = (periodCount - startSection + 1).coerceAtLeast(1)

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || saving) return
        saving = true
        onSave(
            course.copy(
                name = trimmed,
                teacher = teacher.trim(),
                location = location.trim(),
                dayOfWeek = day,
                startSection = startSection,
                duration = duration,
                weekType = weekType.code,
                startWeek = startWeek,
                endWeek = endWeek,
                color = color
            )
        )
    }

    var discardChanges by remember { mutableStateOf(false) }
    val changed = name != course.name || teacher != course.teacher || location != course.location ||
        day != course.dayOfWeek || startSection != course.startSection || duration != course.duration ||
        startWeek != course.startWeek || endWeek != course.endWeek || weekType.code != course.weekType || color != course.color
    fun requestDismiss() { if (!saving) { if (protectEdits && changed) discardChanges = true else onDismiss() } }
    androidx.activity.compose.BackHandler(embedded) { requestDismiss() }
    if (discardChanges) AlertDialog(onDismissRequest = { discardChanges = false }, title = { Text("保存校对修改？") },
        text = { Text("当前课程有尚未保存的修改。") },
        confirmButton = { TextButton(onClick = { discardChanges = false; save() }, enabled = name.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("放弃修改") } })
    val editorContent: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("course-editor-page")
                .fillMaxHeight(if (embedded) 1f else 0.94f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            val canSave = name.isNotBlank() && !saving

            // 单行一体化顶部操作栏（左侧✕、中间居中标题、右侧对称✓）
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = { requestDismiss() },
                    enabled = !saving,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterStart),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "取消", modifier = Modifier.size(20.dp))
                }

                Text(
                    text = title ?: if (course.id == 0L) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )

                IconButton(
                    onClick = ::save,
                    enabled = canSave,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterEnd),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        contentColor = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "保存", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (pinSourceContent && sourceContent != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("course-editor-pinned-source")) {
                    sourceContent()
                }
            }

            // 表单滚动内容
            Column(
                Modifier
                    .weight(1f)
                    .testTag("course-editor-form")
                    .clipToBounds()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Import review replaces the live preview while sharing the entire editor.
                if (sourceContent != null) {
                    if (!pinSourceContent) sourceContent()
                } else CourseCardPreview(
                    name = name,
                    teacher = teacher,
                    location = location,
                    colorLong = color,
                    day = day,
                    startSection = startSection,
                    duration = duration,
                    weekType = weekType,
                    startWeek = startWeek,
                    endWeek = endWeek
                )

                // 模块 1: 基本信息
                EditorSectionCard(icon = "📝", title = "基本信息") {
                    reviewHints.filter { "名称" in it }.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("课程名称 *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text("任课教师") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("上课地点") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 模块 2: 上课时间
                EditorSectionCard(icon = "⏰", title = "上课时间") {
                    reviewHints.filter { "星期" in it || "节次" in it }.forEach { Text("原识别结果：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        text = "星期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WeekdayLabels.forEachIndexed { index, label ->
                            OptionChip(
                                selected = day == index + 1,
                                onClick = { day = index + 1 },
                                label = "周$label",
                                modifier = Modifier.size(42.dp, 38.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberPickerField(
                            label = "开始节次",
                            value = startSection,
                            range = 1..periodCount,
                            formatter = { "第 $it 节" }
                        ) { newSec ->
                            startSection = newSec
                            duration = duration.coerceIn(1, (periodCount - newSec + 1).coerceAtLeast(1))
                        }
                        NumberPickerField(
                            label = "节数时长",
                            value = duration.coerceIn(1, maxDuration),
                            range = 1..maxDuration,
                            formatter = { "共 $it 节" }
                        ) { duration = it }
                    }
                }

                // 模块 3: 周次范围
                EditorSectionCard(icon = "📅", title = "周次范围") {
                    reviewHints.filter { "周次" in it || "单双周" in it }.forEach { Text("原识别结果：$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        text = "单双周规则",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WeekType.entries.forEach { type ->
                            OptionChip(
                                selected = weekType == type,
                                onClick = { weekType = type },
                                label = type.label,
                                modifier = Modifier.size(68.dp, 38.dp)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberPickerField(
                            label = "开始周",
                            value = startWeek,
                            range = 1..totalWeeks,
                            formatter = { "第 $it 周" }
                        ) { value ->
                            startWeek = value
                            endWeek = endWeek.coerceIn(value, totalWeeks)
                        }
                        NumberPickerField(
                            label = "结束周",
                            value = endWeek,
                            range = startWeek..totalWeeks,
                            formatter = { "第 $it 周" }
                        ) { endWeek = it }
                    }
                }

                // 模块 4: 课程颜色
                if (showColorPicker) EditorSectionCard(icon = "🎨", title = "课程颜色") {
                    val paletteItems = remember {
                        CourseColorPalette.map { c ->
                            Triple(c, c.toArgbLong(), c.luminance() > 0.55f)
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        paletteItems.forEach { (paletteColor, paletteColorLong, isLight) ->
                            val selected = paletteColorLong == color
                            val checkColor = if (isLight) Color(0xFF1C1C1E) else Color.White
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                                    .clickable { color = paletteColorLong },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(paletteColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Text("✓", color = checkColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (onDelete != null && saveLabel == null) TextButton(onClick = onDelete, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (saveLabel != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) TextButton(onClick = onDelete, enabled = !saving) { Text("删除", color = MaterialTheme.colorScheme.error) }
                Button(onClick = ::save, enabled = canSave, modifier = Modifier.weight(1f)) { Text(saveLabel) }
            }
        }
    }
    if (embedded) editorContent() else {
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 0
        ModalBottomSheet(
            onDismissRequest = { requestDismiss() },
            canDismiss = { if (protectEdits && changed) { requestDismiss(); false } else !saving },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(if (imeVisible) 0.dp else 32.dp)
        ) { editorContent() }
    }
}

/**
 * 优雅拟态分组卡片容器
 */
@Composable
private fun EditorSectionCard(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    FormSectionCard(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, style = MaterialTheme.typography.titleSmall)
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

/**
 * 1:1 所见即所得的课表卡片实时效果预览
 */
@Composable
private fun CourseCardPreview(
    name: String,
    teacher: String,
    location: String,
    colorLong: Long,
    day: Int,
    startSection: Int,
    duration: Int,
    weekType: WeekType,
    startWeek: Int,
    endWeek: Int,
    modifier: Modifier = Modifier
) {
    val accent = Color.fromStoredLong(colorLong)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardBg = lerp(MaterialTheme.colorScheme.surface, accent, if (isDark) 0.28f else 0.16f)
    val dayLabel = WeekdayLabels.getOrElse(day - 1) { "一" }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "效果预览",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "周$dayLabel · 第${startSection}-${startSection + duration - 1}节 · ${weekType.label} · ${startWeek}-${endWeek}周",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(cardBg)
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(accent)
                )

                Column(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (name.isBlank()) "课程名称" else name,
                        color = if (name.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (teacher.isBlank()) "任课教师" else teacher,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (teacher.isBlank()) 0.4f else 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (location.isBlank()) "上课教室/地点" else location,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (location.isBlank()) 0.4f else 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 触发数字滚轮选择器的紧凑字段控件
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.NumberPickerField(
    label: String,
    value: Int,
    range: IntRange,
    formatter: (Int) -> String = { it.toString() },
    onValue: (Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Box(Modifier.weight(1f)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                onClick = { showPicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatter(value),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "选择",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
    if (showPicker) {
        NumberWheelPickerDialog(
            title = "选择$label",
            range = range,
            initialValue = value,
            formatter = formatter,
            onConfirm = onValue,
            onDismiss = { showPicker = false }
        )
    }
}
