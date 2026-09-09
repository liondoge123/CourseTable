# CourseTable 项目交接文档

更新日期：2026-09-08  
项目目录：`D:\CourseTable`

## 1. 项目概况

CourseTable 是一个单 Activity、Jetpack Compose 实现的 Android 课程表应用，包名为 `com.coursetable.app`，最低支持 Android 8.0（API 26），目标 Android 14（API 34），编译使用 API 37。

现有主要能力：

- **周课表展示**：周课表网格、平滑切周、单双周筛选、当前时间线指示、进行态竖向双色微进度条。
- **课表与课程管理**：多课表切换与增删改、全屏课表管理页、课程编辑与删除弹窗。
- **导入与导出**：教务系统、ICS、Excel/CSV、PDF、图片 OCR 和 JSON 备份导入；JSON 备份与 ICS 日历导出。
- **提醒与系统集成**：课前闹钟/通知提醒、测试提醒和权限状态检查。
- **视觉与主题**：浅色/深色/跟随系统主题、五种主题色、全局 Liquid Glass（液态玻璃）现代视觉设计。

业务数据基于 Room 数据库与 Preferences DataStore。近期所有 UI 重构均未修改数据库 Schema、导入解析器、提醒调度、包名或已有设置项。

## 2. 当前版本与重要状态

源码当前版本（`version.properties`）：

- `VERSION_NAME = 1.5.6`
- `VERSION_CODE = 71`
- `LAST_RELEASE_VERSION = 1.5.6`
- `compileSdk = 37`
- `targetSdk = 34`
- `minSdk = 26`

最新正式发布安装包：
- **产物文件**：`D:\CourseTable\CourseTable-v1.5.6-arm64-v8a-release.apk`
- **版本代码**：`versionCode = 71`，`versionName = 1.5.6`
- **目标架构**：`arm64-v8a`（另有 `armeabi-v7a` 与 `universal`）
- **签名校验**：APK Signature Scheme v2/v3 均通过
- **SHA-256**：`E7ECF5C1C46309885BF75CF81B19CBFA68374BCE055DD1D97CE89D1A1A3D0D99`

项目目录当前没有 `.git` 仓库，版本演进与发布追溯严格依赖 `version.properties`、`RELEASE.md` 以及每次构建在根目录保留的规整命名的 APK 产物。如需备份，建议在每次重大更新后对项目根目录打 zip 快照。

## 3. 近期重大特性与 UI 重构演进（b55 - b70 / v1.5.6）

### 3.1 课表顶栏悬浮微胶囊与真实页面液态玻璃（b60 - b70）
- **彻底去除通栏背景底条与冗余大字**：
  - 移除了横跨屏幕顶部的半透明白色/灰色矩形底条，顶栏背景 100% 全透气穿透底层的液态渐变光影（`LiquidAmbientBackground`）；
  - 移除了顶栏冗余的“课表”大字标题（底栏 Dock 已常驻指示当前模块），将视觉空间完全归还给核心的周次与课表信息。
- **统一 36dp 全胶囊规范**：
  - **周次主胶囊 `[ 第 X 周 ▾ ]`**：高度 `36dp`、全胶囊圆角 `18dp`，当前周前置醒目的 `6dp` 主题色指示微圆点，点击弹出全学期周次网格选择器；
  - **多课表切换胶囊 `[ 课表名 ▾ ]`**：高度 `36dp`、全胶囊圆角 `18dp`，支持多课表下拉切换；
  - **单双周筛选胶囊**：高度 `36dp`、全胶囊圆角 `18dp`，激活时叠加主题色半透水感微光；
  - **连体翻周双键胶囊 `[ < │ > ]`**：高度 `36dp`、全胶囊圆角 `18dp`，将分散的独立按键整合为对称微胶囊，中间带有 `0.8dp` `colors.glassBorder` 细微分割线。
- **与底栏统一的液态玻璃镜片（Liquid Glass Lens）**：
  - 顶栏与底栏统一采用 AndroidLiquidGlass 示例的核心链路：`vibrancy()`、`blur(8.dp)`、`lens(24.dp, 24.dp)` 与 40% 半透明容器色；
  - 顶栏胶囊不再叠加强白色表面、高光与独立实体阴影，实际显示其下方课表的模糊、折射内容；
  - `LiquidCapsuleSurface` 必须优先读取 `LocalTopChromeGlassBackdrop`，不得误接回仅含环境渐变的 `LocalGlassBackdrop`。
- **按下轻微缩放与透镜折射加深（Press Feedback）**：
  - 采用阻尼弹簧物理缩放 `spring(dampingRatio = 0.55f, stiffness = 360f)`，按下时整体微扩张至 `1.05x`，松开平滑回弹；
  - 透镜保持与底栏一致的 `24dp/24dp` 参数，按压过程只通过 RenderNode 缩放表现弹性，避免布局重排；
  - 翻周双键胶囊内部左右箭头独立检测手势：按下 `<` 或 `>` 时胶囊整体微扩张，对应箭头图标独立放大至 `1.20x`。
- **真正的悬浮滚动结构**：
  - `TimetableScreen` 根布局由“顶栏占一行的 `Column`”改为前后叠放的 `Box`；顶栏固定在最上层，不随课表滚动；
  - 课表滚动内容初始预留 `52.dp` 顶部空间，初始状态不被胶囊遮挡；向下浏览时课表进入胶囊下方，胶囊实时折射经过的课程卡片；
  - 顶部不存在通栏实体面板，滚动后只保留四组悬浮玻璃胶囊。

### 3.2 渲染架构级解耦与闪退根治（b61 - b70）
- **闪退根本原因排查**：
  - 在 b61 中引入顶栏 `drawBackdrop` 时，`MainActivity` 原本将整个页面外层 `Box` 包裹在 `.glassBackdropSource()`（即 `layerBackdrop`）内；
  - 当页面子树中的顶栏微胶囊再次调用 `drawBackdrop(LocalGlassBackdrop.current)` 时，Android 硬件加速渲染管线（Skia/HWUI）检测到同一个正在录制 DisplayList 的父级 RenderNode 试图绘制到自身内部，触发底层递归保护异常 `RenderNode cannot be drawn into itself`，导致进入课表页瞬间闪退。
- **解耦解决方案（v1.5.6 最终架构）**：
  - 在 `MainActivity.kt` 中将 `.glassBackdropSource()` 直接且仅锚定在全局环境渐变背景层 `LiquidAmbientBackground` 上；
  - 新增相互独立的 `LocalNavigationGlassBackdrop` 与 `LocalTopChromeGlassBackdrop`：底栏采样完整页面场景，顶栏只采样其下方的环境背景与可滚动课表；
  - 每个玻璃消费者只读取不包含自身的专用 RenderNode。三条 Backdrop 互不自取样，同时允许顶栏、底栏实际折射页面内容；
  - b69 曾因 `LiquidCapsuleSurface` 仍读取旧环境 Backdrop 而呈均匀浅蓝；b70 已将其正确接到 `LocalTopChromeGlassBackdrop`，并恢复通用 `GlassSurface` 使用 `LocalGlassBackdrop`。

### 3.3 课程卡片与非本周课程全新规范（b55 - b58）
- **进行态课程进度指示条**：
  - 放弃粗笨的整卡半透明覆盖层与粗竖条，改为卡片左边缘精致的 `3.5dp` 双色竖向填充进度条；
  - 底轨为课程主题色浅底，进行中根据当前时间占比自上而下填充饱和主题色，底部带微弧圆角，既不遮挡课程文字，又直观展现下课倒计时进度。
- **非本周课程极简统一化**：
  - 彻底移除非本周课程卡片左侧颜色条，卡片整体呈柔和低对比度浅灰，文字左右内边距完全平齐；
  - 彻底移除非本周卡片底部挤占空间的“非本周”三个汉字，网格内小卡片回归“课程名 + 教室”极简排版；
  - 将“非本周”感知转移到点击后的 `CourseDetailDialog`（详情对话框顶部展示显眼的 `[非本周]` 柔光微胶囊徽标），保证查看详情时信息清晰，且不污染日常课表主视图。

### 3.4 主导航三页面平滑切换动效与滚动状态持久化（b59）
- **三页面平滑横向滑动与淡入淡出**：
  - 在 `MainActivity.kt` 中，底部“课表 / 课程 / 更多”三大根模块切换由原本的生硬跳变重构为 `AnimatedContent`；
  - 配合 `220ms` 物理平滑水平位移与透明度过渡（`tween(220, easing = FastOutSlowInEasing)`），根据 Tab 序号相对方向智能判定向左或向右滑动。
- **滚动状态持久化保存**：
  - 对 `TimetableScreen`、`CourseManageScreen`、`SettingsScreen` 的垂直滚动状态（`vScroll` 与 `scrollState`）统一升级为 `rememberSaveable`；
  - 用户在课程列表或设置页滑动到下方后切换到课表页再返回，原页面严格保持浏览位置，不再重置跳回顶部。

### 3.5 玻璃渲染与设计系统架构
- `app/src/main/java/com/coursetable/app/ui/liquid/GlassHost.kt`：
  - `LiquidBackdropHost` 提供三条独立取样源：`LocalGlassBackdrop`、`LocalNavigationGlassBackdrop`、`LocalTopChromeGlassBackdrop`，以及能力检测和覆盖层控制器。
  - `glassBackdropSource()` 只录制环境背景；`navigationGlassBackdropSource()` 录制底栏所需的完整页面；`topChromeGlassBackdropSource()` 录制顶栏下方的可滚动课表。
  - **重要原则：任何 `layerBackdrop` 都不能包含读取同一个 Backdrop 的玻璃消费者；需要嵌套玻璃时必须使用不同的 LayerBackdrop 解耦。**
- `app/src/main/java/com/coursetable/app/ui/liquid/GlassSurfaces.kt`：
  - `LiquidCapsuleSurface`：专为 18dp 浮空胶囊定制的通用液态玻璃组件，内置实时透镜折射、阻尼弹簧按压缩放、动态高光加深以及静态自适应降级。
  - `GlassSurface` & `OverlayGlassSurface`：通用的毛玻璃面板与覆盖层组件。
- `app/src/main/java/com/coursetable/app/ui/liquid/LiquidNavigationTabs.kt`：
  - `LiquidNavigationTabs`：屏幕底栏悬浮 Dock 组件，支持物理拖拽、滑动跟随透镜折射与回弹。
  - `LiquidGlassIconButton`：右下角悬浮操作按钮（FAB），支持液态玻璃圆形镜片与按压微扩张。
- `app/src/main/java/com/coursetable/app/ui/liquid/GlassOverlays.kt`：
  - 同 Window 覆盖层管理（BottomSheet / Dialog / Menu），避免系统独立 Window 跨窗口取样错位。

## 4. 最近验证结果

最近一次正式发布验证命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-apk.ps1 release
```

结果：
- `testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin`、`lintDebug`、R8 Release 构建全部成功；
- 成功生成 v1.5.6（versionCode 71）的 `arm64-v8a`、`armeabi-v7a`、`universal` 三份正式 APK；
- `aapt dump badging` 已验证包名 `com.coursetable.app`、`versionName 1.5.6`、`versionCode 71`、`targetSdk 34` 与各 ABI；
- `apksigner verify` 已验证 v2/v3 签名及预期签名证书；
- 三份正式产物的 SHA-256 已写入 `app\build\outputs\apk\release\SHA256SUMS.txt`。
- 当前真机环境暂未接入自动化 CI，Compose instrumentation 测试已完成编译；后续视觉回归仍需安装 Preview 或 Release APK 在真机确认。

## 5. 当前 Release 产物与测试产物

### 正式 Release 产物目录：`D:\CourseTable\app\build\outputs\apk\release`

当前正式发布版本为 **v1.5.6**（versionCode 71）：

- `CourseTable-v1.5.6-arm64-v8a-release.apk`  
  SHA-256：`e7ecf5c1c46309885bf75cf81b19cbfa68374bce055dd1d97ce89d1a1a3d0d99`
- `CourseTable-v1.5.6-armeabi-v7a-release.apk`  
  SHA-256：`78a0b79ab3b31f1495a7f93d7ecab57a145c8bdbca2a401f232b856293fe0e47`
- `CourseTable-v1.5.6-universal-release.apk`  
  SHA-256：`f54c8589c29f62f2abdd8182e5cdd45a315261452349d2b60233b891454320e2`

它们均通过 APK Signature Scheme v2/v3 验证，R8 混淆优化开启；构建脚本仅把最新 arm64-v8a 正式包复制到根目录。

### 最新测试预览产物（Preview Artifact）：

- **最近验证包**：`D:\CourseTable\CourseTable-v1.5.6-preview-b70-arm64-v8a-preview.apk`
- **版本标识**：`versionName 1.5.6-preview-b70` / `versionCode 70`
- **说明**：Preview 包与正式 Release 包采用完全一致的签名证书，可直接覆盖安装，无须卸载现有版本。构建脚本只向根目录复制 arm64-v8a APK；历史产物需人工归档。

## 6. 构建环境与命令速查

Android SDK：`D:\android-tools\sdk`

已安装并配置用于本项目：
- `platforms\android-37.0`
- `build-tools\36.0.0`
- `build-tools\37.0.0`
- JDK：JBR 21（如 `C:\Users\zhangyuchao\.jdks\jbr-21.0.11`）

Windows / 容器终端网络与 Gradle 临时目录兼容配置（若遇到 `Unable to establish loopback connection`）：

```powershell
$env:JAVA_HOME='C:\Users\zhangyuchao\.jdks\jbr-21.0.11'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\CourseTable\.unix-sockets'
```

### 常用命令：

1. **快速执行单元测试与 UI 源码编译**：
   ```powershell
   .\gradlew.bat testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon --offline
   ```
2. **快速打出真机测试预览包（arm64-v8a）**：
   ```powershell
   .\scripts\build-apk.ps1 preview
   ```
3. **正式发布构建（完整验证 + 3 ABI 打包 + R8 + Lint）**：
   ```powershell
   .\scripts\build-apk.ps1 release
   ```

## 7. 发布与测试打包规范

发布与打测试包必须遵循根目录 [RELEASE.md](file:///d:/CourseTable/RELEASE.md) 与 [AGENTS.md](file:///d:/CourseTable/AGENTS.md)：

1. **统一脚本入口**：
   - 测试包：`scripts\build-apk.ps1 preview`（只编 arm64，跳过测试/R8，快速产出，自动递增 versionCode）。
   - 正式包：`scripts\build-apk.ps1 release`（编 arm64/v7a/universal，执行全部单元测试、编译与 R8 优化）。
2. **命名规范**：
   - 正式 APK 命名：`CourseTable-v{versionName}-{ABI}-release.apk`。
   - 严禁在正式产物中使用 `ui` 等模糊后缀。
3. **交付前五项核验**：
   - 必须通过 `aapt dump badging` 检查包名 `com.coursetable.app`、版本号与 targetSdk。
   - 必须通过 `apksigner verify` 确认 v2/v3 签名。
   - 必须输出并核对 SHA-256 校验和。
   - 仅将 `arm64-v8a` 复制到项目根目录，其余多 ABI 留在 `app\build\outputs\apk\release`。
4. **签名安全**：
   - 证书 SHA-256：`bebf94b3520ca8392225b108d466885c5dec4fcff1389e780b5b32c61a2b76c2`。
   - 任何文档、日志与交互中严禁输出 `keystore.properties` 中的密码敏感信息。

## 8. 已知架构约束、风险与待验证事项

- **【关键架构红线】RenderNode 自取样递归崩溃**：
  - `MainActivity.kt` 中 `.glassBackdropSource()` **只能锚定在底层 `LiquidAmbientBackground` 上**。
  - **严禁**将 `.glassBackdropSource()` 附加在包含 `LiquidCapsuleSurface`、`LiquidGlassIconButton` 等取样消费组件的容器上。Android 渲染树中 RenderNode 无法将自身作为纹理绘制到子 RenderNode 中，否则会导致不可捕获的 `java.lang.IllegalArgumentException: RenderNode cannot be drawn into itself` 闪退。
  - 底栏必须读取 `LocalNavigationGlassBackdrop`，顶栏 `LiquidCapsuleSurface` 必须读取 `LocalTopChromeGlassBackdrop`；不要把二者简化回同一个全局 LayerBackdrop。
- **顶栏悬浮与滚动留白**：
  - `WeekHeader` 必须保持为课表滚动层之后绘制的 `Box` 上层 sibling，不得重新放回占据固定高度的 `Column`；
  - `TimetableGrid` 的 `topContentPadding` 当前为 `52.dp`。它只负责初始避让，必须位于 `verticalScroll` 的内容 padding 内，才能随滚动消失并让课表经过胶囊下方。
- **顶栏胶囊规范尺寸与触摸响应**：
  - 顶栏胶囊必须统一保持 `height(36.dp)`，圆角 `RoundedCornerShape(18.dp)`。
  - 按压微动效通过 `graphicsLayer` 的 `scaleX/scaleY` 实现弹性反馈（1.05x），不得通过直接修改布局尺寸引起重排震颤。
- **Android 版本差异与降级兜底**：
  - Android 13+ (API 33+)：全功能 RuntimeShader / AGSL 折射镜片 + 动态模糊。
  - Android 12 (API 31-32)：RenderEffect Blur 回退，无 AGSL Shader。
  - Android 11 及以下：静态高斯半透明 Tint 降级，确保在旧机型与低性能设备上平稳运行无白屏或崩溃。
- **页面切换与状态留存**：
  - 课表页、课程页、更多页间的水平滑动动效为 220ms。必须保证各 Tab 内部的滚动位置（`LazyListState` / `ScrollState`）与筛选状态正常留存，不得出现页面切换导致的回到顶部或空白闪烁。
- **Lint 与依赖警告**：
  - Lint 存在关于精确闹钟权限与 ML Kit 16KB 对齐的已知非阻断提示。在纯 UI/动画迭代中切勿改动权限行为或变更 core 依赖版本。

## 9. 建议下一步

1. **归档 v1.5.6**：项目无 Git 仓库，建议立即对源码、`version.properties`、本交接文档及三份 Release APK 创建只读 zip 快照。
2. **后续修改流程**：普通代码修改默认只执行编译/单测；仅在明确需要真机包时运行 Preview，避免无意义递增 `versionCode`。
3. **下一版本**：修复或视觉微调使用 `1.5.7`，向后兼容的新功能使用 `1.6.0`；正式发布必须高于 `LAST_RELEASE_VERSION=1.5.6`。
