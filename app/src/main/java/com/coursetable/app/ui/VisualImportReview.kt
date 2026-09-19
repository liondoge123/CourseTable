package com.coursetable.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme
import java.io.File

@Composable
fun VisualImportReview(session: VisualImportSession, settings: AppSettings, candidates: List<CandidateCourse>, onCandidates: (List<CandidateCourse>) -> Unit, onEdit: (Int) -> Unit, onAdd: (String?) -> Unit, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit, saving: Boolean = false, hasEdits: Boolean = false, onReselect: (() -> Unit)? = null) {
    UnifiedImportReview(session, settings, candidates, if (session.sourceImage != null) "图片课表" else "PDF 课表", session.result.warnings, onCandidates, onDismiss, onConfirm, saving, hasEdits, onReselect)
}

private fun VisualImportSession.originalFiles(): List<File> = sourceImage?.let { listOf(it.preview) } ?: pages.map { it.image }

@Composable
internal fun ImportSourceViewer(files: List<File>, title: String, onDismiss: () -> Unit) {
    var page by remember(files) { mutableIntStateOf(0) }
    val bitmap = files.getOrNull(page)?.let { rememberReviewBitmap(it) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), shape = RectangleShape, color = LiquidTheme.colorScheme.background.copy(alpha = 1f)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                PageHeader(title, "双指缩放，拖动查看") { TextButton(onClick = onDismiss) { Text("关闭") } }
                if (files.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("上一页") }
                    Text("${page + 1}/${files.size}")
                    TextButton(onClick = { page++ }, enabled = page < files.lastIndex) { Text("下一页") }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (bitmap != null && !bitmap.isRecycled) ImportImageViewport(bitmap, Modifier.fillMaxSize().testTag("import-source-image")) else Text("正在读取来源图片…")
                }
            }
        }
    }
}

@Composable
internal fun ImportCourseSource(session: VisualImportSession, candidate: CandidateCourse, settings: AppSettings, compact: Boolean = false) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val regions = session.result.regions.filter { it.id in candidate.sourceRegionIds() }
    var selected by remember(candidate) { mutableIntStateOf(0) }
    var full by remember { mutableStateOf(false) }
    var enlarged by remember { mutableStateOf(false) }
    val region = regions.getOrNull(selected)
    val page = session.pages.firstOrNull { it.index == region?.page }
    FormSectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "原图对照",
                    style = LiquidTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { full = true },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("完整原图", style = LiquidTheme.typography.bodyMedium) }
            }
            if (regions.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected-- }, enabled = selected > 0) { Text("上一个来源") }
                Text("${selected + 1}/${regions.size}")
                TextButton(onClick = { selected++ }, enabled = selected < regions.lastIndex) { Text("下一个来源") }
            }
            if (page != null && region != null) {
                val bitmap = rememberReviewBitmap(page.image, region)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val imageHeight = if (compact && bitmap != null) (maxWidth * bitmap.height / bitmap.width.coerceAtLeast(1)).coerceIn(32.dp, if (keyboardVisible) 48.dp else 96.dp) else if (compact) 48.dp else 160.dp
                Box(Modifier.fillMaxWidth().height(imageHeight).clickable { enlarged = true }, contentAlignment = Alignment.Center) {
                    if (bitmap != null && !bitmap.isRecycled) androidx.compose.foundation.Image(bitmap.asImageBitmap(), "对应原图片段，点击放大", Modifier.fillMaxSize()) else CircularProgressIndicator(Modifier.size(22.dp))
                }
                }
                if (!compact) TextButton(onClick = { enlarged = true }) { Text("放大片段") }
                else Text("点击片段放大", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
            } else Text("此记录没有对应的原图片段，可查看完整原图", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
            if (!compact) candidate.reviewIssues(settings).forEach { Text(it, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error) }
    }
    if (full) ImportSourceViewer(session.originalFiles(), "完整原图", { full = false })
    if (enlarged && page != null && region != null) {
        val bitmap = rememberReviewBitmap(page.image, region)
        Dialog(onDismissRequest = { enlarged = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), shape = RectangleShape, color = LiquidTheme.colorScheme.background.copy(alpha = 1f)) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    PageHeader("原图片段", "双指缩放，拖动查看") { TextButton(onClick = { enlarged = false }) { Text("关闭") } }
                    if (bitmap != null && !bitmap.isRecycled) ImportImageViewport(bitmap, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}
