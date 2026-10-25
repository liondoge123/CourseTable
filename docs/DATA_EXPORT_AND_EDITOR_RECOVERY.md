# 日历导出、文件处理与编辑恢复（2026-10-24）

## 日历导出

- 设置页仅查询和导出当前课表，入口说明同步明确范围。导出器也按有效的当前课表 ID 排除其他学期，防止混用开学日期和节次时间。
- 日期统一使用 `WeekUtils.dateOfWeek`，与课表网格采用同一周一至周日定义。例如开学日为 2026-09-01（周二），第 1 周周一应为 2026-08-31，而不是 2026-09-07。
- 回归覆盖七种开学日与七个课程星期的组合、双周安排往返，以及多课表隔离。

## 文件处理

- JSON 备份解析、ICS 解析与课程转换、JSON/ICS 导出序列化在 `Dispatchers.Default` 执行；文件读写继续使用 `Dispatchers.IO`，界面状态更新留在主线程。
- 备份课程分组以单次扫描建立键到本地编号的映射，替代每条记录重复扫描之前所有记录。无分组键的独立记录也分配独立编号，避免与后来的显式分组相撞。
- 回归验证混合独立记录/显式分组的边界，以及达到 2000 条上限时的分组、记录顺序和数量。
- 输出流无法打开时报告失败，不再显示成功；导出取消继续向上传播。

## 编辑恢复

- `CourseDraftState` 仅将课程字段和其他时段编码为 Bundle 支持的值。区分“只编辑当前时段”的 null 与“删除其他全部时段”的空列表；保存前重新读取冲突校对快照，不将整张课表的旧快照带入恢复状态。
- 课程管理页保存当前编辑对象；根页面通过 `SavedStateHandle` 保存编辑入口。编辑器保存完整时段草稿、当前时段和初始化完成标记，已有表单字段继续使用 `rememberSaveable`。
- 恢复已初始化的编辑器时不再从数据库覆盖草稿，因此未保存的新增、删除、切换时段和修改可以一起恢复。
- 该恢复依赖 Android 保存并恢复任务状态，覆盖 Activity 重建与系统回收后的状态恢复；不是用户主动清除应用数据后的持久备份。

新增设备回归：`CourseDraftRestorationTest` 检查多个未保存时段、当前选择、已删除时段不被重新加载、管理页编辑入口，以及 Bundle/Parcel 后新 ViewModel 的恢复与关闭。`RootEditorRestorationTest` 检查实际 MainActivity 重建后仍能编辑所有未保存时段。

## 验证记录

- 202 项 JVM 测试通过，0 failures / errors；Android 测试代码编译通过；最终 Lint 为 0 errors、61 warnings、6 hints。
- Android 14 独立模拟器最终两批共 44 项不同用例通过：草稿恢复 4、实际 Activity 编辑恢复 1、多时段编辑 12、导入事务 1、原生键盘连续性 1、外部点击与焦点 10、实际主导航 10、课程管理列表 5。
- 四项局部状态恢复测试使用静态材质，保留完整的多时段、删除、当前选中、入口和 Parcel 断言。实际 MainActivity 恢复及既有交互回归使用正常材质。Activity 恢复夹具使用 SetText 语义动作准备文本，避免混用 Compose 模拟时钟与原生 IME；原生键盘回归继续使用真实渲染时钟与原生手指事件。
- 早期三次运行被 Windows QEMU 宿主 `0xc0000005` 中断，没有计为通过；切换 NVIDIA 硬件 OpenGL 后完整完成。首轮实际 Activity 用例在重建之前的模拟时钟/IME 交互阶段失败，调整文本准备方式后保留所有恢复断言通过，没有为此修改产品焦点或键盘逻辑。
- 原始证据为 `.artifacts/optimization-fixes-final-build.log`、`optimization-fixes-final-lint.log`、`optimization-fixes-regressions.log`（25 项）、`optimization-fixes-navigation-management.log`（19 项）和 `optimization-fixes-host-failures.log`。

当前弹簧导航另完成 32 轮点击/滑动采样，结果见 [新版导航基线](ROOT_NAVIGATION_FRAME_PROFILE.md#当前弹簧方案的新基线2026-10-24)。没有连接物理手机，不将模拟器结果作为手机帧率结论。真实 OCR 样本仍只有 `selection-details.jpg`，未把合成变体记为新增真实样本。

## 预览产物与清理

通过 `pwsh scripts/build-apk.ps1 preview` 生成 `CourseTable-v1.8.3-preview-b249-arm64-v8a-preview.apk`（30,042,282 字节）。脚本已核验包名 `com.coursetable.app`、版本 `1.8.3-preview` / 249、minSdk 26、targetSdk 34、arm64-v8a、既有证书的 V2 签名，以及根目录副本与输出目录文件的 SHA-256 一致性。

SHA-256：`073ffb9d0b45e9fcdf4427b5cc4279dcff3055aec91027042b1e1d1f09d13546`。

该预览包在同一独立模拟器全新安装并冷启动成功（`Status: ok`、`LaunchState: COLD`，启动后进程存在）；未安装到物理手机或发布正式版本。完成后已移除测试组件、恢复原始 1080×2400 / 420 dpi 显示设置并关闭本次模拟器。整个会话未创建设备截图、UI XML 或录屏文件，无待清理的设备测试路径。
