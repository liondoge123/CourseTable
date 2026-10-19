package com.coursetable.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.coursetable.app.importer.TableVisualEvidence
import com.coursetable.app.importer.OcrImagePreprocessor
import org.junit.Assert.*
import org.junit.Test

class OcrGeometryTest {
    @Test fun faintRulesHaveSameBoundariesAsDarkRules() {
        for (shade in listOf(40, 240, 250)) {
            val bitmap = Bitmap.createBitmap(400, 180, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint().apply { color = Color.rgb(shade, shade, shade) }
                canvas.drawRect(10f, 50f, 390f, 52f, paint)
                canvas.drawRect(10f, 120f, 390f, 122f, paint)
                val edges = TableVisualEvidence.horizontalEdges(bitmap)
                assertEquals("shade=$shade", 2, edges.size)
                assertEquals(129.5f, edges[0], .1f)
                assertEquals(59.5f, edges[1], .1f)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun uniformPaleBackgroundDoesNotCreateRows() {
        val bitmap = Bitmap.createBitmap(400, 180, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.rgb(240, 240, 240))
            assertTrue(TableVisualEvidence.horizontalEdges(bitmap).isEmpty())
        } finally { bitmap.recycle() }
    }

    @Test fun faintVerticalRulesLocateNameColumn() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { color = Color.rgb(250, 250, 250) }
            canvas.drawRect(60f, 10f, 62f, 290f, paint)
            canvas.drawRect(220f, 10f, 222f, 290f, paint)
            assertEquals(listOf(60.5f, 220.5f), TableVisualEvidence.verticalEdges(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun repeatedImageRequiresEveryPixelToMatch() {
        val bitmap = Bitmap.createBitmap(80, 360, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) bitmap.setPixel(x, y,
                Color.rgb((y % 120) * 2, x * 3, (y % 120 + x) % 255))
            assertEquals(120, OcrImagePreprocessor.exactRepeatedHeight(bitmap))
            bitmap.setPixel(3, 254, Color.RED)
            assertNull(OcrImagePreprocessor.exactRepeatedHeight(bitmap))
        } finally { bitmap.recycle() }
    }

    @Test fun paleTextStillSplitsIntoSeparateLines() {
        for (shade in listOf(40, 210)) {
            val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint().apply { color = Color.rgb(shade, shade, shade) }
                for (top in listOf(15f, 60f)) for (left in listOf(20f, 45f, 70f, 95f))
                    canvas.drawRect(left, top, left + 4, top + 15, paint)
                assertEquals(2, OcrImagePreprocessor.inkLines(bitmap, android.graphics.Rect(0, 0, 200, 100)).size)
            } finally { bitmap.recycle() }
        }
    }
}
