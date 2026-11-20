# QQ 经典农场账号选择

功能开关位于「杂项 → 农场启动时选择账号」，配置键为 `farm_account_selector`，默认关闭。

## 分析范围

使用 DexSQL 分析 `D:\QQAPK\9.3.85.43200_59f33114080dbd69.apk`。
APK SHA-256：`4ACEA88A22288D8554AF29CD7C56BEEE8B438F69419E621210AE7930E7BD864A`。
静态分析与设备验证分别记录；不能把 APK 中的调用链当作服务器行为或其他版本的实机证明。

## 启动与登录

动态入口 `NQFLebaPlugin.M` 使用农场 AppID `1112386029`，进入
`IMiniAppService.startAppByAppid` 或 URL 启动路径。解析后经 `MiniSDK.startMiniApp`、
`MiniSDKImpl`、`MiniServer`，到达
`LaunchManagerService.startMiniApp(Activity, MiniAppInfo, Bundle, ResultReceiver)`。
该服务创建进程启动 Intent，附加 `KEY_APPINFO`、`KEY_LOGININFO`，再合并传入 Bundle。

普通登录由 `AuthJsPlugin.login` 调用 `ChannelProxyImpl.login`，通过
`MiniAppGetLoginCodeServlet` 请求 `LightAppSvc.mini_program_auth.GetCode`，返回 `code`。
农场另有快速登录路径：`QQCustomizedProxyImpl.fastLogin` 经 `GameLoginUtils` 使用缓存的
`jsCode`；缓存由 `NQFFarmHandler.requestFastLoginInfo` 请求
`OidbSvcTrpcTcp.0x94b9_33` 获取 `FastLoginRsp.js_code`。

这里交换的是短期登录 code，而非直接从 QQ 取出一个固定 OpenID。
农场仍按自己的正常协议交换身份；TCQT 当前没有独立实现农场网关客户端。
当前游戏使用 `UserService.PlayerLogin`，回复包含 `basic.open_id`，详见下文的鉴权复核。
仅改 `LoginInfo.account` 不足以切换身份：`LoginManager.getAccount` 优先读取
`MiniAppProxyImpl.getAccount`，登录请求也默认使用当前 QQ runtime 的账号。

## 实现边界

- 只拦截该 AppID，按 QQ 保存的在线状态或 `AutoLoginUtil.canAutoLogin` 列出可用账号，
  每次请求前重新核对账号。保存状态不等于凭据一定有效，进入前的网络预检负责检查。
- 主进程为选中的账号获取新 code，使用请求局部标记，在 `MSFServlet.sendToMSF`
  与最终 `MsfServiceSdk.sendMsg` 设置并核对 `ToServiceMsg.uin`。
  `TempServlet.onSend` 重建 `Packet` 时仅复制 `attributes`，不复制原请求的 `extraData`；
  所选账号和请求 ID 必须放在 `attributes` 中，否则会丢失路由标记。
  只允许上述两个登录命令及下文列出的四种好友查询所使用的 HTTPTransfer 命令。
  每个请求 ID 绑定账号与操作类型；请求取消/过期后拒绝发送，回调没有确认路由则拒绝结果。
- `MainService.isValidAccount` 会过滤非当前账号的回复。QQ 已提供请求属性
  `uinNotMatch` 供跨账号回复使用；仅为带 TCQT 标记且已核对操作类型的请求添加此属性。
  网络身份与本地回调分发分别处理，不修改 QQ 当前 runtime。
- 通过私有 `ResultReceiver` Binder 把农场登录请求交给主进程。写入 Bundle 前转换成
  框架 `ResultReceiver`，避免跨进程反序列化模块匿名子类失败。预检 code 仅能消费一次，
  限同一账号与快速登录 API，30 秒后作废。不落盘或记录登录 code、票据。
- 游戏进程只对该农场会话覆盖账号、昵称、普通/快速登录及好友查询回调；Binder 失效时返回失败。
  不把分享、支付等宿主能力视为已切换到小号，这些能力仍遵循 QQ 本身的账号逻辑。
- 进入前通过 `GameLaunchStrategy` 找出农场进程，等待其退出并清理幂等进程记录，
  避免复用旧 JavaScript 会话。若进程还承载其他小游戏则拒绝关闭并提示用户处理。
- QQ 的 `PreloadingFragment` 使用临时 `QPublicTransFragmentActivity`，启动调用返回后
  会关闭它。按最近一次 Resume 维护宿主页面顺序，排除临时加载页面；
  弹窗优先挂到有窗口焦点且仍有效的页面，延迟启动也使用同一 Activity。
  动态页和频道的 `QPublicFragmentActivity` 均可承载，取消不启动农场。
- 取消或超时会移除请求 ID；最终发送 Hook 用 `param.throwable` 明确阻止失效请求，
  不能只在 Before 回调内抛异常，因为 Hook 框架会捕获异常并继续执行原方法。

### 频道分享卡片入口修正（2026-11-14）

在用户已打开的频道个人页复现：点击小程序卡片的「QQ 经典农场」标题，
前台仍为 `QPublicFragmentActivity`，日志却记录 `Picker host=SplashActivity`。
弹窗实际上已经显示在后台首页窗口，返回上一级才可见。

`FarmAccountSelector` 现在在 Activity Resume 时维护最近活动的页面顺序，
排除 `QPublicTransFragmentActivity`，并检查页面存活状态和窗口 token；
有焦点的有效页面优先。账号弹窗和后续小游戏启动使用同一承载 Activity，
不再把 QQ 首页作为固定回退目标。

PKX110 / QQ 9.3.80 / LSPosed 实机结果：

- 同一卡片标题点击后，在频道页立即出现选择窗口；日志为
  `Picker host=com.tencent.mobileqq.activity.QPublicFragmentActivity`，无需先返回首页。
- 点击取消后仍留在频道页，随后可以再次点击卡片打开窗口。
- 选择小号后快速登录请求记录 `current=false`，回复成功、游戏登录回调 `success=true`，
  小游戏正常打开；退出后回到原频道页。分享卡片原有的农场访问目标保持不变，
  游戏中被访问农场的昵称不能单独作为登录账号身份的依据。
- 号码遮蔽仍正常显示。

截图：`channel-hidden-picker.png`、`channel-picker-fixed.png`、`channel-cancel-fixed.png`、
`channel-secondary-launched.png`、`channel-return-after-farm.png`。

`gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --configuration-cache`
已通过（2 分 47 秒），日志为 `build-channel-entry.log`。
JVM 报告共 232 项，0 失败、0 错误、2 跳过；lint 为 0 errors、25 warnings。

## 上线包假设

分析了 `trpc.msg.register_proxy.RegisterProxy.SsoInfoSync` 和
`trpc.qq_new_tech.status_svc.StatusService.UnRegister`。
QQ 的 `ntOnline` 构造 `RegisterInfo` 并交给对应 runtime 的 `IKernelService.onLine`，
最终由原生 `IQQNTWrapperSession.onLine` 生成会话包。`UnRegister` 同样来自 NT 内核，
发送适配器对它采用同步下线发送路径。不能把主账号的 kernel 会话直接用于小号上线/下线。

本次小号超时的实机原因是 `TempServlet` 丢失账号标记，导致未设置跨账号回复属性。
修正后「入梦」获取 code 成功，额外上线/下线并非这台设备的必要步骤；
功能没有人为发送这两个命令，也没有切换全局 runtime 或 NT 当前账号。
这不能证明其他设备上已失效或被撤销的小号凭据仍然可用。

## 2026-11-14：复制与自定义 js_code

账号行右侧新增复制按钮，每次按该行账号向 QQ 获取新的农场快速登录码，再写入剪贴板。
点击复制不改变所选账号，获取期间该按钮显示进度；失败保留弹窗并显示原因。
QQ 号仍按长度遮蔽。底部固定自定义码输入区和两种进入按钮，账号列表可滚动。
输入框遮蔽内容，空值、超长内容和内部空白/控制字符均有校验；错误时保留原输入。

使用本机账号时点击「进入所选账号」；使用其他账号的码时粘贴到「自定义 js_code」，
点击旁边「进入」。自定义路径不查询本机账号列表，也不获取或切换 QQ 凭据。
登录码由本次启动的私有 Binder 在快速登录时交付一次，普通登录和再次索取返回失败，
防止无效码被替换成 QQ 前台账号的登录信息。服务端有效期和可重用次数由 QQ 农场决定，
复制后请尽快使用；失败时退出游戏，重新获取码并从选择窗口进入。
复制的码被标记为敏感剪贴板内容，码不加入 Intent、配置文件、TCQT 日志或错误报告。

### 预加载登录信息修正

QQ 9.3.80 的 `MiniGamePackageManager.createGamePackage` 会读取
`key_preload_login_info<QQ UIN><APP_ID>`，把缓存中的登录信息作为 `qqLoginData` 注入引擎，
游戏可直接使用它而不调用 `fastLogin`。仅隔离 Storage 仍会进入前台账号农场。
现在在已标记的农场会话内屏蔽这一特定 MMKV 键的读取，统一走本次启动的登录 Binder；
其他小游戏的键保持宿主行为。自定义进入还使用本次启动独立的 Storage 缓存命名空间，
避免读到上次进入留下的游戏登录状态，QQ/MSF 账号身份继续由宿主管理。

## 2026-11-14：小号好友列表串号

对 QQ 9.3.80 和 9.3.85 APK，以及设备上的农场 `client_1.16.3.8` 进行核对。
农场 QQ 分支的 `QqPlatformUtil.getFriendStorage` 调用
`qq.httpToSsoRequest`，请求 `http://fakeapi.qq.com/get_friend_storage`。
返回的 OpenID 随后由 `FriendManager.reqAllFriendToServer` 放入 `SyncAllRequest.open_ids`。
登录 code 与这条好友查询链路分别鉴权；只替换登录 code 会造成“小号游戏会话 + 主号好友列表”。
`getFriendCloudStorage` 是同一游戏中的其他平台分支，不能据此判断 QQ 的实际请求。

QQ 调用链：`QQCustomizedProxyImpl.httpToSsoRequest` → `IHttpTransferSSOApi.sendReq`
→ `SSOChannel.doSendRequest` → `SSOEasyServlet` →
`miniapp.trpc.minigame.transfer.Transfer.HTTPTransfer`。
`SSOChannel` 使用当前 QQ runtime 创建 `ToServiceMsg`，并且不使用 `SSORequest.extra`。
因此必须在它提交的 `NewIntent` 进入异步 Servlet 前附加所选账号和请求 ID；
`SSOEasyServlet` 会复制 `attributes`，随后继续由最终发送 Hook 核对账号与命令。

- 只接管该农场 AppID 的 POST 请求：`get_friend_storage`、`get_recent_friends`、
  `get_recommend_friends`、`check_friend`。URL 必须精确匹配；不扩大到任意 HTTPTransfer 业务。
- 游戏侧通过私有 Binder 把请求交给主进程，主进程按绑定的本机账号发送。
  保留 SDK 原始请求头与 body 格式，HTTP 返回结构原样交还游戏；不记录请求头、body 或好友内容。
- 自定义 `js_code` 没有绑定本机 QQ/MSF 账号，当前实现主动拒绝以上宿主查询，
  防止使用前台账号的好友。这个实现限制不表示 code 换取的游戏会话不能查询农场好友。
- 本机账号使用独立的 `tcqt-farm-v2-<account>` Storage 命名空间，避开旧版本留下的混合身份缓存；
  自定义码继续使用每次启动独立的命名空间。未标记农场与其他小游戏不受影响。
- 好友请求在主进程 5 秒内超时，游戏侧 Binder 超时 5.5 秒；取消后拒绝发送，回调只完成一次。

实机回归：PKX110 / Android 17（API 37）/ QQ 9.3.80（16750）/ LSPosed，
农场 `client_1.16.3.8`。最终 Debug 包安装后，主号和小号的好友查询分别记录
`current=true` 与 `current=false`，均返回 `routed=true / status=200 / errCode=0`；
两次好友页显示各自的列表，切回小号未沿用主号列表。小号访问好友后的一键务农
已清除杂草与虫害并获得经验，未再提示“不是好友”。

## 2026-11-14：自定义 code 与 OpenID 鉴权复核

此前从“QQ 宿主好友接口与游戏登录分别鉴权”推导出“仅凭 js_code 无法取得好友列表”，
这个结论范围过大。必须区分 QQ/MSF 好友查询和农场游戏服务器的好友查询。

### 当前协议与真实 API 结果

官方农场 `client_1.16.3.8` 的网关版本参数是 `1.16.3.8_20261028`，
正常 QQ 分支传 `code`、`platform=qq`、`os`、`ver`，不要求调用方预先提供 OpenID。
本人 OpenID 来自 `PlayerLoginReply.basic.open_id`；当前客户端还会发送
`gamepb.globalscenepb.GlobalSceneService.PlayerSceneLogin` 进入农场场景。
旧的 `UserService.Login` 本次也可登录，回复身份与新版相同。

使用用户提供、原本可见游戏好友的账号，直接请求官方网关
`wss://gate-obt.nqf.qq.com/prod/ws`，农场场景 `global_scene_id=1`：

| 请求                                      | 本次结果                                |
|-------------------------------------------|-----------------------------------------|
| `UserService.PlayerLogin`                 | 成功，返回本人 OpenID                   |
| `FriendService.GetAll`                    | `1000020`，请求参数错误；新旧登录均如此 |
| `FriendService.SyncAll`，不传好友 OpenID  | 仅返回本人，不能据此宣称取到了好友      |
| `FriendService.GetBriefInfo`              | 返回 17 个好友游戏 ID                   |
| `FriendService.GetGameFriends`，传上述 ID | 返回 17 位好友，全部有 OpenID，均非本人 |

因此最终实现使用 `GetBriefInfo → GetGameFriends`。`GetAll` 是公开旧实现和
官方客户端其他分支中的线索，不能直接当作当前 QQ 服务端可用的方案。
此前从“两条链路分别鉴权”推导“js_code 不能取得好友”的结论确实过大；
反过来，仅凭旧代码中存在 `GetAll` 也不足以认定它现在可用。

### 获取 OpenID 的条件

- 需要农场 AppID `1112386029` 对应、服务端仍接受的短期 code，以及匹配当前版本的登录协议。
  登录后可从回复读取本人 OpenID；好友 OpenID 来自游戏好友资料回复，不需要用户另行提供。
- 本次观察到旧码返回 HTTP 400 `missing openid parameter`，更新 code 后相同请求登录成功。
  这个错误不能单独解释成“用户必须补 OpenID”；不据本次观察承诺固定有效期或重用次数。
- 若直接调用 QQ 开放平台的
  [`jscode2session`](https://q.qq.com/wiki/develop/game/server/open-port/login.html)，
  官方接口要求 `appid`、`secret`、`js_code`、`grant_type=authorization_code`。
  `secret` 是应用服务端的 appSecret；通过农场网关登录的用户不需要提供它。
- OpenID 是应用范围内的身份标识，不是 QQ/MSF 登录票据。本次恢复的是服务器已有的
  游戏好友；QQ 最近联系人、推荐关系及尚未同步到游戏服务器的新好友不由这条链路保证。

### 已实现的修复

`FarmCustomFriendsHost` 仅在自定义 code 会话、AppID 匹配时拦截
`ScriptRuntime.loadScript(ScriptFile)`，从运行时的 `EngineContext.gamePackage.id` 校验 AppID。
该入口位于包包装与脚本预取之后；支持 Triton 添加的 `/game/` 名称前缀。
`FarmCustomFriendsScript` 识别 Cocos main 工厂，
在工厂的 strict 指令之后插入随 APK 分发的 `farm-custom-friends.js`。
原游戏包和磁盘缓存不被改写，修改后交给引擎的是 `ScriptFile.Content`，code cache 设为 null，
避免引擎执行原来源的旧编译缓存。本轮执行入口已在 QQ 9.3.80 静态核对并取得实机代码回报。

2026-11-14 用户确认第一版安装后仍然没有好友，第一版的本地 API 成功不能作为手机修复成功。
设备 APK 校验与第一版构建一致；手机游戏脚本与本地 fixture 在统一换行后相同。
第一版挂在 `MiniGamePackage.getScript`，没有手机运行记录可证明该入口执行了源码替换；
当前改为执行边界，并通过本地同步桥回报阶段和数量。尚不能将入口变更本身当作已查明根因。

手机端第二次进入的代码记录为 `script_transformed → adapter_started → manager_unsupported`，
随后只有 `discovery_ready`，没有好友请求。这证明源码已经执行，适配器却在模块导出阶段拒绝了
好友管理器。旧逻辑在导出类时一次性检查所有循环导入是否就绪，并将结果永久写入
`managerReady`；之后依赖完成初始化也无法恢复。现在导出时只验证待替换方法，
真正请求列表时才读取协议、连接和玩家模型的实时导入对象。
新增延迟初始化回归：旧逻辑失败，修改后通过；官方脚本 fixture 同样覆盖延迟 setter。

诊断保存到宿主 `files/tcqt_farm_friends/status-<pid>.json`，revision 为 2。
只保存白名单阶段、时间、数量，不保存登录码、QQ 号、OpenID 或好友资料。
`script_transformed` 只表示源码已替换；`manager_ready` 表示适配器已接管；
`query_complete.count` 才表示实际游戏连接的查询完成且已写入客户端列表。
该记录也不保证游戏界面绘制完成。缺失阶段必须继续排查，不能当作成功。
请求阶段的 `manager_unsupported.count` 是缺失依赖位图：1=协议，2=游戏连接，4=玩家模型。

### 修正初始化顺序后的手机代码验证

用户重新使用自定义 JS Code 进入游戏并打开好友列表后，实际小游戏进程（PID 32231）记录：

```text
custom_session → hook_ready → script_received → script_transformed
→ adapter_started → manager_ready → discovery_ready → query_start
→ brief_reply.count=17 → friends_reply.count=17 → query_complete.count=17
```

这是手机内游戏自身 WebSocket 的真实请求结果，最后一个阶段在写入
`FriendManager.clientFriendList` 后产生。没有 `query_failed` 或 `manager_unsupported`。
它证明手机代码路径已取回并提交 17 个好友。

适配器只处理官方 `FriendManager.ts`、`QqPlatformUtil.ts` 模块：

- 自定义模式跳过 QQ 宿主好友发现，改用同一游戏连接先读取好友 ID，再分批获取好友资料。
  每批 50 个，与官方列表默认差量刷新数量一致；所有请求保留 `global_scene_id`。
- 复用游戏自己的 protobuf、连接、加密及 `convertServerFriendToClientFriend`，
  不额外消耗登录 code。过滤本人、缺失 OpenID 和重复 ID，保留原有列表提示标记。
- 全部分批成功后一次更新列表；错误、超时、无效回复保留已有列表。接口未携带的
  黑名单、邀请和申请数量保持现有值，交由游戏原有请求更新。
- 自定义模式不会使用前台 QQ 的凭据补齐好友，不会回退到空 `SyncAll`。
  适配器不匹配时拒绝该请求。所选本机账号的 MSF 路由和未标记游戏保持原逻辑。

### 第一版验证记录（用户已报告手机端失败）

- 农场相关 JVM 测试 16 项通过，覆盖 code 消费、MSF 路由、脚本范围、幂等、源码保留，
  并对实际 `game.js` 使用生产源码变换器生成测试脚本。
- Node 测试 9 项通过：7 项适配器测试及 2 项实际官方脚本集成测试。
  覆盖分批、本人过滤、去重、列表标记、失败保留、超时及迟到回调；
  集成测试实际执行官方 `reqFriendList`、protobuf 和好友资料转换函数。
- 最终直接 API 联调加载上述生产变换后的官方脚本，实际调用
  `GetBriefInfo`、`GetGameFriends`、`BatchModerateText`，结果为 17 位非本人好友，
  17 位都有 OpenID，`bReqFriendListed=true`，原客户端派发加载成功事件。
  探针只输出方法、状态、数量，不输出 code、本人身份或好友资料。

```powershell
$env:TCQT_FARM_GAME_DIR = '<包含 game.js 与 subContext.js 的本地目录>'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.owo233.tcqt.host.service.Farm*Test' --rerun --configuration-cache
node --test tools/test-farm-custom-friends.cjs tools/test-farm-custom-friends-fixture.cjs
```

从本轮起，遵循用户要求，修复验证仅使用代码级单元测试和直接 API 请求；
禁止通过图像、ADB 截图或其他画面检查判断修复状态。
