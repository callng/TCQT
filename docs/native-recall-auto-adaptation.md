# Native 防撤回自动适配

现有 9.2.30～9.3.90 的 31 个 APK 含 29 种 ARM64 内核。它们的 RVA、CBZ
分支距离和 compiler-outlined helpers 会变化，但源码位置日志、拦截点参数
约定、标准化通知及消息记录布局保持兼容。运行时解析器据此定位未知内核，
减少只因地址移动而逐版发布模块的需要。

## 选择和缓存

1. 读取已加载 `libkernel.so` 的 GNU Build ID，优先使用 `KERNEL_PROFILES`。
2. 表中没有该 ID 时，读取宿主私有缓存 `cache/tcqt-native-recall.bin`。
   只有扫描规则 revision、Build ID、长度、校验和及当前代码指令校验均通过，
   才使用缓存 RVA。
3. 缓存不可用时执行 C++ 扫描。候选必须唯一，全部布局和指令约定符合才安装。
4. 三处 Dobby 安装全部成功之后写缓存；文件经同目录临时文件加 rename 发布。
   写入失败不影响已安装的 Hook，下一次启动会重新扫描。

缓存只保存最近一次未知内核的结果，不逐版本累积文件。它保存相对 ELF 地址，
不保存运行时指针，因此不依赖 ASLR 地址；Build ID 改变或扫描规则更新后不复用。
清理宿主缓存会触发重新扫描，不改变功能配置。

该文件是内部派生缓存，独立于模块的功能设置和配置备份。LSPosed、Zygisk
共用同一实现和路径规则，各宿主、Android 用户通过应用沙箱自然隔离。
Java 启动桥只传入 `Application.cacheDir` 下的文件路径并绑定提醒 JNI。
函数定位、身份判定、读取／写入缓存、指令校验及撤回拦截都由 C++ 执行；
仍在原来的内核深层处理点拦截，没有加入 Java push 或 `onMsfPush` Hook。

## 定位与兼容性边界

`app/src/main/cpp/recall/kernel_scan.cpp` 直接读取加载映像的 program headers，
不依赖磁盘 so、ELF section headers、导出函数符号或运行时第三方反编译器。

- 搜索完整、零终止的撤回日志名字，包括通知接收、普通／无痕替换、C2C worker、
  本地记录分发和群 worker。排除包含这些名字的其他日志格式。
- 解码 ARM64 ADRP + ADD 交叉引用，并使用 `PT_GNU_EH_FRAME` 中的展开表确定
  所属函数。多个引用属于同一函数时合并；出现不同候选函数时拒绝。
- 在目标函数开头查找 load + CBZ X8，读取实际 CBZ 指令字和跳转目标，不把
  9.3.85 的分支距离套到新版本。目标必须位于原函数内、位于该检查之后。
- 验证通知构造中的 sender/recipient/operator/seq/extra 地址，chat/operator
  store、PB 标量 getter 对应的存储偏移，以及 24 字节 libc++ string view。
- 从 C2C worker 的参数 5003 调用定位当前 UID 查询函数，核对模块 0x10、方法 1
  与 X2～X5 保存约定。小 helper 按实际 BL 目标读取，避免把展开表中的外层
  函数起点误当作 helper 起点。
- 检查普通／无痕函数及其直接 helper 中的记录 getter 槽位，最终核对三处
  前置 load、CBZ 字、`MOV W21,W5`、`LDRB W9,[X2,#0xc1]` 和查询首指令。
  所有目标 RVA 都必须落在当前内核可读、可执行的加载段中。

当前规则支持已验证的 ARM64 编译／布局家族，并非任意二进制的自动语义恢复。
它使用通知构造的明确指令形状与栈偏移；编译器改变寄存器分配、栈布局或
outlining 形状时也可能需要更新规则，即使业务逻辑没有变化。删除日志字符串、
改变展开表编码、虚表或通知 ABI，同样可能导致拒绝。日志提供拒绝原因；
不会模糊评分后强行选址，也不会悄悄改用 Java 拦截。

安装状态仍是一进程一次尝试。失败回滚、未激活回调和投递错误处理沿用既有
机制，通用扫描不会在每条消息或每次轮询时反复执行。

## 热重载

LSPosed 热重载会替换模块 ClassLoader。Java 单例中的 `started` 和加载缓存
只属于当前 loader，不能代表进程内的 native 引擎状态。首次成功加载库后，
启动桥在进程内的 `System.getProperties()` 保存原 JNI 入口 `Method`，使其
ClassLoader 和库一直存活。后续 loader 通过同一入口传入新的提醒回调 Class，
不再加载第二份 so，也不再扫描或安装 Dobby Hook。

C++ 在已准备好时只替换提醒 Class/Method 绑定，并释放旧 global ref。
事件线程在锁内取得配套的 local ref 和 method，锁外执行 Java，因此正在执行
的旧回调和重绑定可以安全交接。绑定失败保留旧回调；内核安装失败仍按原规则
返回失败，不用“已有入口”掩盖真正的初始化失败。

内核 Hook 与引擎代码按进程存活。更新 native 二进制后，需要完整重启宿主
才能使用新引擎；Java 提醒代码可以随热重载更新。

## 验证与复现

`tests/native/kernel_scan_probe.cpp` 将原始 ARM64 ELF 的 PT_LOAD 段映射到独立
内存，直接调用生产 C++ 扫描器；全程不查内置表、不改 QQ 设置或原始 APK。
`tools/test-kernel-scan.py` 将结果与 Python/Capstone 独立分析证据逐项比较。

`tools/test-native-recall-reload.ps1` 使用 Debug APK 和对应的 `libtcqtrecall.so`，
在开启 CheckJNI 的独立 `app_process` 中创建三个模块 ClassLoader，核对启动
成功、JNI 入口归属、重复启动及映射数量，全程不操作 QQ 或 LSPosed 设置。
生产 C++ 探针另验证重绑定不增加 Hook/线程、失败保留原绑定和投递期间的交接。

2026-11-18 Android 17 / QQ 9.3.85 实测：三 ClassLoader + CheckJNI 探针通过，
直接从第二个 loader 加载同一 so 可复现 ART 的 `already opened by ClassLoader`。
20:16:57 的实际 LSPosed 热重载中，MAIN（31291）和 MSF（12781）PID 保持不变，
均记录 `existing kernel engine reused; reminder bridge rebound`，没有初始化异常。

Linux 主机复现示例，先按既有分析流程准备 `inventory.json` 和提取的 so：

```sh
g++ -std=c++20 -O2 -Wall -Wextra -Werror -fno-exceptions -fno-rtti \
  tests/native/kernel_scan_probe.cpp app/src/main/cpp/recall/kernel_scan.cpp \
  -o app/build/analysis/kernel-scan-probe
python3 tools/test-kernel-scan.py app/build/analysis/kernel-scan-probe \
  app/build/analysis/native-recall-versions/inventory.json \
  --output app/build/analysis/native-recall-versions/native-scanner-validation.json
app/build/analysis/kernel-scan-probe \
  app/build/analysis/native-recall-versions/9.3.85.43200_59f33114080dbd69/libkernel.so --checks
```

2026-11-18 验证结果：

- 30 个 APK／28 种内核的三个 RVA、三个 CBZ 字、UID 查询 RVA／首指令均与
  独立证据一致。桌面扫描样本约 38～51 ms；这些不是宿主启动耗时。
- 同一生产扫描器用 NDK 编译，在 Redmi K90 Pro Max / Android 17 的独立
  ARM64 探针中解析 9.3.85 原件成功，单次样本约 94 ms。
- 主机和 ARM64 均通过 17 项检查：参数／operator 布局改变、日志缺失、候选
  不唯一、越界展开表／RVA、缓存回读、其他 Build ID／规则 revision、损坏／
  截断缓存与不可写路径。部分检查同时确认测试 fixture 确实存在。
- 原生拦截器 ARM64 探针共 76 项通过，新增未知 ID 的缓存安装、内置优先、
  不兼容未知内核在任何 Dobby 安装前退出，以及缓存路径 JNI 失败清理。
- Debug、R8 Release、Zygisk ZIP、`lintDebug` 构建成功；app JVM 275 项，
  0 failures / 0 errors / 3 skipped。双格式内容、签名、16 KiB 对齐和 Linux ZIP CRC
  检查通过。包内确认存在扫描器日志字符串及 native JNI 入口。
- Release 为 `4.0.2.r743.b9ce653`，4,135,087 字节，使用本地 Debug 证书；
  SHA-256 为 `2240ce5984d40fbf8999f332fabf22d82b0553488f32c53ec1327d1bdbc4108c`。

QQ 9.3.90 已加入验证范围。扫描器在这些既有
内核上绕过内置表的结果证明自动定位能力；独立探针也不等同于未来 QQ
版本的好友、群、临时会话与两种提醒的宿主验收。
