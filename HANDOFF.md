# CourseTable 项目交接

更新日期：2026-09-30
仓库：[liondoge123/CourseTable](https://github.com/liondoge123/CourseTable)

## 当前状态

- v1.7.0 正式构建由 `scripts/build-apk.ps1 release -VersionName 1.7.0` 生成。源码与带注释标签保存在 Git，三种安装包与校验和作为 GitHub Release 资产分发。
- `version.properties` 为 `VERSION_NAME=1.7.0`、`VERSION_CODE=130`、`LAST_RELEASE_VERSION=1.7.0`；发布标签为 `v1.7.0`。
- README 已补充时间方案功能；用户更新说明见 [`docs/releases/v1.7.0.md`](docs/releases/v1.7.0.md)，按钮约定见 [`docs/UI_BUTTON_GUIDELINES.md`](docs/UI_BUTTON_GUIDELINES.md)。

正式产物位于 `app/build/outputs/apk/release`：

| ABI | 字节数 | SHA-256 |
| --- | ---: | --- |
| arm64-v8a | 21,077,295 | `ddcdff6e866d24ad4c23d92967fc6ef84075b20980151f9312036d4aa02601fb` |
| armeabi-v7a | 19,733,917 | `f7a51318b43f55bba9ecb083a61bac7be09d6b8a1ecb69e631f0ae2467345797` |
| universal | 30,195,518 | `c327e271010583d8e9cfff646a4b7355722d82969eb527a5c635eb486c305859` |

文件名均为 `CourseTable-v1.7.0-{ABI}-release.apk`，校验和文件为同目录的 `SHA256SUMS.txt`。源码提交不包含 APK。

## 本轮源码变化

- **时间方案**：在“节次时间”窗口内先展示方案列表，点击名称进入编辑，独立选用按钮切换当前课表。方案支持新建、重命名、编辑、删除和自定义时间另存；编辑保存不自动切换。
- **存储**：方案作为全局快照保存在 Preferences DataStore，原有课表节次时间仍保存在 Room。沿用已有课表数据，无数据库迁移。当前 JSON 备份没有包含方案列表；应用到课表的时间随课表备份。
- **图标与控件**：桌面图标更新为蓝色渐变及等长的长/短课程块，主题图标使用独立镂空矢量。选中标记采用圆圈内的勾，操作按钮统一为胶囊，取消/关闭的次要样式不加描边，危险操作使用红色语义。
- **弹窗与输入法**：移除居中弹窗外阴影，识别失败的多个操作独立排列。输入法可见性使用派生状态避免动画逐帧触发重组，避让空间移到弹窗外层，优先保留底部操作区域；关闭输入法后清除焦点。
- **校验**：节次设置拒绝空列表、重叠和跨午夜，并修正提示中的节次编号。

导入清单的布局、队列和数据生命周期见 [`docs/IMPORT_REVIEW.md`](docs/IMPORT_REVIEW.md)。

## 架构与使用边界

- 应用是单 Activity、Jetpack Compose 界面；Room 保存课程和课表，Preferences DataStore 保存设置。包名为 `com.coursetable.app`，最低 API 26，目标 API 34。
- 导入入口覆盖教务系统、ICS、Excel/CSV、PDF、图片和 JSON 备份；`ImportPolicy` 对外部内容执行类型、大小及输出数量约束。图片/PDF 的 `ImportFlowState` 持有临时资源，成功、失败或取消后必须关闭会话并清理目录。
- `LiquidBackdropHost` 的覆盖层记录器按层分离。玻璃采样源只能放在不含玻璃消费者的背景层；课表顶栏与底部导航使用各自的 Backdrop。新增嵌套窗口应通过现有 `ModalBottomSheet`、`AlertDialog` 或 `GlassOverlayPortal`，避免实色全屏窗口遮断背景。
- 正式包的 R8 规则必须保留 ML Kit 与 ONNX Runtime 所需类；历史版本曾在删去 ML Kit 保留规则后出现 Debug 正常、正式包导入崩溃的问题。不要用 Debug 测试 APK 直接判断混淆后正式包的运行结果。
- Android 系统自动备份关闭。跨设备迁移使用应用内 JSON 备份；课程提醒在没有精确闹钟授权时降级。江苏大学教务登录从 HTTPS CAS 开始，但学校课表接口仍使用限定域名的 HTTP，存在会话传输风险；不要放宽全局明文网络策略。
- 原图识别会随样本清晰度和排版变化。缺失或冲突字段仍需用户校对，不能把单个截图的成功结果视为普遍准确率。

## 验证与待验证项

- 正式构建执行 JVM 单元测试（73 项通过）、Android 测试代码编译、Lint、R8 与资源压缩。脚本复核包名、版本、SDK、ABI、V2 签名证书和三种 APK 的 SHA-256，均低于 31,500,000 字节阈值。日志在 `app/build/logs/release-b130.log`。
- 开发期间在 Android 14 独立模拟器运行了时间方案交互 7 项、按钮布局/主题色 1 项及输入法可见性/焦点 2 项测试。验证了编辑与选用分离、保存失败保留内容、返回列表、增删节次、危险删除确认，以及输入法关闭后失焦和再次聚焦。
- 输入法测试验证状态更新和交互行为，没有测量实体设备帧率。实体 ARM 设备仍需验证输入法动画、导入、提醒及正式包混淆后的功能；教务登录需要真实学校环境复核。
- 方案列表当前仍支持从详情返回列表；在详情点击遮罩也会退回列表。辅助的“添加节次、另存为方案、重置、重新选择学校”按钮仍沿用显式描边样式，此轮没有继续扩大界面改动。

## 后续版本发布

1. 以 [`RELEASE.md`](RELEASE.md) 为准，确认语义版本高于 `LAST_RELEASE_VERSION`，并先核对源码与测试结果。
2. 使用 `scripts/build-apk.ps1 release` 生成 arm64-v8a、armeabi-v7a 和 universal 正式 APK。脚本会递增 `versionCode`，执行 JVM 测试、Android 测试代码编译、Lint、R8 构建，并检查包名、SDK、版本、ABI、签名及 SHA-256。
3. 检查三种 APK 均未超过 31,500,000 字节防护阈值，复核 `SHA256SUMS.txt` 与签名证书；不要把本地 Preview 包或 Debug 包当作正式产物。
4. 提交构建脚本更新的 `version.properties`，创建并推送带注释的 `v{versionName}` 标签，再创建 GitHub Release，上传三种 APK 和校验和文件。APK 与本地新增的 `.artifacts/` 不进入 Git 源码提交；仓库里已有的历史验证截图保持原状。

如发布版本发生变化，以上版本号和对应标签须同步调整。签名配置在忽略的 `keystore.properties` 与 `keystore/`，不得写入文档、日志或提交。
