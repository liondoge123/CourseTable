# CourseTable

CourseTable 是一款基于 Jetpack Compose 的 Android 课程表应用，面向需要快速查看、编辑和导入课程安排的用户。

## 功能

- 周视图课程表与周次切换
- 课程新增、编辑和删除
- 多课表管理
- 从 Excel、PDF、ICS 和教务系统导入课程
- ICS 导出与数据备份/恢复
- 课程提醒
- 深色模式与主题色设置
- 液态玻璃风格底部导航栏
- 底部导航支持折射、色差、拖拽挤压和弹簧回弹效果

## 技术栈与致谢

- Kotlin
- Jetpack Compose
- Room
- DataStore
- AndroidLiquidGlass / Backdrop
- ML Kit Text Recognition

本项目的液态玻璃效果使用了以下开源项目：

- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)：提供 Backdrop 液态玻璃渲染库和 Compose 示例实现，许可证为 [Apache License 2.0](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/LICENSE)。
- [Kyant0/Shapes](https://github.com/Kyant0/shapes)：提供 `Capsule` 等连续曲率形状，许可证为 [Apache License 2.0](https://github.com/Kyant0/shapes/blob/master/LICENSE)。
- [martin65536/liquid-glass-webgl](https://github.com/martin65536/liquid-glass-webgl)：作为底部导航静态材质、拖拽挤压和弹簧回弹参数的参考，许可证为 [Apache License 2.0](https://github.com/martin65536/liquid-glass-webgl/blob/main/LICENSE)。

本项目使用的是上述项目的公开库、接口和参考实现；底部导航交互已针对 Android Compose 和本项目的底栏尺寸进行适配，并非直接打包 WebGL 项目。

## 环境要求

- Android Studio 或兼容的 JDK 21 环境
- Android SDK 37
- minSdk 26
- targetSdk 34

## 构建

```powershell
# 构建 arm64 preview 测试包
.\scripts\build-apk.ps1 preview

# 执行完整校验并构建正式包
.\scripts\build-apk.ps1 release
```

正式构建会运行单元测试、Android 测试编译、lint、R8，并生成以下 ABI：

- `arm64-v8a`
- `armeabi-v7a`
- `universal`

## 下载

正式 APK 可在 [Releases](https://github.com/liondoge123/CourseTable/releases) 页面下载。

当前版本：[v1.5.7](https://github.com/liondoge123/CourseTable/releases/tag/v1.5.7)。

## 许可证说明

上面列出的第三方依赖均为开源项目，并按各自的 Apache License 2.0 条款使用。

CourseTable 本身当前未声明单独的开源许可证；如需再发布、二次分发或将其作为自己的应用发布，请先取得作者授权，并同时遵守第三方依赖的许可证与署名要求。
