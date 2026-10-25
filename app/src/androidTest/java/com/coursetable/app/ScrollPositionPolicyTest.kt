package com.coursetable.app

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.coursetable.app.ui.*
import com.coursetable.app.ui.liquid.Text
import com.coursetable.app.ui.theme.CourseTableTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScrollPositionPolicyTest {
    @get:Rule val compose = createComposeRule()
    private var timetable by mutableLongStateOf(1L)
    private var mounted by mutableStateOf(true)
    private var rows by mutableIntStateOf(80)
    private var resets by mutableStateOf(TimetableScrollResets())
    private lateinit var lazy: LazyListState
    private lateinit var scroll: ScrollState
    private lateinit var draft: MutableState<String>

    @Composable private fun Lists(context: String) {
        val list = rememberContextLazyListState(context)
        val column = rememberContextScrollState(context)
        val editing = rememberSaveable { mutableStateOf("未保存的课程草稿") }
        SideEffect { lazy = list; scroll = column; draft = editing }
        Column {
            LazyColumn(Modifier.height(180.dp).fillMaxWidth(), state = list) {
                items(rows) { Text("课程 $it", Modifier.height(40.dp)) }
            }
            Column(Modifier.height(180.dp).fillMaxWidth().verticalScroll(column)) {
                repeat(rows) { Text("节次 $it", Modifier.height(40.dp)) }
            }
        }
    }

    private fun showRoot(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CourseTableTheme {
                val revisions by resets.revisions.collectAsState()
                val context = rememberTimetableScrollKey(timetable, revisions[timetable].orEmpty())
                val holder = rememberSaveableStateHolder()
                if (mounted) holder.SaveableStateProvider("root-page") { Lists(context) }
            }
        }
        return restoration
    }

    private fun moveDown() {
        compose.runOnIdle {
            runBlocking { lazy.scrollToItem(18, 11); scroll.scrollTo(240) }
            draft.value = "修改后尚未保存"
        }
    }

    private fun assertPosition(index: Int, offset: Int, pixels: Int) = compose.runOnIdle {
        assertEquals(index, lazy.firstVisibleItemIndex)
        assertEquals(offset, lazy.firstVisibleItemScrollOffset)
        assertEquals(pixels, scroll.value)
        assertEquals("修改后尚未保存", draft.value)
    }

    @Test fun rootPositionsAndDraftSurviveTabReturnAndStateRestoration() {
        val restoration = showRoot()
        moveDown()
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { mounted = true }
        assertPosition(18, 11, 240)
        restoration.emulateSavedInstanceStateRestore()
        assertPosition(18, 11, 240)
    }

    @Test fun switchingAwayAndBackWhilePageIsUnmountedDoesNotRestoreAnOldPosition() {
        showRoot()
        moveDown()
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { timetable = 2L }
        compose.runOnIdle { timetable = 1L }
        compose.runOnIdle { mounted = true }
        assertPosition(0, 0, 0)
    }

    @Test fun overwriteResetsOnlyTheAffectedTableEvenWhenItsPageIsUnmounted() {
        showRoot()
        moveDown()
        compose.runOnIdle { mounted = false; resets.onOverwriteCommitted(2L) }
        compose.runOnIdle { mounted = true }
        assertPosition(18, 11, 240)
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { resets.onOverwriteCommitted(1L) }
        compose.runOnIdle { mounted = true }
        assertPosition(0, 0, 0)
    }

    @Test fun aMountedTableResetsItsPositionWithoutClearingDraftState() {
        showRoot()
        moveDown()
        compose.runOnIdle { timetable = 2L }
        assertPosition(0, 0, 0)
        moveDown()
        compose.runOnIdle { resets.onOverwriteCommitted(2L) }
        assertPosition(0, 0, 0)
    }

    @Test fun aNewProcessCannotReuseAnEarlierOverwriteContext() {
        resets.onOverwriteCommitted(1L)
        showRoot()
        moveDown()
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { resets = TimetableScrollResets().apply { onOverwriteCommitted(1L) } }
        compose.runOnIdle { mounted = true }
        assertPosition(0, 0, 0)
    }

    @Test fun ordinaryContentUpdatesKeepThePosition() {
        showRoot()
        moveDown()
        compose.runOnIdle { rows++ }
        assertPosition(18, 11, 240)
    }

    @Test fun reenteringASecondaryPageDuringItsExitResetsOnlyItsScroll() {
        var destination by mutableStateOf<String?>("about")
        var renderChild by mutableStateOf(true)
        lateinit var parent: ScrollState
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CourseTableTheme {
            val visit = rememberPageScrollVisit(destination)
            val parentPosition = rememberScrollState()
            SideEffect { parent = parentPosition }
            Column {
                Column(Modifier.height(100.dp).verticalScroll(parentPosition)) {
                    repeat(60) { Text("设置项 $it", Modifier.height(40.dp)) }
                }
                if (renderChild) CompositionLocalProvider(LocalPageScrollKey provides visit) {
                    val childColumn = rememberPageScrollState()
                    val childList = rememberPageLazyListState()
                    val editing = rememberSaveable { mutableStateOf("修改后尚未保存") }
                    SideEffect { scroll = childColumn; lazy = childList; draft = editing }
                    LazyColumn(Modifier.height(100.dp), state = childList) {
                        items(60) { Text("方案 $it", Modifier.height(40.dp)) }
                    }
                    Column(Modifier.height(100.dp).verticalScroll(childColumn)) {
                        repeat(60) { Text("关于 $it", Modifier.height(40.dp)) }
                    }
                }
            }
        } }
        moveDown()
        compose.runOnIdle { runBlocking { parent.scrollTo(180) } }
        restoration.emulateSavedInstanceStateRestore()
        assertPosition(18, 11, 240)
        // A cancelled predictive back keeps the same destination/visit.
        compose.runOnIdle { destination = "about" }
        assertPosition(18, 11, 240)
        // The outgoing page remains composed until its exit animation finishes.
        compose.runOnIdle { destination = null }
        assertPosition(18, 11, 240)
        compose.runOnIdle { destination = "about" }
        assertPosition(0, 0, 0)
        compose.runOnIdle { assertEquals(180, parent.value); renderChild = false }
    }
}
