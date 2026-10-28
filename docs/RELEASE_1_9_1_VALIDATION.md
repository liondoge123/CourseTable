# v1.9.1 发布更正（2026-10-28）

用户明确要求本轮发布为 v1.9.1，优先于项目关于新增功能提升次版本号的一般约定。

误发的 v1.10.0 / versionCode 294 已撤回：删除 GitHub Release 及远程、本地 v1.10.0 标签，保留 Git 提交历史，不重写主分支。

恢复最后有效正式版记录为 v1.9.0，使用标准 `pwsh scripts/build-apk.ps1 release -VersionName 1.9.1` 重新构建。versionCode 继续递增为 295，确保已安装误发 294 的设备也可覆盖更新。应用代码不变，之前的模拟器交互、R8 提醒及重启验证记录仍在 [历史验证记录](RELEASE_1_10_0_VALIDATION.md)；重新构建再次运行单元测试、测试源码编译、Lint、R8 与制品核验。

构建 b295 成功，249 项单元测试通过，Android 测试源码编译、debug lint、R8 与资源收缩完成；三个正式 APK 均通过包名、1.9.1 / 295 版本、ARM ABI、v2 正式签名及 SHA-256 核验。构建日志为 `app/build/logs/release-b295.log`，制品与校验文件保存在 `.artifacts/releases/v1.9.1/`。没有为仅版本信息更正重跑相同应用代码的整套模拟器测试，也没有声称已将重打包后的 b295 安装到设备。

| 制品 | 字节 | SHA-256 |
| --- | ---: | --- |
| ARM64 | 21,391,612 | `c4f3bda9f72c02ace0ce62a6cc278ebc73e7cc24501058ef7f6c26ad88904558` |
| ARMv7 | 20,048,230 | `4ba0836b4af4ea6ca2738059a05041468ea6aaa333e34700a44acb78b8ac2401` |
| universal | 30,509,831 | `b2860cdc1e88aae1cb22157dabc3c9ce6e01d22ddf35499503e9ab1da996472d` |

后续任何 MINOR 或 MAJOR 增长，必须先说明原因并取得用户对具体版本号的同意；一般的“继续”或“发布”不视为同意跨版本。该约定已写入 AGENTS.md 与 RELEASE.md。
