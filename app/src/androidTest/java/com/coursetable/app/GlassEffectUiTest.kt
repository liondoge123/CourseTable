package com.coursetable.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.coursetable.app.data.GlassEffect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlassEffectUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectingBothPresetsUpdatesTheSettingsAndSurvivesRecreation() = runBlocking {
        val repository = (compose.activity.application as CourseApp).settingsRepository
        val original = repository.settings.first()
        try {
            compose.onAllNodesWithText("设置")[0].performClick()
            for (effect in listOf(GlassEffect.CLEAR, GlassEffect.STANDARD)) {
                compose.onNodeWithTag("settings-choice-glass-effect").performScrollTo().performClick()
                compose.onNodeWithTag("glass-effect-${effect.key}").performClick()
                compose.waitUntil(5000) { runBlocking { repository.settings.first().glassEffect == effect.key } }
                compose.onNodeWithTag("settings-choice-glass-effect")
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, effect.label))
                compose.activityRule.scenario.recreate()
                compose.onNodeWithTag("settings-choice-glass-effect").performScrollTo()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, effect.label))
                assertEquals(original.wallpaper, repository.settings.first().wallpaper)
            }
        } finally { repository.save(glassEffect = original.glassEffect) }
    }
}
