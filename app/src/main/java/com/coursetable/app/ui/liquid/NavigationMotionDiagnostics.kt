package com.coursetable.app.ui.liquid

import android.os.Build
import android.os.Trace
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Opt-in instrumentation hook. No frame observers are installed during normal use. */
internal object NavigationMotionDiagnostics {
    var listener: ((String, List<Float>, Long) -> Unit)? by mutableStateOf(null)

    fun event(name: String, values: List<Float> = emptyList()) {
        val observer = listener ?: return
        observer(name, values, System.nanoTime())
        if (Build.VERSION.SDK_INT >= 29 && Trace.isEnabled()) {
            if (values.isEmpty()) {
                Trace.beginSection("CourseTable.$name")
                Trace.endSection()
            } else {
                values.forEachIndexed { index, value ->
                    Trace.setCounter("CourseTable.$name.$index", (value * 10_000).toLong())
                }
            }
        }
    }
}
