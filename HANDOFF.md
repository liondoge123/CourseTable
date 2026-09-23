# CourseTable 项目交接

更新日期：2026-09-23
仓库：[liondoge123/CourseTable](https://github.com/liondoge123/CourseTable)

## 当前状态

- 本次工作整理源码和文档，准备后续正式发布；**尚未生成新的正式 APK、创建版本标签或发布 GitHub Release**。
- `version.properties` 当前为 `VERSION_NAME=1.6.4`、`VERSION_CODE=122`、`LAST_RELEASE_VERSION=1.6.3`。这些字段记录候选源码状态，不能当作已发布 APK 的版本证明。
- 下一次执行 `scripts/build-apk.ps1 release` 会将 `versionCode` 递增到 123，并按 `RELEASE.md` 校验三种 ABI 的正式包。正式发布后再提交脚本更新的版本文件、创建 `v1.6.4` 标签并上传 Release 资产。
- `main` 包含 v1.6.3 的源码历史；GitHub Release 页面与 Git 标签是两种独立状态，发布前应分别核对。

## 本轮源码变化

- **导入确认**：图片识别只显示弹窗进度；校对清单简化顶部信息，来源名称与带数量的筛选标签分开显示，零项分类隐藏，分类清空时回到“全部”。放大后的标签只用于该页面。
- **菜单和抽屉**：课表选择与“更多”改用锚定玻璃菜单，并移除菜单外部阴影。菜单退出后才打开新增课程抽屉；新增标题、保存操作与校对区分。原图、来源片段、重新识别确认和识别范围使用液态玻璃抽屉。
- **背景层级**：选择识别范围时导入确认页保持在抽屉后方；嵌套覆盖层单独采样下方场景，使抽屉上再弹出的窗口仍有玻璃背景。退出原图后保留未保存的课程编辑内容。
- **依赖**：Android Gradle Plugin 调整到 9.4.1；没有数据库迁移、包名变更或公开导入数据结构变更。

导入清单的布局、队列和数据生命周期见 [`docs/IMPORT_REVIEW.md`](docs/IMPORT_REVIEW.md)。

## 架构与使用边界

- 应用是单 Activity、Jetpack Compose 界面；Room 保存课程和课表，Preferences DataStore 保存设置。包名为 `com.coursetable.app`，最低 API 26，目标 API 34。
- 导入入口覆盖教务系统、ICS、Excel/CSV、PDF、图片和 JSON 备份；`ImportPolicy` 对外部内容执行类型、大小及输出数量约束。图片/PDF 的 `ImportFlowState` 持有临时资源，成功、失败或取消后必须关闭会话并清理目录。
- `LiquidBackdropHost` 的覆盖层记录器按层分离。玻璃采样源只能放在不含玻璃消费者的背景层；课表顶栏与底部导航使用各自的 Backdrop。新增嵌套窗口应通过现有 `ModalBottomSheet`、`AlertDialog` 或 `GlassOverlayPortal`，避免实色全屏窗口遮断背景。
- 正式包的 R8 规则必须保留 ML Kit 与 ONNX Runtime 所需类；历史版本曾在删去 ML Kit 保留规则后出现 Debug 正常、正式包导入崩溃的问题。不要用 Debug 测试 APK 直接判断混淆后正式包的运行结果。
- Android 系统自动备份关闭。跨设备迁移使用应用内 JSON 备份；课程提醒在没有精确闹钟授权时降级。江苏大学教务登录从 HTTPS CAS 开始，但学校课表接口仍使用限定域名的 HTTP，存在会话传输风险；不要放宽全局明文网络策略。
- 原图识别会随样本清晰度和排版变化。缺失或冲突字段仍需用户校对，不能把单个截图的成功结果视为普遍准确率。

## 验证与待验证项

- `:app:testDebugUnitTest`：67 个测试通过；`:app:lintDebug` 与 `:app:compileDebugAndroidTestKotlin` 通过。源码提交不代表正式 APK 已通过签名或实体手机验证。
- 已在 Android 14 `Medium_Phone` 模拟器检查导入确认页筛选标签的实际布局；`UnifiedImportReviewTest` 12 项、`VisualImportReviewTest` 10 项和 `OverlayGlassPresentationTest` 2 项通过。Debug 测试使用只在 Debug 构建中声明的 `FileProvider` 提供 `content://` 样本。截图和临时验证文件保留在本地，不进入源码提交。
- 正式发布前仍需在 ARM 设备上验证导入、菜单、抽屉、原图手势与提醒；教务登录需要真实学校环境复核。重点检查深浅色、窄屏、键盘弹出以及连续识别/取消后的资源释放。

## 后续正式发布

1. 以 [`RELEASE.md`](RELEASE.md) 为准，确认语义版本高于 `LAST_RELEASE_VERSION`，并先核对源码与测试结果。
2. 使用 `scripts/build-apk.ps1 release` 生成 arm64-v8a、armeabi-v7a 和 universal 正式 APK。脚本会递增 `versionCode`，执行 JVM 测试、Android 测试代码编译、Lint、R8 构建，并检查包名、SDK、版本、ABI、签名及 SHA-256。
3. 检查三种 APK 均未超过 31,500,000 字节防护阈值，复核 `SHA256SUMS.txt` 与签名证书；不要把本地 Preview 包或 Debug 包当作正式产物。
4. 提交构建脚本更新的 `version.properties`，创建并推送带注释的 `v1.6.4` 标签，再创建 GitHub Release，上传三种 APK 和校验和文件。APK 与本轮新增的本地 `.artifacts/` 不进入 Git 源码提交；仓库里已有的历史验证截图保持原状。

如发布版本发生变化，以上版本号和对应标签须同步调整。签名配置在忽略的 `keystore.properties` 与 `keystore/`，不得写入文档、日志或提交。
