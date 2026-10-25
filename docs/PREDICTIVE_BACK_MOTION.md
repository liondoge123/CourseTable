# 全屏预测性返回的参考与实现

本次参考的是可核实的官方设计和开源代码，没有声称复制 Gmail 等闭源应用的内部参数。

## 参考

- [Android 预测性返回设计](https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back)：整页预览缩小到90%，远侧保留8dp空间，纵向位移受限，原始手势进度需要经过减速映射。
- [MaterialMainContainerBackHelper](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/motion/MaterialMainContainerBackHelper.java)：用于全屏 SearchView 的预览几何，读取窗口的四角信息；不是把普通卡片的圆角直接套到整页上。
- [MaterialBackAnimationHelper](https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/motion/MaterialBackAnimationHelper.java)：采用 `(0.1, 0.1, 0, 1)` 曲线，提交动画时长默认范围150–300ms。
- [Android 系统跨 Activity 返回](https://android.googlesource.com/platform/frameworks/base/+/f68c80b4e9dbdb1e5bf6620f437c3d9f865eb047/libs/WindowManager/Shell/src/com/android/wm/shell/back/CrossActivityBackAnimation.kt)及[系统窗口圆角计算](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/policy/ScreenDecorationsUtils.java)：根据设备窗口/屏幕的像素圆角裁剪，而非固定主题dp圆角。
- [AOSP Launcher3 回桌面实现](https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/quickstep/src/com/android/quickstep/LauncherBackAnimationController.java)：原始进度经过插值，纵向随触点减速移动；从系统窗口圆角过渡到Launcher自己的目标圆角。这里的回桌面缩放下限是85%，不是应用内Material预览的90%。本次通过Gitiles文本接口读取了实际源码。

## CourseTable 的调整

- 保留系统的原始返回进度作为输入，在绘制层映射到减速曲线，不增加逐帧重组或手势延迟。
- 全屏预览最小缩放0.9。横移根据缩小后可用空间计算，保持整个页面在屏幕内，远侧留边8dp；不再使用会把另一侧推出屏幕的12%屏宽横移。
- 纵向跟随触点，使用减速映射，受到可用空间及24dp上限限制。
- 在Android 12及以上使用公开的窗口四角像素半径，旋转/窗口布局变化时更新。不再使用 `28dp × 手势进度`；未提供圆角信息的窗口保持其方角特征。
- 提交从当前姿态继续轻微缩小并淡出，根据进度/释放速度在150–300ms内收尾；取消使用较紧的阻尼弹簧恢复。
- 背景继续覆盖整个窗口，系统栏留白只在内容内部。
- 二级页面使用与玻璃弹窗相同的主题遮罩；预测性返回预览期间保留至少一半遮罩强度，提交时随页面淡出，取消时恢复。

当前连接手机的只读窗口信息显示四角半径134px，物理密度640dpi，即约33.5dp；这些值仅用于核对，没有硬编码进应用。

## 限制

ColorOS等厂商的桌面回退可能有私有圆角修正、图标目标和弹簧参数。即使AOSP Launcher3也有单独的目标圆角资源；窗口四角半径不等于桌面动画的所有参数。普通应用不能通过公开API完整读取所有桌面动画参数，因此按设备窗口圆角和官方几何对齐，但不能承诺与厂商回桌面逐帧完全一致。系统时钟、电量图标仍由系统绘制。

验证只使用明确指定的独立模拟器，不对连接真机运行Gradle全设备测试，也不自动卸载或安装真机应用。

## 2026-10-24 连续性优化

- 返回状态区分静止、跟随、恢复、提交和完成。每次手势持有独立代次；新手势、失去返回层资格或页面卸载后，旧动画不能继续写入或触发确认。
- 回弹由可取消的组合生命周期任务驱动，不再使用整段 `NonCancellable`。同边、换边和同帧取消后重启均从当前进度、横移方向和纵向偏移接管；换边方向连续插值，不直接镜像翻转。
- 记录带符号的速度，恢复弹簧接上释放速度。拖动继续直接更新绘制层；提交保留原有150–300ms收尾范围，系统动画倍率为零时不等待动画。
- `canCommit`、`canDismiss` 只判断退出资格，不改进度或打开弹窗。`ModalBottomSheet.onDismissRejected` 在预测回弹完成后通知；普通关闭被拒绝时立即通知。被新手势接管或卸载的旧通知作废。
- 后代预测退出的完成信号在当前组合提交中消费；只有同时退出的祖先跳过普通动画，不影响保留的祖先之后再执行普通返回。
- 外部文件页改用统一的 `PredictivePageTransition`，保留根页面，缓存退出期间的导入入口。根导入与外部导入使用独立的 ViewModel key，避免覆盖保留页面的草稿；不改导入数据模型。
- 子页按实际挂载生命周期通知底部导航栏。预览、取消和普通退出尚未结束时，导航栏保持隐藏；页面退出完成后恢复，保留原有出现动画。

### 回归方法

- `PredictiveBackContinuityTest`：用可控帧时钟检查恢复中间帧、不同进度和边缘、换边接管、同帧重启、负速度、确认通知时序、卸载和零倍率。
- `NativePredictiveBackTest`：独立模拟器系统边缘手势，原生 `SOURCE_TOUCHSCREEN` / `TOOL_TYPE_FINGER` 输入及 Choreographer 真实渲染时钟；检查左右取消、提交一次，以及退出被拒绝后的换边接管。
- `ExternalImportBackTest`：实际 MainActivity 接收 ICS 文件，取消保留预览、提交没有第二段退出，并验证根导入草稿未被覆盖。
- 同时回归原有预测返回、根导航、弹窗菜单、课程编辑和两套键盘焦点测试。原生事件测试不混用 Compose 模拟时钟。

Android 14 的 [BackProgressAnimator](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/window/BackProgressAnimator.java) 会先通过进度事件将取消动画驱动到零，再通知应用 `onBackCancelled`。因此系统取消过程不能以“应用进入非零 RESTORING 阶段”为前置条件；原生接管测试使用被退出检查拒绝的提交，验证应用自己的非零回弹。

### 验证记录

2026-10-24，独立 Android 14 / API 34 模拟器 `emulator-5580`：

- 单元测试197项通过，失败、错误和跳过均为0；Lint为0错误、59警告、6提示。
- 分批汇总98个不同设备用例通过：返回及外部导入22项、根导航46项、键盘焦点11项、菜单及课程编辑19项。原生测试使用真实渲染时钟。
- 验证期间 headless 模拟器宿主发生 `0xc0000005` 访问异常；中断项没有计为通过。改用隐藏窗口的完整模拟器宿主后，补齐剩余7项编辑器测试，全部通过。
- 通过项目脚本生成 `1.8.3-preview` / b248 / arm64-v8a，签名、包名、版本、ABI和SHA-256检查通过；在同一独立模拟器更新安装并冷启动成功。未创建正式发布或安装到真机。
- 测试输出直接写入电脑 `.artifacts/predictive-back-*.log`，逐项通过清单为 `.artifacts/predictive-back-passed-tests.txt`。没有创建设备截图、UI XML或录屏文件；模拟器显示尺寸、密度已恢复原值。
