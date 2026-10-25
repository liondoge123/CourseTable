package com.coursetable.app

import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.coursetable.app.data.Course
import com.coursetable.app.ui.CourseDraftState
import com.coursetable.app.ui.CourseEditorDialog
import com.coursetable.app.ui.CourseManageScreen
import com.coursetable.app.ui.TimetableViewModel
import com.coursetable.app.ui.liquid.LiquidBackdropHost
import com.coursetable.app.ui.liquid.LocalGlassCapability
import com.coursetable.app.ui.liquid.GlassCapability
import com.coursetable.app.ui.theme.CourseTableTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseDraftRestorationTest {
    @get:Rule val compose = createComposeRule()
    private fun sample() = Course(name = "草稿课程", teacher = "教师", location = "A101",
        dayOfWeek = 2, startSection = 1, duration = 1, startWeek = 1, endWeek = 18).also {
        it.additionalSessions = listOf(it.copy(dayOfWeek = 4, startSection = 3, location = "B101"))
    }

    @Composable private fun RestorationSurface(content: @Composable () -> Unit) {
        CourseTableTheme { LiquidBackdropHost(Modifier.fillMaxSize()) {
            // These tests exercise state restoration; native/full-material checks are separate.
            CompositionLocalProvider(LocalGlassCapability provides GlassCapability.STATIC) { content() }
        } }
    }

    private fun choose(index: Int) {
        compose.onNodeWithTag("course-session-selector").performScrollTo().performClick()
        compose.onNodeWithTag("course-session-choices", useUnmergedTree = true).performScrollToIndex(index)
        compose.onNodeWithTag("course-session-choice-$index").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("course-session-choice-$index").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun allUnsavedSessionsAndTheSelectedSlotSurviveRestoration() {
        val restoration = StateRestorationTester(compose)
        val course = sample()
        var saved: Course? = null
        restoration.setContent { RestorationSurface {
            CourseEditorDialog(course, 18, 12, {}, { saved = it })
        } }
        compose.onNodeWithTag("course-editor-location").performScrollTo().performTextReplacement("A202")
        choose(1)
        compose.onNodeWithTag("course-editor-location").performScrollTo().performTextReplacement("B202")
        compose.onNodeWithTag("course-session-add").performScrollTo().performClick()
        compose.onNodeWithTag("course-editor-location").performScrollTo().performTextReplacement("C303")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("course-session-selector").performScrollTo().assertTextContains("时段 3 / 3")
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("C303")
        choose(0)
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("A202")
        choose(1)
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("B202")
        compose.onNodeWithContentDescription("保存").performClick()
        compose.waitUntil(5_000) { saved != null }
        val result = requireNotNull(saved)
        assertEquals(setOf("A202", "B202", "C303"),
            (listOf(result) + result.additionalSessions.orEmpty()).map { it.location }.toSet())
    }

    @Test fun restoredDatabaseCourseKeepsUnsavedSessionRemovalInsteadOfReloadingIt() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
        val tableId = runBlocking { app.timetableRepository.create("编辑恢复回归") }
        try {
            val course = runBlocking {
                val draft = sample().copy(timetableId = tableId).also { it.additionalSessions = sample().additionalSessions }
                val id = app.courseRepository.save(draft)
                requireNotNull(app.database.courseDao().byId(id))
            }
            val restoration = StateRestorationTester(compose)
            var saved: Course? = null
            restoration.setContent { RestorationSurface {
                CourseEditorDialog(course, 18, 12, {}, { saved = it })
            } }
            choose(1)
            compose.onNodeWithContentDescription("删除此时段").performScrollTo().performClick()
            compose.onNodeWithText("删除", substring = false).performClick()
            compose.onNodeWithTag("course-editor-location").performScrollTo().performTextReplacement("修改未保存")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("course-session-selector").assertDoesNotExist()
            compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("修改未保存")
            compose.onNodeWithContentDescription("保存").performClick()
            compose.waitUntil(5_000) { saved != null }
            val result = requireNotNull(saved)
            assertEquals("修改未保存", result.location)
            assertEquals(emptyList<Course>(), result.additionalSessions)
        } finally { runBlocking { app.timetableRepository.delete(tableId) } }
    }

    @Test fun managePageRestoresItsOpenEditorAndUnsavedText() {
        val restoration = StateRestorationTester(compose)
        val course = sample().copy(id = 81)
        restoration.setContent { RestorationSurface {
            CourseManageScreen(listOf(course), 18, 12, {}, {}, {}, rootMode = true)
        } }
        compose.onNodeWithTag("managed-course-81").performClick()
        compose.onNodeWithTag("course-editor-name").performTextReplacement("尚未保存的名称")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("course-editor-name").assertTextContains("尚未保存的名称")
        compose.onNodeWithTag("course-editor-location").performScrollTo().assertTextContains("A101")
    }

    @Test fun rootEditorSnapshotSurvivesBundleParcelAndANewViewModel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as CourseApp
        val course = sample().copy(id = 91, timetableId = 12, courseId = 8).also {
            it.additionalSessions = sample().additionalSessions
        }
        val state = SavedStateHandle()
        val store = ViewModelStore()
        fun model(key: String, handle: SavedStateHandle) = ViewModelProvider(store, viewModelFactory {
            initializer { TimetableViewModel(app, handle) }
        })[key, TimetableViewModel::class.java]
        try {
            val restoredValues = compose.runOnIdle {
                val vm = model("original", state)
                vm.openCourseEditor(course)
                val parcel = Parcel.obtain()
                try {
                    val bundle = Bundle().apply { putSerializable("draft", state.get<ArrayList<Any>>("editingCourse")) }
                    parcel.writeBundle(bundle)
                    parcel.setDataPosition(0)
                    @Suppress("DEPRECATION", "UNCHECKED_CAST")
                    (parcel.readBundle(javaClass.classLoader)!!.getSerializable("draft") as ArrayList<Any>)
                } finally { parcel.recycle() }
            }
            compose.runOnIdle {
                val handle = SavedStateHandle(mapOf("editingCourse" to restoredValues))
                val restored = model("restored", handle)
                assertTrue(restored.editorOpen)
                assertEquals(course, restored.editingCourse)
                assertEquals(course.additionalSessions, restored.editingCourse!!.additionalSessions)
                restored.closeEditor()
                assertFalse(model("closed", handle).editorOpen)
            }
            assertEquals(course, CourseDraftState.restore(CourseDraftState.save(course)))
        } finally { compose.runOnIdle { store.clear() } }
    }
}
