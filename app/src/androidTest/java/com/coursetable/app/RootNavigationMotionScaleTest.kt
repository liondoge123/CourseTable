package com.coursetable.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.coursetable.app.ui.RootDestination
import com.coursetable.app.ui.RootNavigationHost
import com.coursetable.app.ui.RootNavigationState
import com.coursetable.app.ui.rememberRootNavigationState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RootNavigationMotionScaleTest {
    private val motionScale = object : MotionDurationScale {
        var factor = 1f
        override val scaleFactor: Float get() = factor
    }
    @get:Rule val compose = createComposeRule(effectContext = motionScale)
    private lateinit var navigation: RootNavigationState

    private fun show(scale: Float) {
        motionScale.factor = scale
        compose.setContent {
            navigation = rememberRootNavigationState(RootDestination.IMPORT)
            RootNavigationHost(navigation, true) { Box(Modifier.fillMaxSize()) }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test fun disablingMotionAlignsBothClicksAndReleasedSwipesImmediately() {
        show(0f)
        compose.runOnIdle { navigation.navigateTo(RootDestination.COURSES) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertFalse(navigation.isTransitioning)
            assertEquals(RootDestination.COURSES, navigation.settledDestination)
            navigation.beginDrag()
            navigation.dragBy(0.65f)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { navigation.endDrag(0f, 1f) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle {
            assertFalse(navigation.isTransitioning)
            assertEquals(RootDestination.IMPORT, navigation.settledDestination)
        }
    }

    @Test fun increasedSystemDurationScaleSlowsTheVisibleSettle() {
        show(2f)
        compose.runOnIdle { navigation.beginDrag(); navigation.dragBy(0.65f) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnIdle { navigation.endDrag(0f, 1f) }
        compose.mainClock.advanceTimeBy(180)
        compose.runOnIdle {
            assertTrue(navigation.isTransitioning)
            assertTrue("The system's 2x setting must slow the visible page movement",
                navigation.transition.fraction in 0.78f..0.9f)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(RootDestination.SETTINGS, navigation.settledDestination) }
    }
}
