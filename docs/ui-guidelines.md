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
| 备份还原                             | `ui/settings/SettingActivity.kt` 的 `BackupRestoreDialog` |
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
`share-logs.sh` 在 root 下收集日志，独立的 `logshare.dex` 以 shell UID 调用 Android 分享；
不依赖 APK 安装或 `main.apk` 成功加载。分享失败时保留 `/data/adb/tcqt/last-share-report.txt`，
错误信息应包含保留路径。导出同时包含开机载荷激活结果和实际载荷哈希。

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
  固定搜索框页面只保留列表滚动和 overscroll，不创建或接入折叠顶栏的 scrollBehavior，
  避免未测量的折叠范围吞掉滚动；切页动画中的旧列表不得驱动新页面的顶栏。
  不再手工拼一个只有图标、没有页面标题的顶栏。返回用 `MiuixIcons.Back`。
- `AdaptiveContent` 提供水平边距：手机 12dp，宽窗口居中、内容宽度最多 800dp。
  列表本身保持全宽，边缘区域也可以滚动。使用 `horizontalSafePadding()` 避让横屏缺口。
- 消费 Scaffold 的 top/bottom padding；列表末尾保留 24dp。保存栏使用 Scaffold `bottomBar`，
  由测量结果自动为列表让位；导航栏 inset 只消费一次。`imePadding()` 放在 Scaffold 上，键盘出现时仍可保存。
- 一个语义分组是一张连续卡片。分类行使用 `ArrowPreference`，直接布尔项使用 `SwitchPreference`；
  带子设置的功能使用箭头行进入独立详情页，尾部显示启用状态；详情页首行是总开关。
  行内统一使用 16dp 水平/垂直边距，说明采用 body2，不用装饰图标和状态药丸挤占正文。
- 每个分类或功能是独立 lazy item，使用功能 key / 分类完整路径作稳定 key。
  通过 `PreferenceSegment(first, last)` 拼接首末圆角，不把整个功能目录放进一个 lazy item。
  详情页的选项、滑块和文本域也按字段拆成稳定 lazy item；长日志保持可选择复制。
  每个页面分别保存列表位置和大标题折叠状态；返回详情页来源时保留搜索条件。
- 单选用 `OverlayDropdownPreference` 的锚定勾选菜单，多选用 `CheckboxPreference`，数值用 `SliderPreference`。
  不给每个子选项再套一张不同底色的卡片。滑块保存值必须经过 `normalizeSliderValue`。
- 搜索沿用 Miuix `InputField` 的外观；0.9.4 的 String 接口无法指定选区，因此展开态使用
  `BasicTextField(TextFieldValue)` 保留完整编辑状态。历史词等外部填入将光标置于末尾，
  普通输入、手动选择和中文组合输入不得强制移到末尾。搜索框仅在展开时挂载，进入时请求焦点，
  关闭时移除，避免低版本 Android 折叠态焦点重分配问题。空查询只显示提示/历史；
  搜索、高亮统一使用原字符串的忽略大小写匹配，不通过转小写字符串计算偏移。
  保留默认前置搜索图标及内边距，不能用空 `leadingIcon` 槽移除留白；验收空输入、输入和清空后的光标位置。
- 设置仍是“修改 → 保存 → 重启生效”。行内标识未保存，仅有草稿时显示底部主按钮和待保存数量。
  保存按钮最小高度 52dp、图标 20dp，文字/图标都用 onPrimary，不使用无尺寸约束的 FAB 内容。
  主题设置和备份入口放在首页的通用分组；分类页不重复反馈按钮和版权页脚。
  首页顶部只展示已启用、未启用、功能总数与异常入口；三列等宽，数字与标签在列内居中，顶部对齐。
  通用分组的「关于」进入独立页面。
  关于页的项目名、模块版本和构建时间取 `TCQTBuild`，宿主版本、渠道和注入方式集中放在这里；
  查看源代码打开项目仓库，获取更新点击打开 GitHub CI 工作流、长按打开 CI 频道，不显示额外说明。
  反馈问题点击打开 Issues、长按打开 Telegram 群组；其下提供独立「TG 群组」入口。
  关于页不显示图标，名称与版本居中，保留连续 Preference 分组。
  背景采用 OS3 着色器与粉、紫、蓝循环配色；色块持续移动，离开前台暂停，
  关于页中央项目名取其背后的同一渐变，使用参考项目的浅／深色混色参数填充字形，随背景同步变色；
  系统关闭动画时保持静态，Android 13 以下回退为同色系静态渐变。
  透明顶栏显示同一背景，列表视口在顶栏下裁剪，滚动内容不得穿过标题或系统栏。
  不添加许可证列表、调试开关或重复的操作说明。
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
- DexKit 扫描显示总进度和当前功能进度，功能名称用 `primary` 高亮；进度按查找项计数，
  共用项只计入一次总进度。总耗时和当前功能耗时独立于查询完成事件刷新，结束后保留总耗时
  和超过 1.5 秒的最慢五个功能，没有符合项时隐藏整个耗时列表；准备、查找、保存阶段由扫描流程明确切换。

## 维护与验收

聊天分组栏参考 Telegram `FilterTabsView`：文字和角标决定自然宽度，空间足够时把剩余宽度均分给各标签，
空间不足时横向滚动；常规选中背景高 28dp，整栏高 44dp，大字体按实际文字高度适配。
默认浅色／夜间主题下，分组栏整块底层沿用 QQ 原生搜索栏的导航区背景色，
胶囊两侧和上下留白与头像、搜索区连续。自定义主题下外侧透明，让 QQ 的背景图按原坐标连续显示；
胶囊填色取主题搜索框内部颜色，文字、选中态和角标取宿主搜索文字色，避免固定蓝色和白底。
宿主搜索背景更新时同步重取配色，不把主题图片拉伸成另一块背景。
角标显示未读会话数；不常驻尾部菜单按钮，不在固定头部中放空状态文案。
长按打开锚定的 Miuix 菜单，进入排序模式后才拖动，排序时右侧提供完成按钮。
点击区域保留整栏高度，按压反馈放在胶囊轮廓内裁剪，不能在标签外出现矩形遮罩。
内置分类的编辑入口打开独立的文件夹管理页，自定义文件夹直接进入独立编辑页；“添加聊天”打开选择页。
这些宿主文件夹页面使用 Miuix `SmallTopAppBar`，遵循 Telegram 的紧凑导航层级；保存栏消费导航栏 inset。
长按自定义文件夹支持直接删除确认，编辑页也有删除入口。
批量标记已读按文件夹筛选完整会话列表（含折叠会话），普通聊天和频道分别调用 QQ 原生接口，由宿主回调更新列表与角标。

新增界面先复用 `UiLayout.kt` / `UiDialog.kt` 和 Miuix 原生组件，再判断是否需要新封装。
`TCQTPanel` 复用 Card 的平滑圆角和配色，但保留 Box 的尺寸约束以便图标居中，`ActionButton` 只是支持内容槽的 Miuix Button；
避免再建立一套仿其他设计系统的组件或主题 API。

悬浮底栏保留 QQ 原生 Tab 的图标、标题、角标与点击行为。原生底栏的位移、透明度、
可见性同时驱动表面、选中胶囊、投影和触摸容器；下拉小程序完全展开时外层用 INVISIBLE，
保持悬浮容器的固定测量高度，收起后恢复。底栏移动或隐藏时暂停页面延伸，避免修改小程序页面的布局。
两套实现都在 QQ 主题刷新前交还原生视图树，保留 QQ 当前动画状态，刷新后按新的视图重新安装；
小程序尚未收起时等待原生底栏重新可见，不把这段等待算作安装失败。
普通表面和玻璃色垫在安装时采样原生底栏背景，描边、选中底色从未选中标题的实际文字色派生；
主题文字色识别排除角标。原生图标和角标的背景保留，液滴允许黑白选中色并在主题刷新后清空旧强调色。
设备验收覆盖默认、深色、自定义主题及其来回切换，下拉一半、完全展开、收起和连续重复；
同时检查底栏隐藏后小程序底部可点击、原生 Tab 与角标恢复、底栏位置和缩放保持。

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
