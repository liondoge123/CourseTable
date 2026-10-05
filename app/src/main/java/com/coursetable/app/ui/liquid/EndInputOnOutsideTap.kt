package com.coursetable.app.ui.liquid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

private class EditingTapScope {
    var touchedInput = false
}

private val LocalEditingTapScope = staticCompositionLocalOf<EditingTapScope?> { null }

/** Observe taps without consuming them, so controls still perform their normal action. */
@Composable
internal fun EndInputOnOutsideTap(content: @Composable (Modifier) -> Unit) {
    val scope = remember { EditingTapScope() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val observer = Modifier.pointerInput(scope, focusManager, keyboard) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Initial pass visits the parent first; text fields mark this gesture below.
            scope.touchedInput = false
            var tap = true
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) break
                if (event.changes.size > 1 ||
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop ||
                    change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                ) tap = false
                if (!change.pressed) {
                    if (tap && !scope.touchedInput &&
                        change.position.x >= 0 && change.position.x < size.width &&
                        change.position.y >= 0 && change.position.y < size.height
                    ) {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                    break
                }
            } while (true)
        }
    }
    CompositionLocalProvider(LocalEditingTapScope provides scope) { content(observer) }
}

/** Protect the whole field, including its label and decoration, during an outside tap. */
@Composable
internal fun Modifier.keepEditingOnTap(): Modifier {
    val scope = LocalEditingTapScope.current ?: return this
    return pointerInput(scope) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            scope.touchedInput = true
        }
    }
}
