# CourseTable 项目交接文档

更新日期：2026-09-11
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

业务数据由 Room 与 Preferences DataStore 保存。本次发布未修改数据库 Schema、包名、导入格式或提醒数据结构。

## 2. 当前正式版本

`version.properties`：

- `VERSION_NAME = 1.5.8`
- `VERSION_CODE = 91`
- `LAST_RELEASE_VERSION = 1.5.8`

Android 配置：

- `compileSdk = 37`
- `targetSdk = 34`
- `minSdk = 26`
- 签名证书 SHA-256：`bebf94b3520ca8392225b108d466885c5dec4fcff1389e780b5b32c61a2b76c2`

正式产物位于 `app/build/outputs/apk/release`：

| ABI | 文件 | 字节数 | SHA-256 |
| --- | --- | ---: | --- |
| arm64-v8a | `CourseTable-v1.5.8-arm64-v8a-release.apk` | 16,376,021 | `b89d806511b039a9f7f0596cd98b2e30b8fc2f22f5b3c3540d618a4eaeff2941` |
| armeabi-v7a | `CourseTable-v1.5.8-armeabi-v7a-release.apk` | 12,592,067 | `809adce12b5e24a0835c2218ad3648cfa44c9921a44b4182fae9fa2c47615995` |
| universal | `CourseTable-v1.5.8-universal-release.apk` | 44,893,911 | `0ee5c8d2d0ec06e9e4e43a902d241f9e717441ca4426493f22f60675273637a0` |

根目录仅保留 arm64-v8a 副本；三种 ABI 和 `SHA256SUMS.txt` 均上传到 GitHub Release `v1.5.8`。

## 3. v1.5.8 主要变更

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

## 4. 关键代码与架构约束

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

## 5. 验证状态

正式构建命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-apk.ps1 release
```

v1.5.8 构建结果：

- `testDebugUnitTest`：通过。
- `:app:compileDebugAndroidTestKotlin`：通过。
- `lintDebug`：通过。
- Release R8、资源压缩及三 ABI 构建：通过。
- `aapt dump badging`：包名、版本、minSdk、targetSdk 和 ABI 均通过。
- `apksigner verify`：APK Signature Scheme v2 与预期签名证书通过。
- SHA-256：已写入 `app/build/outputs/apk/release/SHA256SUMS.txt`。
- `LucideIconsTest`：45 个公开图标映射全部可实例化为 24×24 Lucide ImageVector。
- `InlineDeleteActionTest`：二次删除、紧凑语义和五秒复位测试源码已编译。

当前没有连接 Android 设备，因此 Compose instrumentation 测试未在真机执行；发布后的视觉回归应重点检查五槽底栏、Liquid Toggle、弹窗动作区、触点波纹和 Lucide 图标。

完整构建日志：`app/build/logs/release-b91.log`。

## 6. 构建与发布

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
- 禁止在日志或文档中输出 `keystore.properties` 的密码。

若容器内 Java 报 `Unable to establish loopback connection`，设置：

```powershell
$env:JAVA_HOME='C:\Users\zhangyuchao\.jdks\jbr-21.0.11'
$env:JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:\CourseTable\.unix-sockets'
```

## 7. 后续建议

- 在 arm64 真机执行五槽导航、中央添加、导入子页、弹窗、开关拖动、删除确认和深浅色主题视觉回归。
- 后续普通修复版本从 `1.5.9` 开始；新增兼容功能使用 `1.6.0`。
- 非发布开发优先使用编译与单测；仅在需要安装包时运行 Preview，避免无意义递增 `versionCode`。
