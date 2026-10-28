# 后台课程提醒对照与改进（2026-10-27）

本文保留 b287 阶段的排查与实机记录。后续可选系统日历兼容路线见 [两种提醒方式](REMINDER_METHODS.md)。

## 问题与证据边界

用户反馈：一加 12，通知、横幅等权限已开启，按 Home 返回桌面后无法及时收到通知，重新打开应用立即收到。没有划掉最近任务。

初次排查至 b285 打包时未连接手机，未直接归因。随后用户连接一加 12，实机复现并抓到系统冻结、闹钟对齐及投递时间证据，见下文。按 Home 本身不应使已注册的系统闹钟失效。

## 源码对照

以下仅参考设计，未复制第三方实现；源码下载在忽略目录 `.artifacts/reminder-research/`。

| 项目与检查版本 | 实际做法 | CourseTable 的取舍 |
| --- | --- | --- |
| [Sleepy](https://github.com/lingion/sleepy/tree/baa310d9748c16634b60f7d032e110698f78a673)，`BackgroundReliabilityProbe.kt`、`CourseNotificationScheduler.kt` | 查询电池优化豁免；普通课前提醒使用 `setExactAndAllowWhileIdle`，无权限时退回非精确闹钟；更新后重新注册 | 保留已有精确/非精确路径；补电池状态诊断、更新恢复 |
| [Etar](https://github.com/Etar-Group/Etar-Calendar/tree/4e3a94d9975b40ee41131608244ca40c68d4a900)，`AllInOneActivity.java`、`alerts/AlertUtils.java` | 查询电池优化状态并提供授权流程；提醒使用允许空闲时执行的精确闹钟 | 提供系统电池优化设置入口，由用户主动调整；不在启动时弹出申请 |
| [Fossify Calendar](https://github.com/FossifyOrg/Calendar/tree/fa97ebcb6ca22741a7466e9b5e3591c2b0058da5)，`receivers/RescheduleEventsReceiver.kt`、`extensions/Context.kt`、Manifest | 时间、时区变化和应用更新后恢复；精确闹钟及权限降级；恢复任务交由后台任务处理 | 补系统事件恢复；继续使用已有 `goAsync` 与 `finally.finish()` 完成短时重排 |

共同点是让系统持有定时任务。CourseTable 已使用这条路径，不能只因其他项目也使用同一 API，就推断它们能解决这台一加的故障。初次对照保留普通提醒路径；后续实机确认普通精确请求仍被系统放宽后，另提供默认关闭的系统闹钟选项，明确告知状态栏闹钟标记。普通单次通知无需引入持续运行的前台服务。

Android 依据：[系统闹钟](https://developer.android.com/develop/background-work/services/alarms)、[限制后台运行](https://developer.android.com/topic/performance/background-optimization)、[后台限制查询](https://developer.android.com/reference/android/app/ActivityManager#isBackgroundRestricted())、[电池优化豁免查询](https://developer.android.com/reference/android/os/PowerManager#isIgnoringBatteryOptimizations(java.lang.String))。

## 本次实现

- 权限检查增加标准后台限制和电池优化状态。从系统设置返回时刷新。一加/OPPO 的后台活动开关须在应用耗电管理中确认，公共 API 无法穷尽厂商策略，因此“未检测到标准限制”不能显示成“后台完全正常”。电池优化未豁免也不直接判定为通知失败。
- 应用通知检查同时读取应用级通知开关，兼容 Android 13 之前的系统。
- 开机、重新授予精确闹钟权限、应用更新、系统时间或时区变更后重排。继续使用广播生命周期内的异步重排。
- 记录闹钟计划时间、接收时间、观察到的延迟，以及通知被关闭、渠道被关闭、提交失败或已提交系统。已提交系统不等于已出现横幅。
- 测试提醒保留独立的待触发/结果状态，正式提醒不会覆盖它；旧测试的迟到广播不会覆盖新测试。排程失败显示失败，不再提示“已排定”。
- 测试通知改为“测试提醒已触发”，删除“正式提醒将按时送达”的保证。
- b287 增加默认关闭、可持久化的“准时优先”选项。启用后正式课程与测试提醒都用 `setAlarmClock`，界面明确说明状态栏闹钟标记；关闭后仍使用原路径。精确权限缺失或注册被拒绝时继续保留非精确降级并记录原因。没有新增常驻服务。
- 原安全测试中一律禁止 `setAlarmClock` 的断言改为验证该模式默认关闭，保留能力检查及降级检查；另覆盖系统闹钟注册与权限竞态降级行为。

## 验证

本地检查：240 项 debug 单元测试通过，Android 测试源码编译通过，debug lint 无错误。`ReminderRecoveryRegistrationTest` 只完成编译，未通过 instrumentation 在该手机执行；下文实机用原生 ADB 手势、真实时钟及系统日志验证，未混用 Compose 模拟时钟。

新增 `ReminderRecoveryRegistrationTest` 检查合并清单中的恢复广播是否能找到接收器。它验证注册，不能证明系统实际投递或手机后台提醒的时效。

## b284 用户反馈与后续诊断

用户截图显示计划 `10-27 13:20:44`、接收 `13:23:16`，毫秒级计算后显示触发延迟 `151 秒`，通知已提交系统。用户确认：应用在后台时横幅自行弹出，没有连续点击测试按钮。故此次已观察到后台执行，但准时性尚未解决；不能将“最终收到横幅”视为验证完成，也没有证据认定由重复测试触发频率限制。

b284 没有记录实际调用的调度路径。当前权限开关不能证明某条旧闹钟当时使用了精确调度。后续补充：

- 调度成功后分别记录“精确提醒”“排程时无精确权限的非精确提醒”“系统拒绝精确排程后的非精确降级”。路径写入该闹钟的 PendingIntent，随广播返回，普通课程与测试提醒一致。
- 测试待触发时显示实际采用的调度方式；触发后也保留方式。不根据接收时的权限状态倒推。
- 老版本未包含该信息的闹钟明确显示未记录，不默认视为精确。
- 单元测试覆盖精确成功、权限缺失、权限竞态拒绝、非权限异常、降级本身失败；调度策略保持不变，本次是诊断补充，不是已验证的延迟修复。

## 一加 12 实机证据与 b287 结果

设备：OnePlus PJD110，Android 16 / API 36，固件 `PJD110_16.0.10.501(CN01)`。实际安装 b285 后做基线，b286 做权限声明实验，最后更新至签名一致的 b287。测试时通过 USB 充电。

已确认通知权限允许、渠道 importance=4、横幅显示允许、`SCHEDULE_EXACT_ALARM: allow`。原耗电选项是“智能优化后台运行”，标准后台限制查询仍显示未限制。实际日志中回桌面约 4 秒出现 `OplusHansManager freeze`，随后 `OplusAlarmAdjustment alignAlarmsByUid`，证明标准后台限制查询无法完整反映厂商行为。

| 组合/场景 | 计划时间（北京时间） | 实际证据 | 判定 |
| --- | --- | --- | --- |
| b285 普通精确提醒 + 智能优化，回桌面 | 15:51:38.588 | 系统通知创建 15:53:16.368；应用记录晚 97 秒；系统记录冻结、闹钟对齐、到点解除冻结 | 复现延迟 |
| b285 普通精确提醒 + 完全允许后台 | 15:57:01.302 | 系统通知创建 15:57:23.861；应用记录晚 22 秒；无新冻结对齐，但系统仍将零窗口请求登记为约 22.49 秒窗口，正式课程窗口达 1 小时 | 只放开后台不足以准时 |
| b286 额外声明 USE_EXACT_ALARM | — | 权限实际 granted、exactAllowReason 变为 policy_permission，但正式课程仍为 1 小时窗口 | 声明差异未解决窗口问题，已撤回实验声明 |
| b287 准时优先 + 完全允许后台，回桌面 | 16:13:23.161 | 系统投递 16:13:23.163；通知创建 16:13:23.204；SystemUI 记录横幅 pinned | 通过，通知创建晚 43 毫秒 |
| b287 准时优先 + 恢复智能优化，回桌面 | 16:16:14.604 | 系统再次冻结并把闹钟对齐到更晚时间；计划过去后仍未投递。返回前台导致解除冻结，应用记录晚 14 秒 | 失败，本轮不是独立后台完成；系统闹钟也不能绕过该厂商冻结策略 |
| b287 准时优先 + 完全允许后台，锁屏 | 16:18:11.003 | 系统投递 16:18:11.005；通知创建 16:18:11.038；设备处于 Dozing | 通过，通知创建晚 35 毫秒 |
| b287 准时优先 + 完全允许后台，stop-app 后锁屏 | 16:20:15.065 | 命令执行后 PID 为空、stopped=false、原闹钟仍存在；16:20:15.066 系统投递，启动新 PID 6978 执行 ReminderReceiver；通知创建 16:20:15.173 | 通过，通知创建晚 108 毫秒 |

`am kill` 未使本机进程消失，因此没有把那次尝试报告成正常回收验证。最后一轮使用手机命令帮助和 [Android 官方文档](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)说明的 `cmd activity stop-app`，该命令保留闹钟和 jobs。它验证了无进程时的提醒冷启动，不等同于覆盖所有内存回收情形，也未使用会取消闹钟的 force-stop。

最终保留 b287、“准时优先”开启、课程表耗电管理“完全允许后台行为”。正式课程 11 个提醒均重新登记为系统闹钟零窗口。其他应用及系统全局省电策略未更改。未保留 b286 的 USE_EXACT_ALARM 实验声明。

APK：`CourseTable-v1.9.1-preview-b287-arm64-v8a-preview.apk`，包名 `com.coursetable.app`，versionCode 287，已验证正式签名证书、ABI 及 SHA-256：`a6fed94f1746c3e9a01cdcfd1e3da801cba6dd8c9dadd3a4a3d244e6e55caae1`。

设备证据保存在忽略目录 `.artifacts/reminder-device-20261027-b285/`，含各轮 `*-alarms.txt`、`*-notifications.txt`、`*-logcat.txt` 和 UI XML。手机仅创建本次目录 `/data/local/tmp/coursetable-testing/20261027-reminder-b285-01/` 及其中的 `ui.xml`，结束时核对并清理。未把截图、XML 写到共享存储。

本次没有覆盖长期拔线待机、重启后不打开应用、所有厂商系统版本或未来系统更新；不能把几次短时通过扩展为长期准点保证。后续重点补充这些场景。
