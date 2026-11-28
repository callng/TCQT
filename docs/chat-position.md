# 记住消息位置

聊天分类中的 `remember_chat_position` 开关，默认关闭，仅对 QQ NT 的好友私聊（chatType 1）和群聊（chatType 2）生效。

退出聊天时保存首个可见消息的 msgId、msgSeq、相对列表顶部的像素偏移，以及最远已浏览的消息序号 readSeq。每个账号、会话类型、peerUid 分别保存，数据位于模块数据目录的 `chat_position/positions` FastKV 存储中，正常退出后重启 QQ 也可恢复。不保存消息正文。

普通进入聊天时先让 QQ 按目标加载历史消息，再用宿主布局管理器恢复顶部偏移。搜索、引用等带有明确消息 ID、序号或时间的跳转优先使用原生行为。每次退出都会更新浏览位置，包括通过搜索进入后继续浏览的情况。

恢复历史位置时，右下角气泡也恢复尚未浏览的数量。QQ 原生只统计本次进入后收到的消息，因此重新进入时需要补上历史范围：根据底部可见消息推进 readSeq，向上回看不倒退；每次进入只读取一条本地最新消息，以 QQ 原生的序号差口径计算 `max(latestSeq - readSeq, 0)`。收到当前会话的新消息时更新范围，浏览后减少，读到最新时归零。多选隐藏仍由原生气泡控制，归零时保留原生的已读确认。

气泡从零变为正数前，保留 QQ 原生的底部跟随判断：通过未读 VM 的无参 long 方法读取当前焦点，`-1` 表示跟随最新消息，此时先不发布正数计数，等待实际可见消息事件推进阅读进度。浏览历史时仍正常更新计数，不使用固定延时，也不把尚未显示的新消息提前记为已读。

启用本功能时，显示层使用 Miuix 圆形 Surface、向下箭头和正上方居中的 Badge；角标与箭头共用竖直中线，数量宽度变化时仍居中。圆盘视觉尺寸为 44dp，Surface 的 Compose clickable 保留至少 48dp 的触摸区域；箭头为 22dp，颜色取主题 onSurfaceContainer 的 55% 不透明度，阴影为 0.5dp，减弱常驻按钮的突出感。数量角标最小 22dp，默认字号下角标与圆形顶部重叠 8dp，外层留出角标与阴影空间；颜色跟随宿主浅色/深色及模块主题配色，按住时使用 Miuix 的变暗和 SinkFeedback 下沉反馈。保留原生 TextView 作为状态与点击入口，复制其布局约束和 ID，创建、状态更新及复用后同步显示，点击先处理阅读返回，其余点击转发原生 listener，随后再次同步。按钮沿用 QQ 包含数量的无障碍描述，角标不重复朗读。嵌入沿用 CompatibleComposeView 的独立生命周期，避免混用 QQ 的 AndroidX owner。

旧的三段存储格式仍可读取；没有 readSeq 的旧记录在首次恢复后，以实际底部可见消息建立阅读边界。搜索等明确导航不接管气泡计数，但仍记录阅读进度供下次普通进入使用。

### 向下按钮的阅读返回

尚有未读消息时，向上回看会保留离开阅读进度时的视口。第一次点击向下按钮，先回到这个位置，恢复顶部消息及其像素偏移；实际返回后再次点击，交给 QQ 原生点击行为继续到最新消息。手动向下读回该位置也算完成返回。继续阅读后再向上回看，使用新的阅读位置；期间收到新消息只改变计数，不移动返回点。已经读到最新消息时，继续使用 QQ 原生的向下按钮行为。

阅读返回点使用本次进入后实际显示的视口，独立于跨次保存的最远已读序号 readSeq。进入时即使停在很早的历史消息，向上回看后的首次点击也返回这次进入的位置及偏移；向下继续阅读会更新本次的返回位置，向上翻阅不覆盖它。readSeq 只负责累计未读数，不能作为返回目标。只保存原有的位置和 readSeq，不增加消息正文存储。

点击返回使用宿主 `NavigateBySeqEvent` 加载目标；目标已离开当前加载片段时也由 QQ 处理历史加载。沿用首次位置恢复的定位策略恢复偏移，跳过定位高亮。完成后继续显示剩余未读数量，点击本身不提前确认剩余消息已读。未触发阅读返回时，仍转发原生 listener，保留 QQ 的引用返回等行为。

设计参考 Telegram Android 官方源码，固定到 [21f7d109 的 ChatActivity.java](https://github.com/DrKLO/Telegram/blob/21f7d109da6ab3506c9e9577f98c6a38771ab335/TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java#L11130)：`onPageDownClicked()` 依次处理未读起点 `createUnreadMessageAfterId`、导航返回消息 `returnToMessageId`、最新消息；未读分隔行呈现后清除待返回起点（同文件 39068 行附近）。这里分别跟踪 QQ 的累计已读序号与本次浏览视口，按本次实际浏览位置恢复阅读，没有假设 Telegram 为任意上滑手势建立一个返回栈。

## 静态分析依据

使用 DexSQL MCP 分析 `D:\QQAPK\9.3.90.43625_59f33114091efd70.apk`（QQ 9.3.90.43625）：

| 目标       | 此 APK 中的实现                                                                  | 作用                                                                               |
|------------|----------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| 首屏预加载 | `com.tencent.mobileqq.aio.factory.param.m.u(long)`                               | 读取 `key_navigate_msgid/msgseq/time`；存在目标时不预加载最新消息                  |
| 进入边界   | 上述类的 `(Context, AIOParam)` 构造函数                                          | 在预加载开始之前注入恢复目标                                                       |
| 会话识别   | `AIOParam.p()`、`AIOUtil.e(AIOSession)`                                          | 取得内核 Contact，按账号、chatType、peerUid 隔离                                   |
| 首次加载   | `AIOMsgListVM.Q(ic.b)`                                                           | 按时间、序号、ID、最新消息的优先级选择加载路径，随后移除进入参数                   |
| 导航状态   | `BaseMsgRepo.a0(long,long,boolean,MsgSimpleInfo)`                                | 只将有效的序号、ID 放入 `navigate_seq/navigate_id`，因此恢复允许只有一种标识的状态 |
| 列表定位   | `AIOMsgListVB$c.a(MsgListState,IListUIOperationApi)`                             | 在原生定位策略入口查找实际消息索引，替换本次自动恢复的滚动                         |
| 像素偏移   | `AIOLayoutManager.scrollToPositionWithOffset$sdk_debug(int,int,boolean,ec.a$b)`  | 使用顶部锚点和原生异步布局校正；不触发定位高亮                                     |
| 退出边界   | `com.tencent.aio.base.mvvm.a.destroy()`                                          | 在 FrameworkVB 递归销毁子 binder、清除 runtime 之前读取可见消息                    |
| 消息绑定   | `adapter.holder.a.m()` → `AIOBubbleMsgItemVB.Z1()` → `AIOMsgItem.getMsgRecord()` | 跳过时间条等非消息行，保存消息身份而非临时行号                                     |
| 气泡计数   | `reserve1.unreadbubble.f.S()`                                                    | 原生只从本次接收的起止消息与最远可见消息计算数量，退出后清空                       |
| 底部跟随   | 上述 VM 的 `P():long` → `AIOMsgListVB.getCurrentFocus()`                         | 原生在计数为零且焦点为 `-1` 时跳过正数发布，避免自动跟随新消息时闪烁               |
| 阅读进度   | `UnreadMsgIntent.MsgListScrollEvent`、上述 VM 的私有 MsgIntent 分发方法          | 读取构造参数中的底部可见消息，跟踪当前会话的新消息                                 |
| 阅读返回   | `MsgNavigationEvent.NavigateBySeqEvent`、runtime 的 route 分发接口               | 加载阅读返回点，使用同一定位策略还原视口；沿实际返回类型兼容混淆后的路由 getter    |

首屏预加载、定位策略、Contact 转换和气泡计数通过四个 DexKit 查询定位；在此 APK 中均只有一个匹配。其余方法沿实际类型按签名读取，不硬编码混淆方法名。气泡计数的两个 long 字段分别保存当前值和上次发布值，更新时同时同步，确保原生点击判断与显示一致。

### 其他版本的差异

QQ 9.3.5、9.3.10、9.3.15 把预加载逻辑内联进无参 `d()`，原来的 `(long)void` 限制会漏掉它们。定位时保留 `preLoadMsg`、`key_navigate_msgid`、`key_navigate_msgseq` 三个字符串和返回类型约束，不再限制参数。

这些版本的 runtime 接口移到混淆包，其中 9.3.10、9.3.15 的 base VB 和 holder 包名也发生了变化。现在从 `AIOMsgListVB.getMAIOContext$sdk_debug()` 的声明类取得 base VB，从其返回接口读取 AIOParam；退出时从实际 holder 类型读取 base VB getter。这样不需要针对版本列出混淆包名，也减少了两次 DexKit 查询。

恢复优先匹配 msgId；本地 ID 变化时仅允许唯一的 msgSeq 匹配。目标不在 QQ 返回的历史片段中时，删除这条失效记录并交回原生策略。此功能仍依赖 QQ 能取回对应历史，不保证恢复已删除、清理或服务端不可用的消息。

## 验证范围

15 项功能测试覆盖消息身份、部分可见消息偏移、会话隔离、明确导航优先级、旧存储迁移、跨次进入后累计未浏览消息、继续阅读后减少和归零，以及阅读返回的视口、偏移、二次点击、期间新消息和已读到最新等状态。另覆盖进入位置远早于已读序号时返回本次入口，以及在已读范围内继续浏览后更新返回点。阅读边界独立于位置锚点，向上回看不会把已浏览消息重新计入。

Debug 构建包含 `ChatPositionCompatibilityMain`，可在独立 `app_process` 中检查宿主 APK。先把模块 APK、其中的 arm64 `libdexkit.so` 和仅包含待测 APK 原始 `classes*.dex` 的 `fixture.zip` 推送到同一临时目录，再运行：

```shell
CLASSPATH=/data/local/tmp/tcqt-chat-unread-compat/module.apk app_process /system/bin com.owo233.tcqt.loader.validation.ChatPositionCompatibilityMain compat /data/local/tmp/tcqt-chat-unread-compat/libdexkit.so /data/local/tmp/tcqt-chat-unread-compat/fixture.zip
```

该检查执行实际 DexKit 查询和生产功能的 `install()`，由记录引擎收集十一个 Hook 注册，另检查延迟查找的 holder getter 和 `getLatestDbMsgs` 签名。使用的是待测 APK 自身的宿主类，不替换设备上安装的 QQ，也不执行聊天交互。

2026-11-28 的验证分为四轮：

- 初版位置恢复：QQ 9.2.5～9.3.90 全部 37 个 APK 的 DexSQL、Android DexKit 和生产安装查找通过；用户已实测退出重进可恢复位置。报告位于 `app/build/reports/chat-position-compat/`。
- 本次补全气泡计数：全部 37 个 APK 的新增计数入口、事件处理签名、两个 long 字段和本地最新消息接口通过 DexSQL 检查；Android 17 设备抽查 9.2.5、9.3.5、9.3.10、9.3.15、9.3.90，四个 DexKit 查询、七个 Hook 注册及延迟查找全部通过。报告位于 `app/build/reports/chat-unread-compat/`。
- 底部新消息闪烁修正：全部 37 个 APK 的焦点读取签名唯一匹配，同样抽查上述 5 个版本，生产安装查找通过，仍为四个 DexKit 查询和七个 Hook 注册。报告位于 `app/build/reports/chat-unread-bottom-compat/`。该轮再次通过下述构建和包校验，并更新设备模块、重启 QQ 检查加载；用户随后确认修正有效。
- Miuix 新消息按钮：全部 37 个 APK 的创建、状态更新、复用和根视图接口通过 DexSQL 检查；上述 5 个代表版本的四个 DexKit 查询、十个 Hook 注册及延迟查找全部通过。核对 QQ 9.3.90 的原生代码，布局宽高均为 WRAP_CONTENT，角标文字仅为数字（上限 99），带计数和仅箭头状态均在显示前设置无障碍描述。源码复核未发现需修改的问题。报告位于 `app/build/reports/chat-unread-button-compat/`。尚未人工验证新按钮的实际渲染、按住/取消反馈、点击隐藏和多选/复用行为。

首版按钮的应用单元测试共 292 项，289 项通过，3 项原有跳过，无失败；lint、Debug 构建、双格式内容、APK 签名、16 KiB 对齐和 ZIP 完整性校验通过。已通过 ADB 安装到 `192.168.0.136:6677` 并重启 QQ 9.3.90，安装包 SHA-256 与本地一致（`ac1c498fb4d2c31f9d114127a174cb1acb892bf2b8ecb2e87390884d3fd1ea21`），启动日志包含 `RememberChatPosition`，模块错误目录为空。

用户指出首版角标靠右，与 TG 参考图不符。随后将圆形按钮和角标改为 BottomCenter/TopCenter 对齐，内层尺寸改为 48×62dp。修正版 Debug 构建、应用单元测试和包校验通过，已安装并重启 QQ，安装包 SHA-256 为 `e5d062dabb558d9ace6bc2148aa073e116f9b68a12779e1c245653650c700137`，与本地一致。重启后 QQ 自行恢复聊天，截图中的圆形按钮及箭头完整可见；当时无数量角标，因此居中角标的实际渲染尚待截图验收。报告位于 `app/build/reports/chat-unread-button-center/`。本轮仅修改布局，未重复进行宿主入口兼容性扫描。

用户随后提供带计数的截图，确认角标已居中，但指出按钮比 TG 更突出。对照后将圆盘从 48dp 缩至 44dp，箭头从 24dp 缩至 22dp、改为主题前景色的 55% 不透明度，阴影从 4dp 降至 0.5dp；内层尺寸为 48×58dp，维持角标重叠关系。Debug 构建、lint 和包校验通过，已安装并重启 QQ，安装包 SHA-256 为 `28bfe454380400199f33ef23c16da1dd62b740e2cb6ca3624bcfa3997b44a725`，与本地一致。设备自行恢复群聊后的浅色截图中可见计数 17，圆盘、灰色箭头和居中角标完整显示。该截图覆盖当前浅色静态显示，未验证深色、按住及取消手势。报告位于 `app/build/reports/chat-unread-button-quiet/`。本轮只改外观，未重复运行单元测试或宿主兼容性扫描。

这些检查覆盖扫描和反射兼容性，气泡的实际显示、点击和退出重进累计数量仍需人工在好友与群聊中验证。保存发生在正常聊天退出，未退出聊天即被系统杀死时不会生成新的记录。

### 阅读返回验证（2026-11-28）

新增的历史导航构造、路由分发和列表 getter 在目录内全部 37 个 QQ APK 中通过 DexSQL 静态检查；Android 17 独立进程再次检查 9.2.5、9.3.5、9.3.10、9.3.15、9.3.90 的四个 DexKit 查询、十一个 Hook 注册和生产安装反射查找。报告位于 `app/build/reports/chat-unread-return/compat/`。

应用单元测试共 298 项，295 项通过、3 项原有跳过，无失败；其中位置与阅读状态测试 14 项，覆盖返回视口和偏移、期间新消息不移动返回点、返回后再次点击、手动读回、继续阅读后建立新返回点、重进时的序号定位，以及已读到最新时使用原生行为。返回定位完成后消费本次返回点，历史气泡重新测量导致底部可见消息变化时，下一次点击仍可继续到最新消息。

实机首轮启动发现：列表 getter 在构建 View 树时就会执行，此时尚未绑定 runtime。已在这个 Hook 入口跳过未绑定阶段，随后再关联会话。修正包 SHA-256 为 `099f972fff7b71c071c5bd9db6e997d66b47d774cc8c3d5bed6b69b121c9cd49`，已安装到 `192.168.0.136:6677`，安装包哈希与本地一致。QQ 9.3.90 重启后的新进程正常加载功能，未再出现 Hook 回调错误，模块错误目录为空，QQ 自行恢复群聊。

Debug 构建、lint、双格式内容、APK 签名、16 KiB 对齐及 ZIP 完整性检查通过。启动截图和日志位于 `app/build/reports/chat-unread-return/`。本轮自动验证覆盖源码状态、宿主入口及启动生命周期；向上翻阅、首次返回与二次点击的实际落点，遵循 AGENTS.md 第 6 条，由用户手动验收。

用户随后实测发现：进入位置远早于已保存的 readSeq 时，上翻后点击会跳到接近最新消息的位置。原实现用 readSeq 初始化返回目标，且只在底部可见消息到达 readSeq 时更新视口；现将本次浏览到达的消息序号与持久化 readSeq 分开记录。新增的回归场景使用入口 seq 100、偏移 -137、已读序号 990，确认返回本次入口；另覆盖在旧已读范围内继续浏览后的返回点更新。两项回归在旧实现上失败，修正后位置测试 15 项全部通过；应用测试 299 项，296 项通过、3 项原有跳过，Debug 构建、lint 和上述包校验通过。修正包 SHA-256 为 `b6c4dab1f58a77e31ded6ed9de2003efee8a55da20f6752c74ed0e316e91b39c`，报告为 `app/build/reports/chat-unread-return/entry-origin-*`。ADB 首先两次连接超时，设备返回后已成功安装，安装包哈希与本地一致。重启 QQ 经用户解除应用锁后，进程 10908 正常加载 RememberChatPosition；进入 ChatActivity 后再次读取日志，未出现 Hook 回调错误、空指针或崩溃，模块错误目录为空。实际首次及二次点击的落点仍待用户手动验收。
