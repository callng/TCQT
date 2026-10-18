# TCQT 界面规范

本规范以 TCQT 的实际入口、宿主环境和 Miuix **0.9.4** 为准。

## 范围与目标

TCQT 是嵌入 QQ/TIM 的工具模块。优先保证设置容易寻找、状态真实、操作明确、
键盘和小窗口下内容可达。界面提供信息层级，不用每个开关一张卡片的方式制造层级。

以下入口使用同一套设计：

| 入口                                 | 实现                                                      |
|--------------------------------------|-----------------------------------------------------------|
| 设置首页、分类、功能、搜索、异常列表 | `ui/settings/SettingScreen.kt`                            |
| 通知渠道管理                         | `ui/settings/NotificationChannelManagerActivity.kt`       |
| 清空、导出、重启等确认               | `ui/component/UiDialog.kt` 的 `TCQTAlertDialog`           |
| 备份与还原                           | `ui/settings/SettingActivity.kt` 的 `BackupRestoreDialog` |
| GUID 编辑、资料卡、敏感信息确认      | `features/advanced/*Dialog.kt`                            |
| 消息 JSON 查看与搜索                 | `features/chat/MsgDetailDialog.kt`                        |
| 群管理菜单与子表单                   | `features/menu/troop/`                                    |
| DexKit 扫描进度、结果                | `ui/component/DexKitScanDialog.kt`                        |
| KernelSU/APatch WebUI                | 原生 HTML/CSS/JS，源文件位于 `zygisk-template/webroot/`   |

宿主原有的聊天气泡、导航条、系统应用选择器和系统 Toast 保留各自的平台语义。
WebUI 因模块体积预算使用原生 DOM 和系统字体；按参考图采用白底、浅灰细边框卡片、
柔和绿色开关和浅绿状态标签，标题放在卡片内部，减少装饰与字体粗细层级，
不引入 Compose/Skiko、WASM 或字库。Android 设置界面继续使用真实 Miuix 组件。
WebUI 主题颜色集中在 CSS 变量；内容宽度上限 800px，手机边距 12px，跟随系统浅色/深色，
使用语义化表单、可见键盘焦点、实时错误反馈，读取失败不得显示为“未安装”。
KernelSU WebUI 显式启用 `enableEdgeToEdge(true)`，保持系统栏可见，以管理器注入的安全区变量
避让状态栏、导航栏和横屏缺口，浏览器 `env()` 仅作回退。外部链接由模块脚本调用前台用户的
默认浏览器，不能依赖 WebView 的 A 标签或 `target="_blank"`。
`:webui:assembleWebUi` 检查 HTML/CSS/JS/SVG 总量不超过 64 KiB，并自动合并进双格式 APK。
构建不需要 Node/npm；勿提交 `webui/build/`。`UiLayout.kt` / `UiDialog.kt` 仅供 Android 使用。
日志导出应打开系统分享面板，附件使用 MediaStore `content://` 和临时读取授权，禁止共享 `file://`。
`share-logs.sh` 在 root 下收集日志，`LogShare` 以 shell UID 调用 Android 分享；不依赖 APK 安装。

## 颜色、文字与形状

- 直接使用 `MiuixTheme.colorScheme`、`MiuixTheme.textStyles`。不再提供 Material 名称的主题映射。
- 页面底色用 `surface`，分组卡片用 `surfaceContainer`，次要文字用 `onSurfaceSecondary`。
  `primary` 表达主操作或选中，`error` 表达错误或破坏性操作；开关关闭不等于错误。
- 主操作的文字颜色用 `onPrimary`，错误操作用 `onError`。禁止页面内写十六进制颜色或固定黑白文字。
  消息 JSON 的语法和搜索配色也从当前主题派生，不能独立跟随宿主的深色判断。
- 普通设置行的字号、内边距、禁用态交给 Miuix Preference。分组标题用 `SmallTitle`；
  弹窗标题用 `title4`，说明用 `body2` 或 `footnote1`。
- 静态信息卡用 Miuix `Card` 或 `TCQTPanel`；连续分组用 `PreferenceSegment`。
  常规卡片圆角 16dp；小标记可以更小。自绘轮廓使用 `squircleBackground` / `squircleSurface` / `squircleClip`。
  真正圆形的头像或装饰允许 `CircleShape`。不要重复裁剪已由 Card 裁剪的内容。

## 页面与分组

- `Scaffold` + `TopAppBar(scrollBehavior)` + `LazyColumn`；列表接入顶栏的 nested scroll 和 Miuix overscroll。
  不再手工拼一个只有图标、没有页面标题的顶栏。返回用 `MiuixIcons.Back`。
- `AdaptiveContent` 提供水平边距：手机 12dp，宽窗口居中、内容宽度最多 800dp。
  列表本身保持全宽，边缘区域也可以滚动。使用 `horizontalSafePadding()` 避让横屏缺口。
- 消费 Scaffold 的 top/bottom padding；底部保留 24dp，保存悬浮按钮出现时额外让位。
  系统栏 inset 只消费一次；输入列表使用 `imePadding()`。
- 一个语义分组是一张连续卡片。分类行使用 `ArrowPreference`，直接布尔项使用 `SwitchPreference`；
  带子设置的功能行可以组合 `BasicComponent` 与 `Switch`，点击行展开、点击开关只切换开关。
- 每个分类或功能是独立 lazy item，使用功能 key / 分类完整路径作稳定 key。
  通过 `PreferenceSegment(first, last)` 拼接首末圆角，不把整个功能目录放进一个 lazy item。
  单个功能的有限子表单可以随该行展开，长日志保持可选择复制。
- 单选用 `RadioButtonPreference`，多选用 `CheckboxPreference`，数值用 `SliderPreference`。
  不给每个子选项再套一张不同底色的卡片。滑块保存值必须经过 `normalizeSliderValue`。
- 搜索使用 Miuix `InputField`，保留其低版本 Android 的焦点修复。空查询只显示提示/历史；
  搜索、高亮统一使用原字符串的忽略大小写匹配，不通过转小写字符串计算偏移。
- 设置仍是“修改 → 保存 → 重启生效”。行内标识未保存，悬浮按钮显示待保存数量。
  分类和页面切换不得清除草稿。运行异常、初始化条件不满足和未保存要分别表达。

## 弹窗与宿主窗口

- Activity 内使用 `WindowDialog`；简单确认走 `TCQTAlertDialog`，正文可滚动、操作固定在底部。
- 从宿主 View 打开的界面继承 `CompatibleComposeDialog`。基类只维护一个兼容的 Android 窗口和
  生命周期，内部透明 `Scaffold` 承载 `HostDialog`（Miuix `OverlayDialog`）。
  不再重复实现遮罩、阴影、手写入场动画，不在这个窗口里再创建 `WindowDialog`。
- 群管理使用 `OverlayBottomSheet`。由外层统一补 bottom system bars / display cutout；
  IME 由 Miuix sheet 处理，子页面不得再重复加导航栏或键盘 inset。
- 表单用 `DialogForm`：最大内容高度 560dp、内边距 24dp、正文滚动、按钮固定。
  宿主弹窗最大宽度 560dp，Miuix 负责窄屏底部呈现和宽屏居中。
- 操作顺序是取消在前、确认在后；三操作表单可用“恢复 / 取消 / 保存”。
  同行按钮等权，间距 12dp。确认使用主色，取消使用中性色；敏感复制等操作可用错误色。
- 输入无效时保留弹窗和原输入，在输入旁显示原因。只有校验成功才关闭并执行回调。
  不要把“应用主题”“选择备份目录”等重要能力只藏在长按里。
- `dismiss()` 仅请求关闭；Miuix 的 `onDismissFinished` 调用 `dismissImmediately()` 释放窗口。
  不使用固定毫秒数猜测动画长度。Activity 销毁/扫描窗口切换时直接释放；组合随生命周期销毁。
- 进度 Flow 使用 `collectAsStateWithLifecycle()`。扫描期间遵守 `canDismiss`，结束后提供关闭或重启操作。

## 维护与验收

新增界面先复用 `UiLayout.kt` / `UiDialog.kt` 和 Miuix 原生组件，再判断是否需要新封装。
`TCQTPanel` 复用 Card 的平滑圆角和配色，但保留 Box 的尺寸约束以便图标居中，`ActionButton` 只是支持内容槽的 Miuix Button；
避免再建立一套仿其他设计系统的组件或主题 API。

提交前运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

设备验收覆盖浅色、深色、Monet、横屏/窄窗口、大字体、键盘打开、长说明和长日志；
检查分组首末圆角、返回/遮罩关闭、草稿保存、无效输入不退出、所有底部按钮可触达。
确认扫描不可关闭阶段、通知默认渠道不可删除、删除失败有反馈。
宿主注入环境仍需在 QQ/TIM 实机验收，普通编译或组件预览不能代替 Hook 运行验证。

文档来源：本次通过 miuix-mcp 查询 Theme、Scaffold、SwitchPreference、WindowDialog、
OverlayBottomSheet、SliderPreference、Squircle 与 Best Practices，并核对本地 0.9.4 源码 API。
