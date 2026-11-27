# 记住聊天位置

聊天分类中的 `remember_chat_position` 开关，默认关闭，仅对 QQ NT 的好友私聊（chatType 1）和群聊（chatType 2）生效。

退出聊天时保存首个可见消息的 msgId、msgSeq 和相对列表顶部的像素偏移。每个账号、会话类型、peerUid 分别保存，数据位于模块数据目录的 `chat_position/positions` FastKV 存储中，正常退出后重启 QQ 也可恢复。不保存消息正文，也不改变未读状态。

普通进入聊天时先让 QQ 按目标加载历史消息，再用宿主布局管理器恢复顶部偏移。搜索、引用等带有明确消息 ID、序号或时间的跳转优先使用原生行为。每次退出都会更新浏览位置，包括通过搜索进入后继续浏览的情况。

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

首屏预加载、定位策略和 Contact 转换通过三个 DexKit 查询定位；在此 APK 中均只有一个匹配。其余 getter 沿实际类型按返回类型读取，不硬编码混淆方法名。

### 其他版本的差异

QQ 9.3.5、9.3.10、9.3.15 把预加载逻辑内联进无参 `d()`，原来的 `(long)void` 限制会漏掉它们。定位时保留 `preLoadMsg`、`key_navigate_msgid`、`key_navigate_msgseq` 三个字符串和返回类型约束，不再限制参数。

这些版本的 runtime 接口移到混淆包，其中 9.3.10、9.3.15 的 base VB 和 holder 包名也发生了变化。现在从 `AIOMsgListVB.getMAIOContext$sdk_debug()` 的声明类取得 base VB，从其返回接口读取 AIOParam；退出时从实际 holder 类型读取 base VB getter。这样不需要针对版本列出混淆包名，也减少了两次 DexKit 查询。

恢复优先匹配 msgId；本地 ID 变化时仅允许唯一的 msgSeq 匹配。目标不在 QQ 返回的历史片段中时，删除这条失效记录并交回原生策略。此功能仍依赖 QQ 能取回对应历史，不保证恢复已删除、清理或服务端不可用的消息。

## 验证范围

代码测试覆盖 64 位消息 ID、负本地 ID、部分可见消息的负偏移、新增列表行、重复序号、不同账号和会话的隔离、持久化格式，以及明确导航目标的优先级。编译、注册生成、JVM 测试和 lint 通过后生成 Debug 双格式 APK。

批量兼容检查可复现：

```powershell
python tools/test-chat-position-compat.py --apks D:/QQAPK --dexsql <dexsql.exe> --out app/build/reports/chat-position-compat --adb <adb.exe> --serial <device> --module <debug-dual.apk>
```

脚本先用 DexSQL 检查每个 APK 的三个查询均唯一命中，再提取原始 DEX，推送到设备的临时目录。在独立 `app_process` 中使用真正的 DexKit 查询，并调用生产功能的 `install()`，由记录引擎收集三个 Hook 目标；另外检查退出回调中延迟查找的 holder getter。所有查找均使用 APK 自身的宿主类，不替换设备上安装的 QQ。

2026-11-28 对 `D:\QQAPK` 中 QQ 9.2.5～9.3.90 的全部 37 个 APK 完成检查：DexSQL 37/37 通过，Android 17 设备上的 DexKit、生产安装查找和 holder getter 检查 37/37 通过，包括修复前失败的 9.3.5、9.3.10、9.3.15。

设备运行 QQ 9.3.90，用户已实测退出重进功能。本轮通过 ADB 更新兼容版模块并重启 QQ，确认加载本次构建，启动无模块错误记录。复制现有存储到临时目录后校验，功能保持开启，好友和群聊的位置记录均可解码，包含部分可见消息的负偏移。应用单元测试、lint、Debug 构建、双格式包内容、APK 签名、16 KiB 对齐和 ZIP 完整性校验通过。

报告保存在 `app/build/reports/chat-position-compat/matrix.json` 和各版本的 `.android.txt` 中。这些检查覆盖扫描和反射兼容性，不等于逐版本安装后的聊天交互验证。保存发生在正常聊天退出，未退出聊天即被系统杀死时不会生成新的记录。
