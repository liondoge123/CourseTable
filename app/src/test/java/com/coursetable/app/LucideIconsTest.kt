package com.coursetable.app

import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.icons.Icons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LucideIconsTest {
    @Test
    fun allExposedIconsUseValidLucideVectors() {
        val icons = listOf(
            Icons.Filled.Add,
            Icons.Filled.Remove,
            Icons.Filled.Close,
            Icons.Filled.Check,
            Icons.Filled.DateRange,
            Icons.Filled.CalendarMonth,
            Icons.Filled.Today,
            Icons.Filled.ViewWeek,
            Icons.Filled.EventNote,
            Icons.Filled.Schedule,
            Icons.Filled.Delete,
            Icons.Filled.DeleteForever,
            Icons.Filled.Edit,
            Icons.Filled.Person,
            Icons.Filled.Groups,
            Icons.Filled.LocationOn,
            Icons.Filled.Refresh,
            Icons.Filled.Notifications,
            Icons.Filled.NotificationsActive,
            Icons.Filled.Settings,
            Icons.Filled.CloudDownload,
            Icons.Filled.Save,
            Icons.Filled.IosShare,
            Icons.Filled.School,
            Icons.Filled.FileImport,
            Icons.Filled.Restore,
            Icons.Filled.Security,
            Icons.Filled.BatterySaver,
            Icons.Filled.PlayCircle,
            Icons.Filled.AutoStart,
            Icons.Filled.Send,
            Icons.Filled.Brightness6,
            Icons.Filled.Palette,
            Icons.Filled.FormatAlignLeft,
            Icons.Filled.Visibility,
            Icons.Filled.Info,
            Icons.Filled.FilterList,
            Icons.Filled.RadioButtonUnchecked,
            Icons.Filled.CheckCircle,
            Icons.Filled.BookOpen,
            Icons.Filled.MoreHorizontal,
            Icons.AutoMirrored.Filled.ArrowBack,
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            Icons.AutoMirrored.Filled.List
        )

        assertEquals(45, icons.size)
        icons.forEach { icon ->
            assertTrue(icon.name.startsWith("lucide-"))
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
        }
    }
}
