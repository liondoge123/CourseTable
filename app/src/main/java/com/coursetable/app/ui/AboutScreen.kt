package com.coursetable.app.ui

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val APACHE_2_SUMMARY = """本项目遵循 Apache License 2.0 开源协议。

• 商业使用：允许商业使用、修改、分发及私人使用。
• 版权声明：分发本软件或其衍生作品时，必须保留原始版权声明与本许可证副本。
• 修改说明：修改过的文件必须带有显著通知，说明您更改了这些文件。
• 免责与担保限制：本软件按“现状”提供，作者与贡献者不承担任何明示或暗示的担保及连带赔偿责任。"""

private const val APACHE_2_FULL_TEXT = """                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.
      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work.

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

   Copyright 2026 liondoge123

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License."""

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

    var showLicenseDialog by remember { mutableStateOf(false) }

    fun openUrlOrCopy(url: String, label: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
            clipboard.setText(AnnotatedString(url))
            Toast.makeText(context, "$label 链接已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
    }

    FullscreenPageContainer(Modifier.testTag("about-page")) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "关于 CourseTable",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = bottomContentPadding),
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

                        // Version Chip
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
                            text = "轻量精致 · 本地优先 · 玻璃拟态设计",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Section 1: Project & Community
                Text(
                    "项目与开源",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
                    }
                }

                // Section 2: License & Compliance (Apache 2.0)
                Text(
                    "开源协议与合规",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                SectionFrame {
                    Column {
                        AboutActionItem(
                            icon = Icons.Filled.Security,
                            title = "Apache License 2.0",
                            subtitle = "Copyright © 2026 liondoge123\n点击查看完整许可证文本与条款",
                            trailingText = "查看条款",
                            onClick = { showLicenseDialog = true }
                        )
                        AboutDivider()
                        // License Permissions Summary
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "许可权利与要求简述",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            LicenseRuleRow("✔", "允许商业使用、自由分发、修改与私有部署")
                            LicenseRuleRow("✔", "授予专利与著作权许可")
                            LicenseRuleRow("ℹ", "修改过的文件必须包含显式的修改说明")
                            LicenseRuleRow("ℹ", "二次分发时必须保留原版权声明与许可证副本")
                        }
                    }
                }

                // Section 3: Legal Disclaimer (Apache 2.0 Section 7 & 8)
                SectionFrame {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "免责与责任限制声明",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "根据 Apache License 2.0 第 7 条（无担保声明）与第 8 条（责任限制）规定：\n\n" +
                                    "本软件及相关文档基于「现状」（AS IS）提供，不附带任何明示或暗示的保证（包括但不限于适销性、特定用途适用性及不侵权保证）。\n\n" +
                                    "在任何情况下，版权持有人或贡献者均不对因使用或无法使用本软件所导致的任何直接、间接、偶发、特殊或衍生损害（包括但不限于数据丢失、业务中断或设备故障）承担任何法律责任。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Section 4: Privacy & Features
                Text(
                    "特性与设计理念",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
                Text(
                    "第三方开源库与致谢",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
                SectionFrame {
                    Column {
                        LibraryCreditItem(
                            name = "Backdrop & Kyant Shapes",
                            license = "Apache 2.0",
                            copyright = "© Kyant",
                            description = "Liquid Glass 拟态玻璃渲染引擎与平滑圆角几何支持"
                        )
                        AboutDivider()
                        LibraryCreditItem(
                            name = "Lucide Icons",
                            license = "ISC",
                            copyright = "© Lucide Contributors",
                            description = "精美一致的轻量级矢量轮廓图标库"
                        )
                        AboutDivider()
                        LibraryCreditItem(
                            name = "AndroidX & Jetpack Compose",
                            license = "Apache 2.0",
                            copyright = "© Google / AOSP",
                            description = "现代 Android 声明式 UI 工具集与核心架构组件"
                        )
                        AboutDivider()
                        LibraryCreditItem(
                            name = "Room & DataStore",
                            license = "Apache 2.0",
                            copyright = "© Google / AOSP",
                            description = "设备端高效本地关系型数据库与轻量偏好存储引擎"
                        )
                        AboutDivider()
                        LibraryCreditItem(
                            name = "PDFBox-Android",
                            license = "Apache 2.0",
                            copyright = "© Tom Roush / Apache Software Foundation",
                            description = "高效可靠的 PDF 课表文档智能解析引擎"
                        )
                        AboutDivider()
                        LibraryCreditItem(
                            name = "ML Kit Text Recognition",
                            license = "Android SDK License",
                            copyright = "© Google LLC",
                            description = "设备端离线智能 OCR 课程表图像识别"
                        )
                        AboutDivider()
                        LibraryCreditItem(
                            name = "OkHttp & Jsoup",
                            license = "Apache 2.0 / MIT",
                            copyright = "© Square, Inc. & Jonathan Hedley",
                            description = "网络请求传输与教务系统 HTML 页面安全解析"
                        )
                        AboutDivider()
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

    // License Dialog
    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Apache License 2.0",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = APACHE_2_SUMMARY,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "官方许可证正文 (Official Text)：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SelectionContainer {
                        Text(
                            text = APACHE_2_FULL_TEXT,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                val dismissController = LocalDialogDismissController.current
                TextButton(
                    onClick = {
                        dismissController?.dismiss { showLicenseDialog = false } ?: run { showLicenseDialog = false }
                    }
                ) {
                    Text("关闭")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(APACHE_2_FULL_TEXT))
                        Toast.makeText(context, "许可证全文已复制", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("复制全文")
                }
            }
        )
    }
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp, end = 16.dp))
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
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
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

@Composable
private fun LicenseRuleRow(badge: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            badge,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
