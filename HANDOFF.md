# CourseTable 项目交接

更新日期：2026-10-05
仓库：[liondoge123/CourseTable](https://github.com/liondoge123/CourseTable)

## 当前状态

- v1.7.1 正式构建由 `scripts/build-apk.ps1 release` 生成。源码与带注释标签保存在 Git，三种安装包与校验和作为 GitHub Release 资产分发。
- `version.properties` 为 `VERSION_NAME=1.7.1`、`VERSION_CODE=177`、`LAST_RELEASE_VERSION=1.7.1`；发布标签为 `v1.7.1`。
- 用户更新说明见 [`docs/releases/v1.7.1.md`](docs/releases/v1.7.1.md)。

正式产物位于 `app/build/outputs/apk/release`：

| ABI | 字节数 | SHA-256 |
| --- | --- | --- |
| arm64-v8a | 21,173,503 | `ff906e7cae42557f6937316f955c4a03c1bfa736c8bd4072056eb67f8731c21b` |
| armeabi-v7a | 19,830,125 | `40b3410eb2517316e5fe8e69a8a568434c2803035061d73d2343bdd929a1f9d6` |
| universal | 30,291,726 | `31fa612e4e69fc4a6b4df20f976458ba9e437fdb12c938d952293dc3faefbb6b` |

根目录存放了对应的 `CourseTable-v1.7.1-arm64-v8a-release.apk`，校验和文件为同目录的 `SHA256SUMS.txt`。源码提交不包含 APK。

## 本轮源码变化

- **液态导航栏与融球动效**：底部导航栏解耦为 4 项纯导航 `[课表, 课程, 导入, 设置]`，与独立同行动作岛 `+` 配合；在课表与课程页展开，在导入与设置页平滑隐藏并自动重新居中。搭载高光表面张力与内凹收腰的物理液态融球（Metaball）动效；静止态两端完全分离且各自拥有原生 Kyant `lens` 折射镜片。
- **课表与周次弹窗**：重构周次编辑弹窗与多周次支持，优化节次时间编辑弹窗与滚轮滑动。
- **预测性返回手势**：支持 Android 系统级预测性返回手势动画过渡。
- **输入法与焦点稳定性**：点击空白处与非输入控件退出编辑并清理焦点，彻底解决软键盘关闭时的焦点残留。

## 验证与发布

- 正式构建执行 JVM 单元测试、Android 测试代码编译、Lint、R8 与资源压缩。脚本复核包名、版本、SDK、ABI、V2 签名证书和三种 APK 的 SHA-256，均低于 31,500,000 字节阈值。
- 本地在模拟器上完成动效与交互验收，测试文件均已清理。
