package com.coursetable.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun lineIcon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw
        )
    }.build()

private val plusIcon = lineIcon("plus") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) }
private val minusIcon = lineIcon("minus") { moveTo(5f, 12f); horizontalLineTo(19f) }
private val closeIcon = lineIcon("close") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
private val checkIcon = lineIcon("check") { moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7f) }
private val backIcon = lineIcon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
private val nextIcon = lineIcon("next") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
private val listIcon = lineIcon("list") { moveTo(8f, 7f); horizontalLineTo(19f); moveTo(8f, 12f); horizontalLineTo(19f); moveTo(8f, 17f); horizontalLineTo(19f); moveTo(5f, 7f); lineTo(5.01f, 7f); moveTo(5f, 12f); lineTo(5.01f, 12f); moveTo(5f, 17f); lineTo(5.01f, 17f) }
private val calendarIcon = lineIcon("calendar") { moveTo(7f, 3f); verticalLineTo(6f); moveTo(17f, 3f); verticalLineTo(6f); moveTo(4f, 9f); horizontalLineTo(20f); moveTo(6f, 5f); horizontalLineTo(18f); quadTo(20f, 5f, 20f, 7f); verticalLineTo(19f); quadTo(20f, 21f, 18f, 21f); horizontalLineTo(6f); quadTo(4f, 21f, 4f, 19f); verticalLineTo(7f); quadTo(4f, 5f, 6f, 5f) }
private val clockIcon = lineIcon("clock") { moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); moveTo(12f, 7f); verticalLineTo(12f); lineTo(15.5f, 14f) }
private val trashIcon = lineIcon("trash") { moveTo(5f, 7f); horizontalLineTo(19f); moveTo(9f, 7f); verticalLineTo(4f); horizontalLineTo(6f); moveTo(7f, 7f); lineTo(8f, 21f); horizontalLineTo(16f); lineTo(17f, 7f); moveTo(10f, 11f); verticalLineTo(17f); moveTo(14f, 11f); verticalLineTo(17f) }
private val editIcon = lineIcon("edit") { moveTo(4f, 20f); lineTo(8f, 19f); lineTo(19f, 8f); lineTo(16f, 5f); lineTo(5f, 16f); close(); moveTo(14.5f, 6.5f); lineTo(17.5f, 9.5f) }
private val userIcon = lineIcon("user") { moveTo(12f, 4f); arcTo(4f, 4f, 0f, true, true, 11.99f, 4f); moveTo(4.5f, 21f); quadTo(5.5f, 15f, 12f, 15f); quadTo(18.5f, 15f, 19.5f, 21f) }
private val pinIcon = lineIcon("pin") { moveTo(12f, 21f); quadTo(5f, 14f, 5f, 9f); arcTo(7f, 7f, 0f, true, true, 19f, 9f); quadTo(19f, 14f, 12f, 21f); moveTo(12f, 7f); arcTo(2f, 2f, 0f, true, true, 11.99f, 7f) }
private val refreshIcon = lineIcon("refresh") { moveTo(20f, 6f); verticalLineTo(11f); horizontalLineTo(15f); moveTo(19f, 11f); arcTo(8f, 8f, 0f, true, false, 5f, 7f); moveTo(4f, 18f); verticalLineTo(13f); horizontalLineTo(9f); moveTo(5f, 13f); arcTo(8f, 8f, 0f, true, false, 19f, 17f) }
private val bellIcon = lineIcon("bell") { moveTo(6f, 16f); quadTo(7f, 14f, 7f, 10f); arcTo(5f, 5f, 0f, false, true, 17f, 10f); quadTo(17f, 14f, 18f, 16f); close(); moveTo(10f, 20f); quadTo(12f, 22f, 14f, 20f) }
private val gearIcon = lineIcon("settings") { moveTo(12f, 8f); arcTo(4f, 4f, 0f, true, true, 11.99f, 8f); moveTo(12f, 2.5f); verticalLineTo(5f); moveTo(12f, 19f); verticalLineTo(21.5f); moveTo(2.5f, 12f); horizontalLineTo(5f); moveTo(19f, 12f); horizontalLineTo(21.5f); moveTo(5.3f, 5.3f); lineTo(7f, 7f); moveTo(17f, 17f); lineTo(18.7f, 18.7f); moveTo(18.7f, 5.3f); lineTo(17f, 7f); moveTo(7f, 17f); lineTo(5.3f, 18.7f) }
private val cloudIcon = lineIcon("cloud-download") { moveTo(7f, 18f); horizontalLineTo(18f); arcTo(4f, 4f, 0f, false, false, 18f, 10f); quadTo(16f, 4f, 10f, 6f); quadTo(6f, 5f, 5f, 10f); arcTo(4f, 4f, 0f, false, false, 7f, 18f); moveTo(12f, 11f); verticalLineTo(20f); moveTo(9f, 17f); lineTo(12f, 20f); lineTo(15f, 17f) }
private val saveIcon = lineIcon("save") { moveTo(5f, 3f); horizontalLineTo(17f); lineTo(21f, 7f); verticalLineTo(21f); horizontalLineTo(3f); verticalLineTo(3f); close(); moveTo(7f, 3f); verticalLineTo(9f); horizontalLineTo(16f); verticalLineTo(3f); moveTo(7f, 21f); verticalLineTo(14f); horizontalLineTo(17f); verticalLineTo(21f) }
private val shareIcon = lineIcon("share") { moveTo(12f, 16f); verticalLineTo(4f); moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f); moveTo(5f, 13f); verticalLineTo(21f); horizontalLineTo(19f); verticalLineTo(13f) }
private val shieldIcon = lineIcon("shield") { moveTo(12f, 3f); lineTo(20f, 6f); verticalLineTo(12f); quadTo(20f, 18f, 12f, 22f); quadTo(4f, 18f, 4f, 12f); verticalLineTo(6f); close(); moveTo(8f, 12f); lineTo(11f, 15f); lineTo(16f, 9f) }
private val playIcon = lineIcon("play") { moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f); moveTo(10f, 8f); lineTo(17f, 12f); lineTo(10f, 16f); close() }
private val sendIcon = lineIcon("send") { moveTo(3f, 11f); lineTo(21f, 3f); lineTo(13f, 21f); lineTo(10f, 14f); close(); moveTo(10f, 14f); lineTo(21f, 3f) }
private val bookIcon = lineIcon("book") { moveTo(4f, 5f); quadTo(8f, 3f, 12f, 6f); verticalLineTo(21f); quadTo(8f, 18f, 4f, 20f); close(); moveTo(20f, 5f); quadTo(16f, 3f, 12f, 6f); verticalLineTo(21f); quadTo(16f, 18f, 20f, 20f); close() }
private val moreIcon = lineIcon("more") { moveTo(5f, 12f); lineTo(5.01f, 12f); moveTo(12f, 12f); lineTo(12.01f, 12f); moveTo(19f, 12f); lineTo(19.01f, 12f) }
private val genericIcon = lineIcon("circle") { moveTo(12f, 3f); arcTo(9f, 9f, 0f, true, true, 11.99f, 3f) }

object Icons {
    object Filled {
        val Add = plusIcon; val Remove = minusIcon; val Close = closeIcon; val Check = checkIcon
        val DateRange = calendarIcon; val CalendarMonth = calendarIcon; val Today = calendarIcon
        val ViewWeek = calendarIcon; val EventNote = calendarIcon; val Schedule = clockIcon
        val Delete = trashIcon; val DeleteForever = trashIcon; val Edit = editIcon
        val Person = userIcon; val Groups = userIcon; val LocationOn = pinIcon
        val Refresh = refreshIcon; val Notifications = bellIcon; val NotificationsActive = bellIcon
        val Settings = gearIcon; val CloudDownload = cloudIcon; val Save = saveIcon; val IosShare = shareIcon
        val Security = shieldIcon; val BatterySaver = shieldIcon; val PlayCircle = playIcon; val Send = sendIcon
        val Brightness6 = genericIcon; val Palette = genericIcon; val FormatAlignLeft = listIcon
        val FilterList = listIcon; val RadioButtonUnchecked = genericIcon; val CheckCircle = checkIcon
        val BookOpen = bookIcon; val MoreHorizontal = moreIcon
    }
    object AutoMirrored { object Filled { val ArrowBack = backIcon; val KeyboardArrowLeft = backIcon; val KeyboardArrowRight = nextIcon; val List = listIcon } }
}
