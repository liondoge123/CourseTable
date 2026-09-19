# CourseTable

CourseTable 是一款面向 Android 的课程表应用，使用 Kotlin 和 Jetpack Compose 构建。它将课程查看、课程编辑、课表导入导出、教务系统接入和课程提醒整合在一个轻量的本地应用中。

本项目由 `liondoge123` 发起和维护，并在开发过程中得到 ChatGPT 的协助，包括液态玻璃交互适配、代码实现、第三方项目与许可证核对、文档整理以及构建发布流程。

## 主要功能

### 课程表

- 按周查看课程安排，并支持切换周次。
- 显示课程名称、教师、教室、时间和周次信息。
- 支持当前周、非当前周和自定义学期设置。
- 支持主题色、深色模式和卡片对齐方式设置。

### 课程与课表管理

- 新增、编辑和删除课程。
- 支持多个独立课表，并可切换当前使用的课表。
- 支持配置总周数、节次数量、上课时间和课程提醒。
- 数据默认保存在设备本地，不依赖在线账号才能使用核心功能。

### 导入、导出与备份

- 从 Excel 文件导入课程。
- 从 PDF 文件导入课程，并结合 OCR 识别课程表内容。
- 图片/PDF 导入支持周课表和选课明细表：先定位时间结构，再关联课程、教师与地点，并保留多次上课和分散周次。
- OCR 使用 ML Kit 定位文字，结合本地 PP-OCRv6 Tiny 识别；低置信度或时间字段冲突会标记待校对。正式 APK 体积控制在 30 MB 左右。
- 导入前可在原图上缩放、切页、补框、调整框、局部重识别或手动补课程；待校对记录需补齐或删除后才能导入。
- 导入和导出 ICS 日历文件。
- 支持从教务系统获取课表，目前包含适配的学校/教务系统入口。
- 支持完整备份与恢复，用于迁移设备或保存多个课表。

### 提醒

- 可以为课程设置课前提醒。
- 使用 Android 系统的定时任务调度提醒，不需要应用持续运行在前台。

## 液态玻璃导航与交互

底部导航栏采用“课表 / 课程 / 添加 / 导入 / 设置”五槽结构。中央“+”是全局添加课程动作，其余四项是可拖动切换的根页面。导航采用 Backdrop 的真实背景采样渲染，并以 AndroidLiquidGlass 的原生 Compose 实现为基准：

- 静态状态：背景模糊、亮度/饱和度处理、镜片折射和胶囊形状。
- 选中状态：指示镜片通过组合背景采样显示底层内容，并支持色差折射。
- 按住和拖动：镜片会根据拖动方向产生横向拉伸和纵向挤压。
- 快速左右移动：使用动画位置的速度驱动挤压强度，而不是简单地将单帧位移直接转换为缩放。
- 松手后：位置、按压进度、横向缩放、纵向缩放和速度分别使用弹簧动画回到稳定状态。
- 底栏仍使用 CourseTable 自己的尺寸（外层 54dp、镜片 48dp），没有照搬参考项目的布局尺寸。
- 设置开关、弹窗表面、触点波纹和顶栏胶囊也使用统一的 Liquid 交互反馈。

交互参数直接参考 AndroidLiquidGlass 的原生 `LiquidBottomTabs.kt` 与 `DampedDragAnimation.kt`：位置和按压使用临界阻尼弹簧，横向和纵向缩放使用欠阻尼弹簧，速度形变使用单独的欠阻尼弹簧。CourseTable 在此基础上保留了按下非当前标签时镜片立即移动、松手后确认页面切换的交互。Android 13（API 33）及以上设备可以使用完整的 AGSL 折射效果；Android 12（API 31–32）提供模糊能力，较低版本使用兼容的静态玻璃样式。

## 技术栈

- Kotlin 2.4
- Jetpack Compose
- AndroidX Activity、Lifecycle、Foundation 和 Animation
- Room：课程和课表数据存储
- DataStore：应用设置存储
- Backdrop：液态玻璃背景采样和渲染
- Kyant Shapes：连续曲率胶囊形状
- Lucide Icons：统一的 24×24 线性 SVG 图标
- ML Kit Text Recognition：PDF/OCR 课程表识别
- OkHttp、PDFBox、JSON 和 ICS 解析相关组件

## 开源项目与致谢

本项目使用和适配以下开源项目与视觉资源：

1. [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)

   提供 Backdrop 液态玻璃渲染库，以及 Liquid Bottom Tabs、Liquid Button 等原生 Compose 示例。本项目的背景采样、折射、模糊、色差、内外阴影、速度挤压和弹簧动画均以这里的 Android/Compose 实现为基础。具体参考：

   - [LiquidBottomTabs.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt)
   - [DampedDragAnimation.kt](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt)
   - [Apache License 2.0](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/LICENSE)

2. [Kyant0/Shapes](https://github.com/Kyant0/shapes)

   提供 `Capsule` 和其他连续曲率圆角形状，用于让玻璃边缘保持更平滑的曲率。该仓库公开标注为 [Apache License 2.0](https://github.com/Kyant0/shapes/blob/master/LICENSE)。

3. [Lucide Icons](https://lucide.dev/)

   应用内通用操作、导航与状态图标取自 Lucide 1.45.0 官方 SVG，统一采用 24×24 网格、2px 圆端线性样式，并遵循 [ISC License](app/src/main/assets/licenses/lucide-icons.txt)。

本项目使用上述开源项目公开提供的依赖、接口、参考实现与视觉资源，并针对 Android 版本差异以及 CourseTable 自己的界面尺寸进行了适配。

## 项目结构

```text
CourseTable/
├── app/src/main/java/com/coursetable/app/
│   ├── data/                 # Room 数据模型、Repository 和设置
│   ├── importer/             # Excel、PDF、ICS、OCR 和教务系统导入
│   ├── reminder/             # 课程提醒调度
│   ├── ui/                   # Compose 页面、组件和 ViewModel
│   │   └── liquid/            # 液态玻璃宿主、表面、覆盖层和导航栏
│   └── MainActivity.kt       # 应用入口和根导航
├── app/src/test/              # JVM 单元测试
├── app/src/androidTest/       # Compose/Android 仪器测试
├── scripts/build-apk.ps1     # Preview/Release 打包脚本
├── RELEASE.md                # 版本、产物和校验规范
└── version.properties        # 当前版本号和 versionCode
```

## 开发环境

- Android Studio，或可以运行 Android Gradle Plugin 的 IDE。
- JDK 21/JBR 21。
- Android SDK Platform 37。
- Android SDK Build-Tools，且已配置 `local.properties` 或 `ANDROID_SDK_ROOT`。
- 最低支持 Android 8.0（API 26）。
- 目标 SDK 为 Android 14（API 34）。

## 构建项目

在项目根目录执行：

```powershell
# 构建 arm64-v8a Preview 测试包。
.\scripts\build-apk.ps1 preview

# 运行完整校验并构建正式包。
.\scripts\build-apk.ps1 release
```

Preview 构建用于快速安装测试，关闭 R8 且只生成 `arm64-v8a`。Release 构建会执行：

- JVM 单元测试。
- Android 测试代码编译。
- Debug lint 检查。
- Release R8 混淆和资源压缩。
- `arm64-v8a`、`armeabi-v7a` 和 `universal` 三种 APK 构建。
- 包名、版本、SDK、ABI、签名和 SHA-256 校验。

构建日志位于 `app/build/logs/`，校验和文件位于对应的 `app/build/outputs/apk/{preview|release}/SHA256SUMS.txt`。

## 安装 APK

正式包可以从 [GitHub Releases](https://github.com/liondoge123/CourseTable/releases) 下载。也可以使用 ADB 安装：

```powershell
adb install -r CourseTable-v1.5.8-arm64-v8a-release.apk
```

Preview 和 Release 使用同一应用包名及签名证书，因此 Preview 可以覆盖已经安装的正式版本。

## 当前版本

当前正式版本为 [v1.5.8](https://github.com/liondoge123/CourseTable/releases/tag/v1.5.8)（versionCode 91）。该版本加入五槽根导航、独立导入页、Liquid Toggle、弹窗与触点反馈优化，并将应用图标体系统一迁移到 Lucide Icons 1.45.0。

正式 APK：

- [arm64-v8a](https://github.com/liondoge123/CourseTable/releases/download/v1.5.8/CourseTable-v1.5.8-arm64-v8a-release.apk) — `b89d806511b039a9f7f0596cd98b2e30b8fc2f22f5b3c3540d618a4eaeff2941`
- [armeabi-v7a](https://github.com/liondoge123/CourseTable/releases/download/v1.5.8/CourseTable-v1.5.8-armeabi-v7a-release.apk) — `809adce12b5e24a0835c2218ad3648cfa44c9921a44b4182fae9fa2c47615995`
- [universal](https://github.com/liondoge123/CourseTable/releases/download/v1.5.8/CourseTable-v1.5.8-universal-release.apk) — `0ee5c8d2d0ec06e9e4e43a902d241f9e717441ca4426493f22f60675273637a0`
- [SHA256SUMS.txt](https://github.com/liondoge123/CourseTable/releases/download/v1.5.8/SHA256SUMS.txt)

## 兼容性与限制

- 完整的 AGSL 液态玻璃折射依赖 Android 13 及以上系统能力；旧版本会自动降级到可用的模糊或静态玻璃表现。
- 不同设备的 GPU、系统渲染器和屏幕密度可能导致折射强度、色差和动画观感存在差异。
- 教务系统导入依赖学校页面格式；页面改版后可能需要更新对应解析器。
- PDF/OCR 导入的识别效果取决于原始文件清晰度、排版和字体。
- 项目当前未提供云同步服务，跨设备迁移请使用备份和恢复功能。

## 许可证说明

CourseTable 现以 [Apache License 2.0](LICENSE) 开源。你可以按照许可证条款使用、修改和再分发本项目，但需要保留许可证和相关署名，并遵守第三方依赖各自的许可证要求。

上面列出的第三方项目分别采用 Apache License 2.0 或 ISC License。本项目的名称和品牌标识不代表授予第三方商标或品牌使用权。
