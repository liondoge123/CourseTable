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
