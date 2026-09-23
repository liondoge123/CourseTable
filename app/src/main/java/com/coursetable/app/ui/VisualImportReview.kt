package com.coursetable.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coursetable.app.data.AppSettings
import com.coursetable.app.importer.*
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme
import com.kyant.shapes.RoundedRectangle
import java.io.File

@Composable
fun VisualImportReview(session: VisualImportSession, settings: AppSettings, candidates: List<CandidateCourse>, onCandidates: (List<CandidateCourse>) -> Unit, onEdit: (Int) -> Unit, onAdd: (String?) -> Unit, onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit, saving: Boolean = false, hasEdits: Boolean = false, onReselect: (() -> Unit)? = null) {
    UnifiedImportReview(session, settings, candidates, if (session.sourceImage != null) "图片课表" else "PDF 课表", session.result.warnings, onCandidates, onDismiss, onConfirm, saving, hasEdits, onReselect)
}

private fun VisualImportSession.originalFiles(): List<File> = sourceImage?.let { listOf(it.preview) } ?: pages.map { it.image }

@Composable
private fun ImportImageViewerSheet(
    bitmap: Bitmap?,
    title: String,
    imageTag: String,
    onDismiss: () -> Unit,
    pageCount: Int = 1,
    pageIndex: Int = 0,
    onPageChange: (Int) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val dismissController = LocalDialogDismissController.current
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
            PageHeader(title, "双指缩放，拖动查看") {
                TextButton(onClick = { dismissController?.dismiss() ?: onDismiss() }) { Text("关闭") }
            }
            if (pageCount > 1) Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onPageChange(pageIndex - 1) }, enabled = pageIndex > 0) { Text("上一页") }
                Text("${pageIndex + 1}/$pageCount")
                TextButton(onClick = { onPageChange(pageIndex + 1) }, enabled = pageIndex < pageCount - 1) { Text("下一页") }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .clip(RoundedRectangle(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null && !bitmap.isRecycled) {
                    ImportImageViewport(bitmap, Modifier.fillMaxSize().testTag(imageTag))
                } else Text("正在读取来源图片…")
            }
        }
    }
}

@Composable
internal fun ImportSourceViewer(files: List<File>, title: String, onDismiss: () -> Unit) {
    var page by remember(files) { mutableIntStateOf(0) }
    val bitmap = files.getOrNull(page)?.let { rememberReviewBitmap(it) }
    ImportImageViewerSheet(bitmap, title, "import-source-image", onDismiss, files.size, page) { page = it }
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
    val regionBitmap = if (page != null && region != null) rememberReviewBitmap(page.image, region) else null
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
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                val imageHeight = if (compact && regionBitmap != null) (maxWidth * regionBitmap.height / regionBitmap.width.coerceAtLeast(1)).coerceIn(32.dp, if (keyboardVisible) 48.dp else 96.dp) else if (compact) 48.dp else 160.dp
                Box(Modifier.fillMaxWidth().height(imageHeight).clickable { enlarged = true }, contentAlignment = Alignment.Center) {
                    if (regionBitmap != null && !regionBitmap.isRecycled) androidx.compose.foundation.Image(regionBitmap.asImageBitmap(), "对应原图片段，点击放大", Modifier.fillMaxSize()) else CircularProgressIndicator(Modifier.size(22.dp))
                }
                }
                if (!compact) TextButton(onClick = { enlarged = true }) { Text("放大片段") }
                else Text("点击片段放大", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
            } else Text("此记录没有对应的原图片段，可查看完整原图", style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.onSurfaceVariant)
            if (!compact) candidate.reviewIssues(settings).forEach { Text(it, style = LiquidTheme.typography.bodySmall, color = LiquidTheme.colorScheme.error) }
    }
    if (full) ImportSourceViewer(session.originalFiles(), "完整原图", { full = false })
    if (enlarged && page != null && region != null) {
        ImportImageViewerSheet(regionBitmap, "原图片段", "import-fragment-image", { enlarged = false })
    }
}
