# CourseTable

CourseTable 是一款 Android 课程表应用，帮助你查看每周课程、管理多个课表，并从教务系统或已有文件导入课程。课程与设置保存在设备本地，日常使用不需要在线账号。

## 主要功能

- **查看课表**：按周浏览课程，切换课表和周次，查看上课时间、地点、教师及单双周安排。
- **管理课程**：新增、编辑和删除课程；自定义学期起始日、总周数、节次、主题和课程提醒。
- **多个上课时段**：导入时自动归并同一课程的不同时段；编辑页统一管理各时段的时间、周次和教室，共享课程名称、教师和颜色。
- **时间方案**：保存多套节次时间和每节课时长，在“节次时间”窗口中编辑方案，并用“选用”切换当前课表的作息。
- **导入课程**：支持教务系统、ICS、Excel/CSV、PDF 和图片。图片与 PDF 识别结果会先进入确认清单，可对照原图校对、调整识别范围或手动补充课程。
- **追加与冲突处理**：导入前逐条勾选，重复默认跳过，同名新时段可归入已有课程；时间冲突显示重叠周次和节次，可修改后导入或仍然保存。
- **导出与迁移**：导出 ICS 日历，使用应用内 JSON 备份恢复课表。
- **提醒**：按设置发送课前提醒；无法使用精确闹钟时自动采用兼容的提醒方式。

## 下载

安装包可在 [GitHub Releases](https://github.com/liondoge123/CourseTable/releases) 下载。应用最低支持 Android 8.0。

## 数据与隐私

课表和设置默认保存在本机。应用不提供云同步；换机时请先在旧设备导出 JSON 备份，再在新设备恢复。使用教务系统导入时，应用会连接所选学校的登录与课表服务。

图片和 PDF 的识别效果受清晰度、排版与字体影响。导入前请核对课程名称、时间、周次和地点。完整液态玻璃折射效果需要 Android 13 及以上系统；其他系统会使用兼容样式。

## 开源致谢

- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 提供 Backdrop 液态玻璃渲染库与 Compose 交互参考，采用 [Apache License 2.0](https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/LICENSE)。
- [Kyant Shapes](https://github.com/Kyant0/shapes) 提供连续曲率形状，采用 [Apache License 2.0](https://github.com/Kyant0/shapes/blob/master/LICENSE)。
- [Lucide Icons](https://lucide.dev/) 提供界面图标，采用 [ISC License](app/src/main/assets/licenses/lucide-icons.txt)。

CourseTable 采用 [Apache License 2.0](LICENSE)。第三方组件和资源仍遵循各自的许可证；项目名称与品牌标识不包含在许可授权中。
