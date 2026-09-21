package com.coursetable.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.coursetable.app.data.AppSettings
import com.coursetable.app.data.Course
import com.coursetable.app.data.PeriodTime
import com.coursetable.app.data.PeriodUtils
import com.coursetable.app.data.WeekType
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.fromStoredLong
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.coursetable.app.util.WeekUtils
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
private val DATE_FMT = DateTimeFormatter.ofPattern("M/d")

private const val TIME_COL_DP = 32f
private const val SLOT_H_DP = 62f
private const val HEADER_H_DP = 46f
private val FLOATING_WEEK_HEADER_INSET = 52.dp

@Composable
fun TimetableScreen(
    vm: TimetableViewModel,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onAtBottomChanged: (Boolean) -> Unit = {}
) {
    val settings by vm.settings.collectAsState()
    val courses by vm.courses.collectAsState()
    val timetables by vm.timetables.collectAsState()

    val totalWeeks = settings.totalWeeks
    val displayedWeek = vm.displayedWeek()
    val todayWeek = vm.todayWeekNumber()
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = (displayedWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0)),
        pageCount = { totalWeeks.coerceAtLeast(1) }
    )
    var showWeekPicker by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        vm.setWeek(pagerState.currentPage + 1)
    }

    LaunchedEffect(displayedWeek, totalWeeks) {
        val targetPage = (displayedWeek - 1).coerceIn(0, (totalWeeks - 1).coerceAtLeast(0))
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .topChromeGlassBackdropSource()
        ) {
            // Supply colour in the initial header spacer; after scrolling, the grid
            // itself moves through this area and becomes the sampled glass backdrop.
            LiquidAmbientBackground(Modifier.fillMaxSize())
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val week = page + 1
                val visible = remember(courses, settings.showNonCurrentWeek, vm.weekFilter, week) {
                    rememberCourses(settings, courses, week, vm.weekFilter)
                }
                TimetableGrid(
                    topContentPadding = FLOATING_WEEK_HEADER_INSET,
                    bottomContentPadding = bottomContentPadding,
                    settings = settings,
                    semesterStart = settings.semesterStart,
                    displayedWeek = week,
                    visible = visible,
                    isTimeTodayColumn = todayWeek == week,
                    todayDay = LocalDate.now().dayOfWeek.value,
                    onCourseTap = { vm.showDetail(it) },
                    onCellTap = { day, section -> vm.openCellEditor(day, section) },
                    onAtBottomChanged = if (page == pagerState.currentPage) onAtBottomChanged else null
                )
            }
        }

        WeekHeader(
            todayWeek = todayWeek,
            displayedWeek = displayedWeek,
            totalWeeks = totalWeeks,
            timetables = timetables,
            activeTimetableId = settings.timetableId,
            onSwitchTimetable = { vm.switchTimetable(it) },
            canPrev = pagerState.currentPage > 0,
            canNext = pagerState.currentPage < totalWeeks - 1,
            onPrev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            onTitleClick = { showWeekPicker = true },
            filter = vm.weekFilter,
            onCycleFilter = {
                val next = when (vm.weekFilter) {
                    WeekFilter.ALL -> WeekFilter.ODD
                    WeekFilter.ODD -> WeekFilter.EVEN
                    WeekFilter.EVEN -> WeekFilter.ALL
                }
                vm.setFilter(next)
            }
        )
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            currentWeek = displayedWeek,
            todayWeek = todayWeek,
            totalWeeks = totalWeeks,
            onPick = { week ->
                showWeekPicker = false
                scope.launch { pagerState.scrollToPage(week - 1) }
            },
            onJumpToday = {
                showWeekPicker = false
                scope.launch { pagerState.animateScrollToPage(todayWeek - 1) }
            },
            onDismiss = { showWeekPicker = false }
        )
    }

    vm.detailCourse?.let { detail ->
        CourseDetailDialog(
            course = detail,
            isNonCurrentWeek = !detail.visibleOnWeek(displayedWeek),
            onDismiss = { vm.closeDetail() },
            onEdit = { vm.openCourseEditor(detail) },
            onDelete = { vm.deleteCourse(detail) }
        )
    }
}

private fun rememberCourses(
    settings: AppSettings,
    courses: List<Course>,
    displayedWeek: Int,
    filter: WeekFilter
): List<Course> {
    val byFilter = when (filter) {
        WeekFilter.ALL -> courses
        WeekFilter.ODD -> courses.filter {
            it.weekType != WeekType.EVEN.code &&
                WeekUtils.weekRangeHasOdd(it.startWeek, it.endWeek)
        }
        WeekFilter.EVEN -> courses.filter {
            it.weekType != WeekType.ODD.code &&
                WeekUtils.weekRangeHasEven(it.startWeek, it.endWeek)
        }
    }
    val notEnded = byFilter.filter { it.lastWeek() >= displayedWeek }
    return if (settings.showNonCurrentWeek) notEnded else notEnded.filter { it.visibleOnWeek(displayedWeek) }
}

@Composable
private fun WeekHeader(
    todayWeek: Int,
    displayedWeek: Int,
    totalWeeks: Int,
    timetables: List<com.coursetable.app.data.Timetable>,
    activeTimetableId: Long,
    onSwitchTimetable: (Long) -> Unit,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: () -> Unit,
    filter: WeekFilter,
    onCycleFilter: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧微胶囊组：多课表切换 + 当前周次主胶囊
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (timetables.size > 1) {
                TimetableSelector(timetables, activeTimetableId, onSwitchTimetable)
            }
            WeekPickerChip(
                displayedWeek = displayedWeek,
                isTodayWeek = displayedWeek == todayWeek,
                onClick = onTitleClick
            )
        }

        Spacer(Modifier.weight(1f))

        // 右侧微胶囊组：单双周筛选 + 连体翻周按键 [ < │ > ]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterCycleButton(filter = filter, onClick = onCycleFilter)
            WeekNavigationPill(
                canPrev = canPrev,
                canNext = canNext,
                onPrev = onPrev,
                onNext = onNext
            )
        }
    }
}

@Composable
private fun WeekPickerChip(
    displayedWeek: Int,
    isTodayWeek: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    LiquidCapsuleSurface(
        modifier = Modifier.height(36.dp),
        onClick = onClick,
        preferTopChromeBackdrop = true
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isTodayWeek) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = "第 $displayedWeek 周",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isTodayWeek) colors.primary else colors.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "▾",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimetableSelector(
    timetables: List<com.coursetable.app.data.Timetable>,
    activeId: Long,
    onSwitch: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val active = timetables.firstOrNull { it.id == activeId } ?: timetables.firstOrNull()
    val colors = MaterialTheme.colorScheme

    Box {
        LiquidCapsuleSurface(
            modifier = Modifier.height(36.dp),
            onClick = { expanded = true },
            preferTopChromeBackdrop = true
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = active?.name ?: "课表",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 68.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            timetables.forEach { table ->
                DropdownMenuItem(
                    text = {
                        Text(
                            table.name,
                            fontWeight = if (table.id == activeId) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onSwitch(table.id)
                    },
                    selected = table.id == activeId
                )
            }
        }
    }
}

@Composable
private fun FilterCycleButton(filter: WeekFilter, onClick: () -> Unit) {
    val label = when (filter) {
        WeekFilter.ALL -> "全部"
        WeekFilter.ODD -> "单周"
        WeekFilter.EVEN -> "双周"
    }
    val isFiltered = filter != WeekFilter.ALL
    val colors = MaterialTheme.colorScheme
    val baseColor = if (isFiltered) colors.primary.copy(alpha = if (colors.isDark) 0.20f else 0.14f) else null
    val borderColor = if (isFiltered) colors.primary.copy(alpha = 0.45f) else null

    LiquidCapsuleSurface(
        modifier = Modifier.height(36.dp),
        onClick = onClick,
        baseColor = baseColor,
        borderColor = borderColor,
        preferTopChromeBackdrop = true
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isFiltered) colors.primary else colors.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isFiltered) colors.primary else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekNavigationPill(
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val prevInteractionSource = remember { MutableInteractionSource() }
    val prevPressed by prevInteractionSource.collectIsPressedAsState()
    val prevProgress by animateFloatAsState(
        targetValue = if (prevPressed && canPrev) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f),
        label = "pillPrevPress"
    )

    val nextInteractionSource = remember { MutableInteractionSource() }
    val nextPressed by nextInteractionSource.collectIsPressedAsState()
    val nextProgress by animateFloatAsState(
        targetValue = if (nextPressed && canNext) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 360f),
        label = "pillNextPress"
    )

    val pillPressProgress = maxOf(prevProgress, nextProgress)

    LiquidCapsuleSurface(
        modifier = Modifier.height(36.dp),
        externalPressProgress = pillPressProgress,
        preferTopChromeBackdrop = true
    ) {
        Row(
            Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(34.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                    .clickable(
                        interactionSource = prevInteractionSource,
                        indication = null,
                        enabled = canPrev,
                        onClick = onPrev
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一周",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            val scale = androidx.compose.ui.util.lerp(1f, 1.20f, prevProgress)
                            scaleX = scale
                            scaleY = scale
                        },
                    tint = colors.onSurface.copy(alpha = if (canPrev) 0.9f else 0.35f)
                )
            }
            Box(
                Modifier
                    .width(0.8.dp)
                    .height(14.dp)
                    .background(colors.glassBorder)
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(34.dp)
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
                    .clickable(
                        interactionSource = nextInteractionSource,
                        indication = null,
                        enabled = canNext,
                        onClick = onNext
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一周",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            val scale = androidx.compose.ui.util.lerp(1f, 1.20f, nextProgress)
                            scaleX = scale
                            scaleY = scale
                        },
                    tint = colors.onSurface.copy(alpha = if (canNext) 0.9f else 0.35f)
                )
            }
        }
    }
}

@Composable
private fun TimetableGrid(
    topContentPadding: androidx.compose.ui.unit.Dp,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    settings: AppSettings,
    semesterStart: LocalDate,
    displayedWeek: Int,
    visible: List<Course>,
    isTimeTodayColumn: Boolean,
    todayDay: Int,
    onCourseTap: (Course) -> Unit,
    onCellTap: (Int, Int) -> Unit,
    onAtBottomChanged: ((Boolean) -> Unit)?
) {
    val timeColW = TIME_COL_DP.dp
    val slotH = SLOT_H_DP.dp
    val headerH = HEADER_H_DP.dp
    val vScroll = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    val bottomThresholdPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val slots = settings.periods.size.coerceAtLeast(1)
    val periods = settings.periods
    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(isTimeTodayColumn, todayDay) {
        if (!isTimeTodayColumn || todayDay !in 1..7) return@LaunchedEffect
        while (true) {
            currentTime = LocalTime.now()
            delay(30_000L)
        }
    }

    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val todayColBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    val containerCornerRadius = 12.dp
    val containerShape = RoundedCornerShape(containerCornerRadius)
    val viewportShape = RoundedCornerShape(
        topStart = containerCornerRadius,
        topEnd = containerCornerRadius
    )

    LaunchedEffect(vScroll, onAtBottomChanged) {
        if (onAtBottomChanged == null) return@LaunchedEffect
        snapshotFlow { vScroll.value to vScroll.maxValue }
            .collect { (value, maxValue) ->
                onAtBottomChanged(maxValue > 0 && value >= (maxValue - bottomThresholdPx).coerceAtLeast(0))
            }
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .clip(viewportShape)
                .verticalScroll(vScroll)
                .padding(
                    top = topContentPadding + 8.dp,
                    bottom = bottomContentPadding + 8.dp
                )
        ) {
            val viewportW = maxWidth
            val dayW = (viewportW - timeColW) / 7
            val gridW = viewportW
            val highlightToday = isTimeTodayColumn && todayDay in 1..7

            Box(
                Modifier
                    .width(gridW)
                    .height(headerH + slotH * slots)
                    .clip(containerShape)
                    .background(surfaceColor)
                    .drawBehind {
                        val tw = timeColW.toPx()
                        val hh = headerH.toPx()
                        val sh = slotH.toPx()
                        val dw = (size.width - tw) / 7f
                        val line = 1.dp.toPx()

                        if (highlightToday) {
                            val x = tw + (todayDay - 1) * dw
                            drawRect(todayColBg, topLeft = Offset(x, 0f), size = Size(dw, hh + sh * slots))
                        }
                        // 仅保留表头下方一条分隔线
                        drawLine(lineColor, Offset(0f, hh), Offset(size.width, hh), line)
                    }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, containerShape)
            ) {
                Row(
                    Modifier
                        .width(gridW)
                        .height(headerH)
                ) {
                    Spacer(Modifier.size(timeColW, headerH))
                    repeat(7) { dayIndex ->
                        val day = dayIndex + 1
                        val isToday = highlightToday && day == todayDay
                        val date = runCatching {
                            WeekUtils.dateOfWeek(semesterStart, displayedWeek, day).format(DATE_FMT)
                        }.getOrDefault("")
                        Box(
                            Modifier
                                .width(dayW)
                                .height(headerH),
                            contentAlignment = Alignment.Center
                        ) {
                            TimetableDayHeader(
                                label = WEEKDAY_NAMES[dayIndex],
                                date = date,
                                isToday = isToday,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Box(
                    Modifier
                        .offset(y = headerH)
                        .width(gridW)
                        .height(slotH * slots)
                ) {
                    Column(Modifier.width(timeColW)) {
                        repeat(slots) { row ->
                            Box(
                                Modifier
                                    .width(timeColW)
                                    .height(slotH),
                                contentAlignment = Alignment.Center
                            ) {
                                val period = periods.getOrNull(row)
                                TimetablePeriodLabel(
                                    section = row + 1,
                                    startTime = period?.start?.toString()?.substring(0, 5),
                                    endTime = period?.end?.toString()?.substring(0, 5),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Box(
                        Modifier
                            .offset(x = timeColW)
                            .size(width = dayW * 7, height = slotH * slots)
                            .pointerInput(slots, dayW, timeColW) {
                                detectTapGestures { offset ->
                                    val day = (offset.x / dayW.toPx()).toInt() + 1
                                    val section = (offset.y / slotH.toPx()).toInt() + 1
                                    if (day in 1..7 && section in 1..slots) {
                                        onCellTap(day, section)
                                    }
                                }
                            }
                    )

                    visible.forEach { course ->
                        val isTodayCourse = highlightToday &&
                            course.dayOfWeek == todayDay &&
                            course.visibleOnWeek(displayedWeek)
                        val progress = if (isTodayCourse) {
                            PeriodUtils.calculateCourseProgress(
                                startSection = course.startSection,
                                duration = course.duration,
                                periods = periods,
                                now = currentTime
                            )
                        } else null

                        CourseCard(
                            course = course,
                            timeColW = timeColW,
                            dayW = dayW,
                            slotH = slotH,
                            alignLeft = settings.cardAlignLeft,
                            dimmed = settings.showNonCurrentWeek && !course.visibleOnWeek(displayedWeek),
                            progress = progress,
                            onClick = { onCourseTap(course) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    timeColW: androidx.compose.ui.unit.Dp,
    dayW: androidx.compose.ui.unit.Dp,
    slotH: androidx.compose.ui.unit.Dp,
    alignLeft: Boolean,
    dimmed: Boolean,
    progress: Float? = null,
    onClick: () -> Unit
) {
    val x = dayW * (course.dayOfWeek - 1) + timeColW + 1.5.dp
    val y = slotH * (course.startSection - 1) + 1.5.dp
    val accent = Color.fromStoredLong(course.color)
    TimetableCourseCardSurface(
        name = course.name,
        teacher = course.teacher,
        location = course.location,
        accent = accent,
        duration = course.duration,
        alignLeft = alignLeft,
        dimmed = dimmed,
        progress = progress,
        onClick = onClick,
        modifier = Modifier
            .offset(x = x, y = y)
            .size(width = dayW - 3.dp, height = slotH * course.duration - 3.dp)
    )
}

@Composable
private fun WeekPickerDialog(
    currentWeek: Int,
    todayWeek: Int,
    totalWeeks: Int,
    onPick: (Int) -> Unit,
    onJumpToday: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            SheetHeader(
                title = "选择周次",
                subtitle = "当前第 $currentWeek 周 · 共 $totalWeeks 周"
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (todayWeek in 1..totalWeeks) {
                    Button(
                        onClick = onJumpToday,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Today,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("回到本周（第 $todayWeek 周）")
                    }
                }
                val weeks = (1..totalWeeks).toList()
                weeks.chunked(5).forEach { rowWeeks ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowWeeks.forEach { week ->
                            val isCurrent = week == currentWeek
                            val isToday = week == todayWeek
                            val bg = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val fg = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimary
                                isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(
                                        1.dp,
                                        if (isCurrent || isToday) Color.Transparent
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onPick(week) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$week",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isCurrent || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = fg
                                    )
                                    if (isToday) {
                                        Text(
                                            text = "本周",
                                            fontSize = 10.sp,
                                            lineHeight = 11.sp,
                                            color = fg.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }
                        repeat(5 - rowWeeks.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(
    course: Course,
    isNonCurrentWeek: Boolean = false,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = Color.fromStoredLong(course.color)
    var deleteArmed by remember { mutableStateOf(false) }
    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            kotlinx.coroutines.delay(5_000)
            deleteArmed = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(cardColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isNonCurrentWeek) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "非本周",
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionFrame(Modifier.padding(horizontal = 20.dp)) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DetailRow(Icons.Filled.Person, "教师", course.teacher)
                    DetailRow(Icons.Filled.LocationOn, "地点", course.location)
                    DetailRow(
                        Icons.Filled.Schedule,
                        "时间",
                        "${WEEKDAY_NAMES.getOrNull(course.dayOfWeek - 1) ?: ""} " +
                            "第${course.startSection}-${course.startSection + course.duration - 1}节  " +
                            weekRangeText(course)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InlineDeleteAction(
                    armed = deleteArmed,
                    onArm = { deleteArmed = true },
                    onConfirm = onDelete,
                    compact = false
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("编辑")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "未填写" },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun weekRangeText(course: Course): String {
    val type = when (course.weekType) {
        WeekType.ODD.code -> "单周"
        WeekType.EVEN.code -> "双周"
        else -> ""
    }
    return "$type 第${course.startWeek}-${course.endWeek}周".trim()
}
