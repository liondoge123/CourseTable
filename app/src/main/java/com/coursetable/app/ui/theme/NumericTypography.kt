package com.coursetable.app.ui.theme

import androidx.compose.ui.text.TextStyle

/** Equal digit advances while preserving the surrounding font, size and weight. */
internal fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "'tnum' 1, 'pnum' 0")
