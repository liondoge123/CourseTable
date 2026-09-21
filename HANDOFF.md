# CourseTable 项目交接文档

更新日期：2026-09-21
仓库：`https://github.com/liondoge123/CourseTable`
本地目录：`D:\CourseTable`

## 1. 项目概况

CourseTable 是一个单 Activity、Jetpack Compose 实现的 Android 课程表应用，包名为 `com.coursetable.app`，最低支持 Android 8.0（API 26），目标 Android 14（API 34），编译使用 API 37。

主要能力：

- 周课表展示、切周、单双周筛选、当前时间进度与非本周课程弱化。
- 多课表和课程的新增、编辑、删除及学期配置。
- 教务系统、ICS、Excel/CSV、PDF、图片 OCR 与 JSON 备份导入。
- JSON 备份、ICS 导出、课程提醒及系统权限检查。
- 浅色/深色主题、五种主题色和 Liquid Glass 交互界面。

业务数据由 Room 与 Preferences DataStore 保存。当前未修改数据库 Schema、包名或提醒数据结构；最近一轮在既有图片/PDF 校对能力上补齐了网络、精确闹钟、系统备份和所有文件导入入口的安全边界。

## 2. 当前版本与已验证正式构建

`version.properties`：

- `VERSION_NAME = 1.6.3`
- `VERSION_CODE = 113`
- `LAST_RELEASE_VERSION = 1.6.2`

工作区当前版本是未发布的 `1.6.3`。最近的正式构建仍为 `1.6.2`（versionCode 112）；本轮安全修改只完成了 Preview 验证，没有制作新的正式包，也没有改动上述版本字段。

Android 配置：

- `compileSdk = 37`
- `targetSdk = 34`
- `minSdk = 26`
- 签名证书 SHA-256：`bebf94b3520ca8392225b108d466885c5dec4fcff1389e780b5b32c61a2b76c2`

正式产物位于 `app/build/outputs/apk/release`：

| ABI | 文件 | 字节数 | SHA-256 |
| --- | --- | ---: | --- |
| arm64-v8a | `CourseTable-v1.6.2-arm64-v8a-release.apk` | 21,047,683 | `1c9fda39b05b7b67fc70e3c7e0a368be45f8d409229bec7e75d5fb4187cc82ba` |
| armeabi-v7a | `CourseTable-v1.6.2-armeabi-v7a-release.apk` | 19,704,305 | `1ec7e6a823edc032e46dd3f68fc29298fa5dd899f7dd0d04858f77d75bbbdf40` |
| universal | `CourseTable-v1.6.2-universal-release.apk` | 30,165,906 | `1caee1ca122203e29a17aadcdc8c55c1297128296ca3f041a0c29295b9072ce4` |

根目录有 `1.6.1`、`1.6.2` arm64 正式包及较早生成的 `1.6.3` Preview 副本。`1.6.2` 三种正式包与 `SHA256SUMS.txt` 位于 `app/build/outputs/apk/release`，arm64 包已复核包名 `com.coursetable.app`、版本 1.6.2/112、V2 签名和预期证书。不要将本地 `LAST_RELEASE_VERSION` 当作已完成远端发布；本轮没有上传 GitHub、创建 Tag 或提交代码。

用户要求正式 APK **30 MB 左右**，允许小幅超出，不是严格 30,000,000 字节上限。脚本使用 31,500,000 字节（5% 余量）作为增长防护；当前 universal 为约 30.13 MB。三个正式产物都适用此检查。

## 3. 2026-09-21 安全与兼容性修复（未发布）

### 网络与教务导入

- `AndroidManifest.xml` 不再全局允许明文流量；`res/xml/network_security_config.xml` 默认禁止 HTTP，仅为 `jwxt.ujs.edu.cn` 设置精确域名例外，并只信任系统 CA。
- 江苏大学入口直接打开 `https://pass.ujs.edu.cn/cas/login`，HTTP 教务地址只作为 CAS 回调和课表接口。登录密码由 HTTPS 页面承载，但课表会话 Cookie 与响应经过 HTTP，仍存在同网段窃取的残余风险，学校提供 HTTPS 前无法彻底消除。
- `SchoolAdapter` 现在区分 `loginUrl`、`cookieOrigin`、允许导航主机与允许 HTTP 的主机；自定义正方教务登录页仅接受 HTTPS。
- `EduImportScreen` 禁止 WebView 文件访问、内容访问、混合内容、第三方 Cookie 与多窗口，限制主框架导航域名，显示当前域名/HTTP 风险，退出或成功导入后销毁 WebView 并清理 Cookie、缓存及 Web Storage。

### 精确闹钟与备份

- Manifest 声明 `SCHEDULE_EXACT_ALARM`；设置页增加“精确闹钟”检查和系统授权入口。
- 课程提醒在授权时用 `setExactAndAllowWhileIdle`，未授权或授权被并发撤销时降级为 `setAndAllowWhileIdle`；测试提醒走同一逻辑，不再直接调用 `setAlarmClock`。
- 每日重排改用 30 分钟非精确窗口；开机或重新授予精确闹钟权限后重排。已移除 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 及主动申请白名单流程，它不能替代精确闹钟权限。
- `allowBackup=false`，并同时提供 Android 12+ `dataExtractionRules` 与旧版 `fullBackupContent` 全排除规则。跨设备迁移只使用应用内 JSON 导出/恢复。

### 文件导入安全边界

- 新增 `importer/ImportPolicy.kt` 和 `DetectedImportType`。外部入口只接受 `content://`，Manifest 移除 `file://`/`BROWSABLE`；优先根据文件签名或文本内容识别格式，不信任扩展名和 MIME。
- 大小上限：ICS/JSON/CSV 5 MiB、XLSX 20 MiB、图片 25 MiB、PDF 30 MiB；即使 ContentProvider 不报告长度，也由计数流强制限制。
- PDF 最多 20 页、单页最多 1600 万渲染像素；PDFBox 使用 8 MiB 内存加 128 MiB 临时存储的混合模式。图片源最长边 32,768、总像素不超过 1 亿，实际 OCR 最长边仍降采样到 4096。
- XLSX 最多 256 个 ZIP 条目、64 MiB 解压总量、单个目标 XML 16 MiB、10,000 行、256 列、100,000 单元格；拒绝 DOCTYPE/ENTITY，禁用外部实体、外部 DTD 和 XInclude，并限制异常单元格坐标。
- JSON 最多 20 张课表、总计 2,000 门课程；ICS、CSV、OCR/PDF 输出同样最多 2,000 条；课程名称、教师和地点最多 256 字符。图片 EXIF 改用 AndroidX ExifInterface。
- 解析失败向用户显示稳定的安全错误，不再暴露底层异常类型和消息；所有 PDF/图片临时目录继续在成功、失败和取消时清理。

### 本轮验证

- `:app:testDebugUnitTest`：64 个单元测试通过；新增类型伪造、未知长度超限流、备份上限、XXE、巨大 XLSX 单元格坐标、HTTPS 教务配置和 Manifest/WebView/闹钟安全契约测试。
- `:app:compileDebugAndroidTestKotlin`：通过；当前无连接设备，因此本轮 instrumentation 只完成编译，未执行 Android 11/12/14 真机或模拟器矩阵。
- `:app:lintPreview`、`:app:assemblePreview`：通过；Lint 仍有依赖更新、ML Kit 原生库 16 KiB 对齐等既有警告，没有错误。
- 当前 Gradle Preview 验证产物：`app/build/outputs/apk/preview/CourseTable-v1.6.3-preview-b113-arm64-v8a-preview.apk`，29,638,654 字节，SHA-256 `b8fc7d1b3ea21a0ab459233750832297c8b44bb1d5b47f27136b931648691fd1`。它不是交付包；如需安装测试包，仍须按项目规则运行 `scripts/build-apk.ps1 preview` 重新生成并核验。
- 合并 Preview Manifest 已复核：`allowBackup=false`、无 `file://`、无忽略电池优化权限、包含 `SCHEDULE_EXACT_ALARM`、全局 `usesCleartextTraffic=false` 并引用域名级网络配置。

## 4. v1.5.8 历史主要变更（继续保留）

### 根导航与页面结构

- 底部导航改为五槽结构：课表、课程、中央添加、导入、设置。
- 中央“+”是全局添加课程动作，不是可选中的页面；拖动导航时只落到四个真实目的地。
- 导入从“更多”中拆成独立根页面，设置页重新按“学期与课表 / 课程显示 / 提醒与权限 / 数据与安全”分组。
- 外部分享或打开文件会直接进入导入页；教务系统子页面打开时隐藏底栏。

### Liquid Glass 与交互

- 顶栏胶囊、底栏和通用环境分别使用独立 Backdrop，避免 RenderNode 自取样递归。
- 顶栏胶囊接入 AndroidLiquidGlass 风格的跟手高光、拖动偏移和弹性形变，并显式使用 `LocalTopChromeGlassBackdrop`。
- 弹窗、底部面板和菜单统一为连续曲率玻璃表面；确认/取消操作使用一致的胶囊动作区。
- 设置开关改为支持点击、拖动、速度形变和玻璃滑块的原生 Liquid Toggle。
- 自定义 `LiquidTheme` 全局提供有界触点波纹；显式 `indication = null` 的定制手势组件不受影响。

### 图标与操作语言

- 所有通用图标迁移到 Lucide Icons 1.45.0 官方 SVG，统一为 24×24、2px 圆端描边，并保持现有 `Icons` 门面兼容。
- Lucide ISC 与 Feather MIT 许可文本随 APK 打包在 `app/src/main/assets/licenses/lucide-icons.txt`。
- 导入页三个入口改为等权中性边框。
- 删除动作统一使用垃圾桶语义：第一次进入确认态，第二次执行；不再使用容易误解为“完成”的勾。
- 课程详情底部仅保留“删除”和“编辑”，两者统一使用 13dp 圆角和图标加文字。

## 5. 关键代码与架构约束

- `MainActivity.kt`：根导航、五槽底栏和中央添加动作。
- `ui/liquid/GlassHost.kt`：提供 `LocalGlassBackdrop`、`LocalNavigationGlassBackdrop`、`LocalTopChromeGlassBackdrop`。
- `ui/liquid/GlassSurfaces.kt`：玻璃面板、弹窗表面和顶栏胶囊。
- `ui/liquid/LiquidNavigationTabs.kt`：可拖动底栏、不可选中央动作槽。
- `ui/liquid/LiquidToggle.kt`：Liquid Toggle 点击与拖动实现。
- `ui/theme/LiquidRipple.kt`：设计系统全局波纹。
- `ui/icons/LiquidIcons.kt`：由 Lucide SVG 生成的 Compose `ImageVector`。

必须遵守：

1. `.glassBackdropSource()` 只能锚定在不包含玻璃消费者的底层环境背景上。
2. 底栏读取 `LocalNavigationGlassBackdrop`；课表顶栏读取 `LocalTopChromeGlassBackdrop`，不能合并为同一 RenderNode。
3. `LiquidCapsuleSurface` 用于课表顶栏时必须传 `preferTopChromeBackdrop = true`。
4. 五槽底栏的 `actionIndex = 2` 不属于 `selectableIndices`，中央动作不能写入根目的地状态。
5. 普通文字按钮共享 `LiquidButtonShape`；紧凑纯图标操作才使用圆形。
6. 更新图标时继续从固定版本 Lucide SVG 生成，保留上游名称和许可，不要重新手绘路径。

## 6. v1.6.1 OCR 正式构建验证记录（历史）

正式构建命令：

```powershell
.\scripts\build-apk.ps1 release -VersionName 1.6.1
```

上面的命令是本次构建记录，不能原样重发：脚本已将 `LAST_RELEASE_VERSION` 写为 `1.6.1`，后续正式发布需更高语义版本。本次结果：

- `testDebugUnitTest`：47 个单元测试通过。
- `:app:compileDebugAndroidTestKotlin`：通过。
- `lintDebug`：通过。
- Release R8、资源压缩及三 ABI 构建：通过。
- `aapt dump badging`：包名、版本、minSdk、targetSdk 和 ABI 均通过。
- `apksigner verify`：APK Signature Scheme v2 与预期签名证书通过。
- SHA-256：已写入 `app/build/outputs/apk/release/SHA256SUMS.txt`。
- `StructuredTimetableParserTest`：截图转录结构、分散周次、钟点映射、跨节次背景块、续页和合并区域重识别等逻辑通过。
- Tiny 接入后的原始截图识别、原图校对交互两个针对性模拟器测试通过；局部 OCR 基准实验也已执行。不要误称最后一轮完整五项 instrumentation 全部通过：早期一轮在发现长周次裁剪问题后中断，随后修正并重跑针对性测试。
- 正式 **arm64 v1.6.1** 已安装到模拟器，通过真实 Android 分享入口导入原始截图，成功打开“原图校对 · 14 条上课记录”，该进程未出现导入异常或 Tiny 回退错误。证据：`app/build/benchmarks/release-smoke.xml`。

验证使用本机 `Medium_Phone` Android 14、x86_64 模拟器；尚未在实体 ARM 手机验证，也没有实机运行 armeabi-v7a 包。正式 ARM 包在模拟器上的完整推理耗时较长，不能据此声称实体手机性能已达标。PDF 多页和文字层曾在较早阶段验证，最终 Tiny 集成版的完整多页流程仍建议补验。

完整最终构建日志：`app/build/logs/release-b97.log`。历史失败或中间构建日志不能替代最终验证。

## 7. 构建与发布

环境：

- JBR/JDK 21，例如 `C:\Users\zhangyuchao\.jdks\jbr-21.0.11`
- Android SDK：`D:\android-tools\sdk`
- Android Platform 37 与 Build-Tools 36/37

命令：

```powershell
# arm64 预览包；会递增 versionCode
.\scripts\build-apk.ps1 preview

# 正式发布；完整验证并生成三种 ABI
.\scripts\build-apk.ps1 release
```

必须遵循 `RELEASE.md`：

- 修复或 UI 优化递增 PATCH，兼容功能递增 MINOR，破坏性改动递增 MAJOR。
- 每次已发布构建都必须递增 `versionCode`。
- 正式 APK 命名为 `CourseTable-v{versionName}-{ABI}-release.apk`。
- 发布前必须核对签名、包名、版本、ABI 和 SHA-256。
- 每种正式 APK 目标约 30 MB，防护阈值为 31,500,000 字节；不要为几十 KB 做有风险的 SDK 裁剪。
- `useLegacyPackaging = true` 压缩原生库，Android 安装时解压；下载包体积不等于安装后的存储占用。
- universal 仅含 arm64-v8a、armeabi-v7a，打包时排除 x86/x86_64 原生库；不提供 x86 发布支持。
- 禁止在日志或文档中输出 `keystore.properties` 的密码。

若容器内 Java 报 `Unable to establish loopback connection`，设置：

```powershell
$env:JAVA_HOME='C:\Users\zhangyuchao\.jdks\jbr-21.0.11'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\CourseTable\.unix-sockets'
```

## 8. 后续建议

- 在 Android 12、14 及较新版本真机分别测试精确闹钟已授权、拒绝、撤销后的课程提醒和测试提醒，确认授权广播重排与无权限降级均不会崩溃。
- 实测江苏大学 HTTPS CAS → HTTP 教务回调、Cookie 抓课、异常跨域拦截以及完成/退出后的会话清理；自定义 HTTPS 正方入口如需跨域 SSO，应新增显式学校适配器和主机白名单，不要放宽全局策略。
- 用文件管理器、QQ/微信分享入口验证 `content://` 的 ICS、JSON、CSV、XLSX、PDF、PNG/JPEG/WebP 正常样本，以及过大、伪造 MIME、权限撤销和损坏文件的错误提示。
- 在 arm64 真机执行五槽导航、中央添加、导入子页、弹窗、开关拖动、删除确认和深浅色主题视觉回归。
- 下一次正式发布至少使用 `1.6.3`/versionCode 113 或更高版本，并严格按 `RELEASE.md` 重新构建和核验；不要把当前 Preview 当作正式包。
- 非发布开发优先使用编译与单测；仅在需要安装包时运行 Preview，避免无意义递增 `versionCode`。
- 收集不同学校的周课表、明细表、无线框、合并格、模糊小字与拍照样本，统计课程名、时间/周次和整体导入准确率；一张图拆出 14 条记录不代表任意排版都可靠。
- 在 ARM 真机测识别耗时、峰值内存、连续导入和多页 PDF；保留校对机制，不承诺所有图片全自动准确识别。

## 9. 图片/PDF 导入升级（历史背景）

### 解析与数据流

- `importer/StructuredTimetableParser.kt`：布局分类、明细表按时间列关联课程行、周课表按星期/节次锚点关联课程区域，辅助使用表格线与颜色块。
- `importer/OcrEngine.kt`：ML Kit 元素级坐标 → Tiny 局部文字识别 → 统一 `TextToken` → 结构解析；小字适度放大，缺失或不明确的字段局部重试。裁剪边界包含原文字包围盒，避免长周次列表左侧被表头中点推断裁掉。
- `importer/VisualTimetableImporter.kt`：PDF 优先文字层，否则 OCR，按页解析并缓存预览；续页只在尺寸及数据列位置相容时沿用表头，关闭会话清理缓存、释放 Tiny session。
- `CandidateCourse` 扩展区域关联、待校对及合并前来源信息；`PdfParseResult` 带识别区域，坐标为归一化的左上原点。仍使用原有 `Course` 写入数据库。
- 多次上课、不同地点/时长/单双周分别保留，分散周次拆条；仅相同上课安排的连续周次合并，保留来源，局部重识别不应删除其他页的记录。
- 仅钟点格式按当前作息匹配（起止时间各允许 5 分钟差）；无法可靠匹配不猜节次。未标周次默认当前学期并提示，异常周次/时间、低置信度或双引擎时间冲突进入校对。

### 原图校对

- `ui/VisualImportReview.kt` 与 `ui/ImportScreen.kt`：支持缩放/平移、切页、点击区域关联全部上课安排、补框/重画框、局部重识别、手动补课程、编辑与删除。
- 识别为空也能进入原图补录；缺课程名、星期、节次或不合法范围的记录需补齐或删除才能确认。
- 保留追加/覆盖选择；处理和保存期间禁用冲突操作，保存前再次检查有效性。`CourseTableImport` 日志用于诊断导入异常。

### Tiny OCR 与必须保留的约束

- `importer/TinyTextRecognizer.kt` 在 `CourseApp.onCreate()` 记录 application context，首次识别懒加载模型，串行 session 推理，使用 BGR/CHW 归一化、CTC 解码；不用 OpenCV，也不打包第二套检测/方向模型。
- 推理库固定为 `com.microsoft.onnxruntime:onnxruntime-android:1.21.1`。主包模型：`app/src/main/assets/ocr/PP-OCRv6_rec_tiny.onnx`，4,489,813 字节，SHA-256 `e16e242de5937ad92609223f19bc2aff3727ee40b095f996907c24749bad251b`。
- **必须保留 `-keep class com.google.mlkit.** { *; }` 与 ONNX Runtime 类保留规则。** 删除整个 ML Kit 保留规则后，Debug 测试通过，但正式包分享导入出现 NullPointerException；恢复规则后的 v1.6.1 真实入口验证通过。
- 中间 v1.6.0 正式包未通过运行验证，不要交付；原根目录副本已移入忽略的 `app/build/benchmarks/`，最终使用 v1.6.1。
- 不要将未混淆的 Debug AndroidJUnitRunner 测试 APK直接注入 R8 发布包：会因 Kotlin/应用类混淆不匹配而失败。这是测试工具适配问题，发布版应通过真实入口验证，或另配正式构建测试。
- `NOTICE` 及 `assets/licenses/`、`assets/ocr/NOTICE.txt` 包含 PaddleOCR/RapidOCR Apache-2.0 和 ONNX Runtime MIT 许可说明。

### 评测与工作区

- 详细方法及限制见 `OCR-BENCHMARK.md`。同一原始截图的固定局部裁剪中，ML Kit 课程名主体/时间匹配为 5/11、9/12；Android ML Kit 定位 + Tiny 识别为 11/11、12/12。名字忽略括号等标点，仍存在缺闭括号、房间号或校区文字错误，不能当作全字段 100% 准确。
- 原图有 11 门课程、12 个上课安排；“电力电子技术”两次上课，“中国古代史”第 4、9–10、14–16 周，拆分为 14 条记录。
- 电脑实验脚本：`scripts/benchmark-ocr.py`、`scripts/summarize-ocr-benchmark.py`；Python 环境和检测/方向模型在忽略的 `.cache/ocr-benchmark/`，原始结果在 `app/build/benchmarks/`，不进入 APK。
- Otsu 对比度实验使识别变差，仅留在 `androidTest`，未启用生产增强；截图及预期数据也仅属测试 assets。
- 当前工作仍在未提交工作区，没有提交或推送。本轮安全修改与此前已有的 `TimetableScreen.kt`、`ui/liquid/GlassOverlays.kt`、`version.properties` 修改并存；后续提交前必须检查完整 diff，避免误删或把既有修改误归因到安全修复。
