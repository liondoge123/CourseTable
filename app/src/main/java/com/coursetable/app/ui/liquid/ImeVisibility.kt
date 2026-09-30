package com.coursetable.app.ui.liquid

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager

/** Consumers need visibility transitions, not every pixel of the IME animation. */
@Composable
internal fun rememberImeVisible(insets: WindowInsets = WindowInsets.ime): Boolean {
    val density = LocalDensity.current
    val visible by remember(insets, density) {
        derivedStateOf { insets.getBottom(density) > 0 }
    }
    return visible
}

/** Clear editing focus only after an open keyboard closes, never on initial focus. */
@Composable
internal fun ClearFocusOnImeDismiss(imeVisible: Boolean) {
    val focusManager = LocalFocusManager.current
    var wasVisible by remember { mutableStateOf(imeVisible) }
    LaunchedEffect(imeVisible, focusManager) {
        if (wasVisible && !imeVisible) focusManager.clearFocus()
        wasVisible = imeVisible
    }
}
