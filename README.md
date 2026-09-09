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

## 技术栈

- Kotlin
- Jetpack Compose
- Room
- DataStore
- AndroidLiquidGlass / Backdrop
- ML Kit Text Recognition

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

## 安全说明

签名密钥、签名密码、`local.properties` 和构建缓存不会提交到仓库。请勿将自己的签名密钥或密码上传到公开仓库。

## 许可证

本项目当前未声明单独的开源许可证。未经作者许可，请勿将其作为自己的应用发布。
