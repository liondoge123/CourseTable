package com.coursetable.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide Outline icons generated from the official Lucide 1.45.0 SVG release.
 * Source names are kept on each ImageVector for traceability.
 */
private fun buildLucideIcon(
    name: String,
    strokePaths: Array<out String>,
    fillPaths: Array<out String> = emptyArray()
): ImageVector = ImageVector.Builder(
    name = "lucide-$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    strokePaths.forEach { pathData ->
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    fillPaths.forEach { pathData ->
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black)
        )
    }
}.build()

private fun lucideIcon(name: String, vararg paths: String): ImageVector =
    buildLucideIcon(name, paths)

private val plusIcon = lucideIcon(
    "plus",
    "M5 12h14",
    "M12 5v14",
)

private val minusIcon = lucideIcon(
    "minus",
    "M5 12h14",
)

private val closeIcon = lucideIcon(
    "x",
    "M18 6 6 18",
    "m6 6 12 12",
)

private val checkIcon = lucideIcon(
    "check",
    "M20 6 9 17l-5-5",
)

private val arrowBackIcon = lucideIcon(
    "arrow-left",
    "m12 19-7-7 7-7",
    "M19 12H5",
)

private val chevronLeftIcon = lucideIcon(
    "chevron-left",
    "m15 18-6-6 6-6",
)

private val chevronRightIcon = lucideIcon(
    "chevron-right",
    "m9 18 6-6-6-6",
)

private val listIcon = lucideIcon(
    "list",
    "M3 5h.01",
    "M3 12h.01",
    "M3 19h.01",
    "M8 5h13",
    "M8 12h13",
    "M8 19h13",
)

private val calendarDaysIcon = lucideIcon(
    "calendar-days",
    "M8 2v3",
    "M16 2v3",
    "M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z",
    "M3 9h18",
    "M8 13h.01",
    "M12 13h.01",
    "M16 13h.01",
    "M8 17h.01",
    "M12 17h.01",
    "M16 17h.01",
)

private val calendarRangeIcon = lucideIcon(
    "calendar-range",
    "M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z",
    "M16 2v3",
    "M3 9h18",
    "M8 2v3",
    "M17 13h-6",
    "M13 17H7",
    "M7 13h.01",
    "M17 17h.01",
)

private val calendarCheckIcon = lucideIcon(
    "calendar-check",
    "M8 2v3",
    "M16 2v3",
    "M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z",
    "M3 9h18",
    "m9 15 2 2 4-4",
)

private val calendarClockIcon = lucideIcon(
    "calendar-clock",
    "M16 14v2.2l1.6 1",
    "M16 2v3",
    "M21 7.338V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h2.338",
    "M3 9h5.859",
    "M8 2v3",
    "M 22 16 A 6 6 0 1 0 10 16 A 6 6 0 1 0 22 16",
)

private val clockIcon = lucideIcon(
    "clock-3",
    "M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12",
    "M12 6v6h4",
)

private val trashIcon = lucideIcon(
    "trash",
    "M10 11v6",
    "M14 11v6",
    "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
    "M3 6h18",
    "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
)

private val editIcon = lucideIcon(
    "pencil",
    "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
    "m15 5 4 4",
)

private val userIcon = lucideIcon(
    "user-round",
    "M 17 8 A 5 5 0 1 0 7 8 A 5 5 0 1 0 17 8",
    "M20 21a8 8 0 0 0-16 0",
)

private val usersIcon = lucideIcon(
    "users-round",
    "M18 21a8 8 0 0 0-16 0",
    "M 15 8 A 5 5 0 1 0 5 8 A 5 5 0 1 0 15 8",
    "M22 20c0-3.37-2-6.5-4-8a5 5 0 0 0-.45-8.3",
)

private val pinIcon = lucideIcon(
    "map-pin",
    "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0",
    "M 15 10 A 3 3 0 1 0 9 10 A 3 3 0 1 0 15 10",
)

private val refreshIcon = lucideIcon(
    "refresh-cw",
    "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
    "M21 3v5h-5",
    "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
    "M8 16H3v5",
)

private val restoreIcon = lucideIcon(
    "rotate-ccw",
    "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
    "M3 3v5h5",
)

private val bellIcon = lucideIcon(
    "bell",
    "M10.268 21a2 2 0 0 0 3.464 0",
    "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
)

private val activeBellIcon = lucideIcon(
    "bell-ring",
    "M10.268 21a2 2 0 0 0 3.464 0",
    "M22 8c0-2.3-.8-4.3-2-6",
    "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
    "M4 2C2.8 3.7 2 5.7 2 8",
)

private val gearIcon = lucideIcon(
    "settings",
    "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
    "M 15 12 A 3 3 0 1 0 9 12 A 3 3 0 1 0 15 12",
)

private val downloadIcon = lucideIcon(
    "download",
    "M12 15V3",
    "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
    "m7 10 5 5 5-5",
)

private val saveIcon = lucideIcon(
    "save",
    "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
    "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7",
    "M7 3v4a1 1 0 0 0 1 1h7",
)

private val shareIcon = lucideIcon(
    "share-2",
    "M 21 5 A 3 3 0 1 0 15 5 A 3 3 0 1 0 21 5",
    "M 9 12 A 3 3 0 1 0 3 12 A 3 3 0 1 0 9 12",
    "M 21 19 A 3 3 0 1 0 15 19 A 3 3 0 1 0 21 19",
    "M 8.59 13.51 L 15.42 17.49",
    "M 15.41 6.51 L 8.59 10.49",
)

private val schoolIcon = lucideIcon(
    "school",
    "M14 21v-3a2 2 0 0 0-4 0v3",
    "M18 4.933V21",
    "m4 6 7.106-3.79a2 2 0 0 1 1.788 0L20 6",
    "m6 11-3.52 2.147a1 1 0 0 0-.48.854V19a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5a1 1 0 0 0-.48-.853L18 11",
    "M6 4.933V21",
    "M 14 9 A 2 2 0 1 0 10 9 A 2 2 0 1 0 14 9",
)

private val fileImportIcon = lucideIcon(
    "file-down",
    "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
    "M14 2v5a1 1 0 0 0 1 1h5",
    "M12 18v-6",
    "m9 15 3 3 3-3",
)

private val shieldIcon = lucideIcon(
    "shield-check",
    "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
    "m9 12 2 2 4-4",
)

private val batteryIcon = lucideIcon(
    "battery-charging",
    "m11 7-3 5h4l-3 5",
    "M14.856 6H16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.935",
    "M22 14v-4",
    "M5.14 18H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2.936",
)

private val playIcon = lucideIcon(
    "circle-play",
    "M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z",
    "M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12",
)

private val autoStartIcon = lucideIcon(
    "power",
    "M12 2v10",
    "M18.4 6.6a9 9 0 1 1-12.77.04",
)

private val sendIcon = lucideIcon(
    "send",
    "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
    "m21.854 2.147-10.94 10.939",
)

private val brightnessIcon = lucideIcon(
    "sun-moon",
    "M12 2v2",
    "M14.837 16.385a6 6 0 1 1-7.223-7.222c.624-.147.97.66.715 1.248a4 4 0 0 0 5.26 5.259c.589-.255 1.396.09 1.248.715",
    "M16 12a4 4 0 0 0-4-4",
    "m19 5-1.256 1.256",
    "M20 12h2",
)

private val paletteIcon = buildLucideIcon(
    name = "palette",
    strokePaths = arrayOf(
        "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
        "M 14 6.5 A 0.5 0.5 0 1 0 13 6.5 A 0.5 0.5 0 1 0 14 6.5",
        "M 18 10.5 A 0.5 0.5 0 1 0 17 10.5 A 0.5 0.5 0 1 0 18 10.5",
        "M 7 12.5 A 0.5 0.5 0 1 0 6 12.5 A 0.5 0.5 0 1 0 7 12.5",
        "M 9 7.5 A 0.5 0.5 0 1 0 8 7.5 A 0.5 0.5 0 1 0 9 7.5",
    ),
    fillPaths = arrayOf(
        "M 14 6.5 A 0.5 0.5 0 1 0 13 6.5 A 0.5 0.5 0 1 0 14 6.5",
        "M 18 10.5 A 0.5 0.5 0 1 0 17 10.5 A 0.5 0.5 0 1 0 18 10.5",
        "M 7 12.5 A 0.5 0.5 0 1 0 6 12.5 A 0.5 0.5 0 1 0 7 12.5",
        "M 9 7.5 A 0.5 0.5 0 1 0 8 7.5 A 0.5 0.5 0 1 0 9 7.5",
    )
)

private val alignLeftIcon = lucideIcon(
    "text-align-start",
    "M21 5H3",
    "M15 12H3",
    "M17 19H3",
)

private val visibilityIcon = lucideIcon(
    "eye",
    "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0",
    "M 15 12 A 3 3 0 1 0 9 12 A 3 3 0 1 0 15 12",
)

private val infoIcon = lucideIcon(
    "info",
    "M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12",
    "M12 16v-4",
    "M12 8h.01",
)

private val filterListIcon = lucideIcon(
    "list-filter",
    "M2 5h20",
    "M6 12h12",
    "M9 19h6",
)

private val radioIcon = lucideIcon(
    "circle",
    "M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12",
)

private val checkCircleIcon = lucideIcon(
    "circle-check",
    "M 22 12 A 10 10 0 1 0 2 12 A 10 10 0 1 0 22 12",
    "m16 9-5.5 5.5L8 12",
)

private val bookIcon = lucideIcon(
    "book-open",
    "M12 5v16",
    "M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z",
)

private val moreIcon = lucideIcon(
    "ellipsis",
    "M 13 12 A 1 1 0 1 0 11 12 A 1 1 0 1 0 13 12",
    "M 20 12 A 1 1 0 1 0 18 12 A 1 1 0 1 0 20 12",
    "M 6 12 A 1 1 0 1 0 4 12 A 1 1 0 1 0 6 12",
)

private val viewWeekIcon = lucideIcon(
    "columns-3",
    "M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z",
    "M9 3v18",
    "M15 3v18",
)

object Icons {
    object Filled {
        val Add = plusIcon; val Remove = minusIcon; val Close = closeIcon; val Check = checkIcon
        val DateRange = calendarRangeIcon; val CalendarMonth = calendarDaysIcon; val Today = calendarCheckIcon
        val ViewWeek = viewWeekIcon; val EventNote = calendarClockIcon; val Schedule = clockIcon
        val Delete = trashIcon; val DeleteForever = trashIcon; val Edit = editIcon
        val Person = userIcon; val Groups = usersIcon; val LocationOn = pinIcon
        val Refresh = refreshIcon; val Notifications = bellIcon; val NotificationsActive = activeBellIcon
        val Settings = gearIcon; val CloudDownload = downloadIcon; val Save = saveIcon; val IosShare = shareIcon
        val School = schoolIcon; val FileImport = fileImportIcon; val Restore = restoreIcon
        val Security = shieldIcon; val BatterySaver = batteryIcon; val PlayCircle = playIcon; val AutoStart = autoStartIcon; val Send = sendIcon
        val Brightness6 = brightnessIcon; val Palette = paletteIcon; val FormatAlignLeft = alignLeftIcon
        val Visibility = visibilityIcon; val Info = infoIcon
        val FilterList = filterListIcon; val RadioButtonUnchecked = radioIcon; val CheckCircle = checkCircleIcon
        val BookOpen = bookIcon; val MoreHorizontal = moreIcon
    }
    object AutoMirrored {
        object Filled {
            val ArrowBack = arrowBackIcon
            val KeyboardArrowLeft = chevronLeftIcon
            val KeyboardArrowRight = chevronRightIcon
            val List = listIcon
        }
    }
}
