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
农场仍按自己的正常协议交换身份，TCQT 没有实现或推测农场服务器的 OpenID 协议。
仅改 `LoginInfo.account` 不足以切换身份：`LoginManager.getAccount` 优先读取
`MiniAppProxyImpl.getAccount`，登录请求也默认使用当前 QQ runtime 的账号。

## 实现边界

- 只拦截该 AppID，按 QQ 保存的在线状态或 `AutoLoginUtil.canAutoLogin` 列出可用账号，
  每次请求前重新核对账号。保存状态不等于凭据一定有效，进入前的网络预检负责检查。
- 主进程为选中的账号获取新 code，使用请求局部标记，在 `MSFServlet.sendToMSF`
  与最终 `MsfServiceSdk.sendMsg` 设置并核对 `ToServiceMsg.uin`。
  `TempServlet.onSend` 重建 `Packet` 时仅复制 `attributes`，不复制原请求的 `extraData`；
  所选账号和请求 ID 必须放在 `attributes` 中，否则会丢失路由标记。
  只有上述两个登录命令允许此路由。请求取消/过期后拒绝发送，回调没有确认路由则拒绝 code。
- `MainService.isValidAccount` 会过滤非当前账号的回复。QQ 已提供请求属性
  `uinNotMatch` 供跨账号回复使用；仅为带 TCQT 标记的两个登录请求添加此属性。
  网络身份与本地回调分发分别处理，不修改 QQ 当前 runtime。
- 通过私有 `ResultReceiver` Binder 把农场登录请求交给主进程。写入 Bundle 前转换成
  框架 `ResultReceiver`，避免跨进程反序列化模块匿名子类失败。预检 code 仅能消费一次，
  限同一账号与快速登录 API，30 秒后作废。不落盘或记录登录 code、票据。
- 游戏进程只对该农场会话覆盖账号、昵称及普通/快速登录回调；Binder 失效时返回失败。
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
