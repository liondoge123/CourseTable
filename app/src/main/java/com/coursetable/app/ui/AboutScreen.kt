package com.coursetable.app.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coursetable.app.update.GithubReleaseCheck
import com.coursetable.app.update.UpdateCheckResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.coursetable.app.R
import com.coursetable.app.ui.icons.Icons
import com.coursetable.app.ui.liquid.*
import com.coursetable.app.ui.theme.LiquidTheme as MaterialTheme
import com.kyant.shapes.Capsule

private const val GITHUB_REPO_URL = "https://github.com/liondoge123/CourseTable"
private const val GITHUB_ISSUES_URL = "https://github.com/liondoge123/CourseTable/issues"
private const val GITHUB_RELEASES_URL = "https://github.com/liondoge123/CourseTable/releases"
private const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

@Composable
fun AboutScreen(
    appVersionName: String,
    onBack: () -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val appVersionCode = remember(context) { getAppVersionCode(context) }
    val appIconBitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val sizePx = (72 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            drawable.toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }

    fun openUrlOrCopy(url: String, label: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
            clipboard.setText(AnnotatedString(url))
            Toast.makeText(context, "$label 链接已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkForUpdate() {
        if (updateStatus is UpdateStatus.Checking) return
        updateStatus = UpdateStatus.Checking
        scope.launch {
            val status = when (val result = GithubReleaseCheck.check(appVersionName)) {
                UpdateCheckResult.UpToDate -> UpdateStatus.UpToDate
                is UpdateCheckResult.UpdateAvailable ->
                    UpdateStatus.Available(result.latestVersion, result.releaseUrl)
                is UpdateCheckResult.Failed -> UpdateStatus.Failed(result.message)
            }
            updateStatus = status
            val message = when (status) {
                UpdateStatus.UpToDate -> "已是最新版本"
                is UpdateStatus.Available -> "发现新版本 v${status.versionName}"
                is UpdateStatus.Failed -> status.message
                else -> return@launch
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val checkingForUpdate = updateStatus is UpdateStatus.Checking
    val updateLabel = when (val status = updateStatus) {
        UpdateStatus.Idle -> "检查更新"
        UpdateStatus.Checking -> "正在检查"
        UpdateStatus.UpToDate -> "已是最新版本"
        is UpdateStatus.Available -> "查看新版本 v${status.versionName}"
        is UpdateStatus.Failed -> "检查失败，点此重试"
    }

    LiquidScrollablePage(Modifier.testTag("about-page"), topBar = {
        LiquidPageTopBar("关于 CourseTable", onBack)
    }) { pageInsets ->
            val direction = LocalLayoutDirection.current
            val contentPadding = pageInsets.pagePadding(extraBottom = bottomContentPadding)
            Column(
                Modifier
                    .fillMaxSize()
                    // Reserve the fixed header outside the scroll viewport.
                    .padding(top = pageInsets.calculateTopPadding())
                    .testTag("about-scroll")
                    .verticalScroll(rememberPageScrollState())
                    .padding(
                        start = contentPadding.calculateStartPadding(direction),
                        top = 16.dp,
                        end = contentPadding.calculateEndPadding(direction),
                        bottom = contentPadding.calculateBottomPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Logo + App Name + Version + Slogan
                SectionFrame {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap,
                                contentDescription = "CourseTable 应用图标",
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_background),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = "CourseTable 应用图标",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Text(
                            text = "CourseTable 课程表",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Surface(
                                shape = Capsule(),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = "v$appVersionName (b$appVersionCode)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = updateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (checkingForUpdate) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !checkingForUpdate) {
                                        val available = updateStatus as? UpdateStatus.Available
                                        if (available != null) {
                                            openUrlOrCopy(available.releaseUrl, "新版本")
                                        } else {
                                            checkForUpdate()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("check-update")
                            )
                        }

                        Text(
                            text = "轻量精致 · 本地优先 · 玻璃拟态设计",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Section 1: Project & Community
                PageSectionTitle("项目与开源")
                SectionFrame {
                    Column {
                        AboutActionItem(
                            icon = Icons.Filled.Code,
                            title = "GitHub 开源仓库",
                            subtitle = "liondoge123/CourseTable",
                            trailingText = "访问仓库",
                            onClick = { openUrlOrCopy(GITHUB_REPO_URL, "GitHub 仓库") },
                            onLongClick = {
                                clipboard.setText(AnnotatedString(GITHUB_REPO_URL))
                                Toast.makeText(context, "仓库地址已复制", Toast.LENGTH_SHORT).show()
                            }
                        )
                        AboutDivider()
                        AboutActionItem(
                            icon = Icons.Filled.Info,
                            title = "问题反馈与建议",
                            subtitle = "向开发者提交 Bug 或新功能建议",
                            trailingText = "Issues",
                            onClick = { openUrlOrCopy(GITHUB_ISSUES_URL, "Issue 反馈") }
                        )
                        AboutDivider()
                        AboutActionItem(
                            icon = Icons.Filled.CloudDownload,
                            title = "版本发布与更新日志",
                            subtitle = "查看历史版本记录与更新说明",
                            trailingText = "Releases",
                            onClick = { openUrlOrCopy(GITHUB_RELEASES_URL, "Releases") }
                        )
                        AboutDivider()
                        AboutActionItem(
                            icon = Icons.Filled.Security,
                            title = "开源许可",
                            subtitle = "Apache License 2.0",
                            trailingText = "查看",
                            onClick = { openUrlOrCopy(APACHE_LICENSE_URL, "许可证") }
                        )
                    }
                }

                // Section 4: Privacy & Features
                PageSectionTitle("特性与设计理念")
                SectionFrame {
                    Column {
                        AboutInfoItem(
                            icon = Icons.Filled.Save,
                            title = "本地离线优先 · 隐私安全",
                            description = "所有课表、时间偏好完全存储在本地 SQLite/Room 数据库中，无中心服务器收集，课表数据完全属于您自己。"
                        )
                        AboutDivider()
                        AboutInfoItem(
                            icon = Icons.Filled.CheckCircle,
                            title = "纯净体验 · 零广告追踪",
                            description = "完全开源透明，无内置广告推广，无第三方用户画像追踪与分析 SDK。"
                        )
                        AboutDivider()
                        AboutInfoItem(
                            icon = Icons.Filled.IosShare,
                            title = "日历生态互通",
                            description = "支持导出标准 RFC 5545 iCalendar (ICS) 日历，与系统日历、其他日历软件无缝连接；支持教务系统与 PDF 智能导入。"
                        )
                    }
                }

                // Section 5: Third-Party Libraries
                PageSectionTitle("第三方开源库与致谢")
                SectionFrame {
                    Column {
                        LibraryCreditItem(
                            name = "Backdrop & Kyant Shapes",
                            license = "Apache 2.0",
                            copyright = "© Kyant",
                            description = "Liquid Glass 拟态玻璃渲染引擎与平滑圆角几何支持"
                        )
                        AboutDivider(inset = 16.dp)
                        LibraryCreditItem(
                            name = "Lucide Icons",
                            license = "ISC",
                            copyright = "© Lucide Contributors",
                            description = "精美一致的轻量级矢量轮廓图标库"
                        )
                        AboutDivider(inset = 16.dp)
                        LibraryCreditItem(
                            name = "AndroidX & Jetpack Compose",
                            license = "Apache 2.0",
                            copyright = "© Google / AOSP",
                            description = "现代 Android 声明式 UI 工具集与核心架构组件"
                        )
                        AboutDivider(inset = 16.dp)
                        LibraryCreditItem(
                            name = "Room & DataStore",
                            license = "Apache 2.0",
                            copyright = "© Google / AOSP",
                            description = "设备端高效本地关系型数据库与轻量偏好存储引擎"
                        )
                        AboutDivider(inset = 16.dp)
                        LibraryCreditItem(
                            name = "PDFBox-Android",
                            license = "Apache 2.0",
                            copyright = "© Tom Roush / Apache Software Foundation",
                            description = "高效可靠的 PDF 课表文档智能解析引擎"
                        )
                        AboutDivider(inset = 16.dp)
                        LibraryCreditItem(
                            name = "ML Kit Text Recognition",
                            license = "Android SDK License",
                            copyright = "© Google LLC",
                            description = "设备端离线智能 OCR 课程表图像识别"
                        )
                        AboutDivider(inset = 16.dp)
                        LibraryCreditItem(
                            name = "OkHttp & Jsoup",
                            license = "Apache 2.0 / MIT",
                            copyright = "© Square, Inc. & Jonathan Hedley",
                            description = "网络请求传输与教务系统 HTML 页面安全解析"
                        )
                        AboutDivider(inset = 16.dp)
                        LibraryCreditItem(
                            name = "ONNX Runtime & PaddleOCR",
                            license = "MIT / Apache 2.0",
                            copyright = "© Microsoft & PaddlePaddle Authors",
                            description = "PP-OCRv6 Tiny 端侧推理模型与跨平台运行时环境"
                        )
                    }
                }

                // Footer
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "CourseTable · Apache-2.0 License",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Copyright © 2026 liondoge123",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
    }
}

@Composable
private fun AboutDivider(inset: androidx.compose.ui.unit.Dp = ListItemDefaults.TextInset) {
    HorizontalDivider(Modifier.padding(start = inset, end = 16.dp))
}

@Composable
private fun AboutActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingText: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListItemIcon(icon)
        Spacer(Modifier.width(ListItemDefaults.IconGap))
        Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
        if (trailingText != null) {
            Surface(
                shape = Capsule(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
            ) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        ListItemChevron()
    }
}

@Composable
private fun AboutInfoItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        ListItemIcon(icon, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(ListItemDefaults.IconGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun LibraryCreditItem(
    name: String,
    license: String,
    copyright: String,
    description: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                shape = Capsule(),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Text(
                    license,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Text(
            copyright,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val versionName: String, val releaseUrl: String) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

private fun getAppVersionCode(context: Context): Long {
    return try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
    } catch (_: Exception) {
        166L
    }
}
