# 按钮样式约定

依据 Apple 现行 HIG 和 Liquid Glass 指南，结合 Android 界面的组件结构。统一同类组件的规则，不要求工具栏、正文和弹窗使用同一种轮廓。

## 形状与布局

- 正文操作和弹窗操作统一使用 `LiquidButtonShape` 胶囊形；工具栏图标可使用圆形。表单输入框、分段控件和内容卡片保留各自的形状。
- 标准居中弹窗和自定义管理窗口中的同组按钮保持相同高度和胶囊轮廓。
- 按钮点击区域至少 48 dp。按钮组通过色彩和材质表达主次，不靠缩小次要按钮。
- 弹窗超过两个操作时，使用 `additionalActions` 逐项纵向排列，主要操作在上；不要把多个按钮放入同一个底部按钮槽。

## 角色与颜色

- `Primary`：当前任务的主要操作，如保存、确认、重试。采用主题 `primary` / `onPrimary`，不固定为蓝色。
- `Secondary`：取消、返回等辅助操作。采用淡淡的半透明中性色背景及可读文字，不额外添加描边；同组主要操作更醒目。
- `Destructive`：删除、清空、覆盖已有内容、放弃修改。采用淡红背景及 `error` 文字，禁止红字叠普通主色背景；提供安全退出操作。
- 点选立即生效的选择面板，只有一个底部取消/完成按钮时，可以保留独立的强调胶囊按钮。不要仅凭标题是“取消”就降级样式。
- 同时存在明确主要操作和关闭按钮时，关闭使用次要样式。单个信息告知窗口用“完成”或“关闭”；取消用于放弃当前操作。
- 正文中的危险按钮使用 `DestructiveButton`；轻量删除图标/文字可以直接使用 `error` 色。

## 参考

- [Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons)
- [Alerts](https://developer.apple.com/design/human-interface-guidelines/alerts)
- [Action sheets](https://developer.apple.com/design/human-interface-guidelines/action-sheets)
- [Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets)
- [Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views)

这些约定是本项目对官方设计原则的应用，单个取消按钮的强调样式属于项目选择，并非 Apple 强制要求。
