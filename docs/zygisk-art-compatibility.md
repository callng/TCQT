# Zygisk / ART：Android 13–17 兼容记录

核对日期：2026-11-14。Zygisk 支持范围收敛到 **Android 13–17 / API 33–37 / arm64-v8a**。安装器、`preAppSpecialize`、ART 初始化和 Java 入口分别检查范围；系统版本读取失败、Android 12 及更低版本、尚未核对的 API 38+ 都拒绝启用。APK/LSPosed 的 minSdk 27 独立保留。

“支持范围”表示代码维护与兼容检查范围，并不表示每个系统、ART Mainline 版本和 OEM 组合都完成过设备验收。ART 属于可独立更新的系统模块，不能仅凭 `SDK_INT` 决定内存布局或内部符号一定可用。

## Google 源码基线

本次直接读取 Android Gitiles 的 ART 各 release 分支。固定的提交用于复查；它们不是每个设备当前实际安装的 ART 版本，也不等同于截至核对日的所有安全补丁。

| Android | API | ART release 分支提交                                                                                                                    | 本次设备覆盖                                                           |
|---------|----:|-----------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| 13      |  33 | [`88baac34da072b41917b4adc1987c55546266b33`](https://android.googlesource.com/platform/art/+/88baac34da072b41917b4adc1987c55546266b33/) | 源码核对；无该版本设备                                                 |
| 14      |  34 | [`3c05e56adf5b268ec5b20bf8aec460815e45161c`](https://android.googlesource.com/platform/art/+/3c05e56adf5b268ec5b20bf8aec460815e45161c/) | 源码核对；无该版本设备                                                 |
| 15      |  35 | [`bebbc3cc49f2d9d5420197df0a336fbc3fcbea40`](https://android.googlesource.com/platform/art/+/bebbc3cc49f2d9d5420197df0a336fbc3fcbea40/) | 源码核对；无该版本设备                                                 |
| 16      |  36 | [`ed6c006bd06ae060bd9698fd2cb25c4865512ec3`](https://android.googlesource.com/platform/art/+/ed6c006bd06ae060bd9698fd2cb25c4865512ec3/) | 源码核对；无该版本设备                                                 |
| 17      |  37 | [`b753cf97923c3695338d21466fa14c57b480a59a`](https://android.googlesource.com/platform/art/+/b753cf97923c3695338d21466fa14c57b480a59a/) | Redmi 25102RKBEC / myron、OnePlus PKX110 / OP60F5L1，独立 ART 测试进程 |

重点对照以下文件，五个版本均读取：

- `libdexfile/dex/modifiers.h`：Nterp、预编译、共享内存、JNI 和 intrinsic 标志。
- `runtime/art_method.h`、`art_method.cc`、`art_method-inl.h`：ArtMethod 布局、intrinsic 清理、方法复制。
- `runtime/class_linker.cc`：不可调用的抽象方法绑定解释器入口的规则。
- `runtime/thread_list.h/.cc`：`ScopedSuspendAll(const char*, bool)` 与析构契约。
- `runtime/jni/jni_id_manager.cc`：JNI ID 的 pointer/indices 表示，以及编码时对原声明类方法数组的依赖。
- `runtime/native/dalvik_system_DexFile.cc`、`runtime/runtime.h/.cc`：`DexFile.setTrusted` 的调试状态限制与变化。
- libcore 各 release 分支的 `ojluni/src/main/java/java/lang/reflect/Executable.java`：`artMethod`、`accessFlags` 和缓存的 `declaringClass`。

官方背景资料：[ART Mainline](https://source.android.com/docs/core/ota/modular-system/art)、[16 KB 页面](https://developer.android.com/guide/practices/page-sizes)、[Android 14 动态代码加载要求](https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading)。

## 核对结论和实现

### 方法标志与解释器入口

五个版本的以下常量一致：

| 标志                              | 值           | 处理                                                            |
|-----------------------------------|--------------|-----------------------------------------------------------------|
| `kAccCompileDontBother`           | `0x02000000` | 安装后禁止目标、桥和备份继续被编译                              |
| `kAccPreCompiled`                 | `0x00800000` | 安装时清除；已有此位不能被当作其他框架的不可编译分发器          |
| `kAccNterpEntryPointFastPathFlag` | `0x00100000` | 托管方法清除；在 native 方法上该位是 `CriticalNative`，不得误清 |
| `kAccNterpInvokeFastPathFlag`     | `0x00200000` | 托管方法清除，避免绕过新入口                                    |
| `kAccMemorySharedMethod`          | `0x40000000` | 已修改的方法不再保留共享标记；该位不是快速解释器标志            |
| `kAccDefault`                     | `0x00400000` | 保留默认方法语义；该位不是 proxy 标志                           |

`art_method_patch.cpp` 使用统一处理函数，区分托管方法和 JNI 方法。桥和普通托管备份使用明确的 quick-to-interpreter 入口，不再假设生成桥当前的入口必定是安全的解释器入口。已有框架的入口和 JNI 原入口仍按原有共存规则保留。

解释器入口优先从 ELF 的动态或完整符号表解析 `art_quick_to_interpreter_bridge`。符号不可解析时，根据 Google ClassLinker 的规则，读取 `Runnable.run()` 与 `Iterator.hasNext()` 两个抽象方法的真实入口，要求抽象标志正确、地址可执行、两者一致。Redmi Android 17 设备的裁剪版 libart 没有该汇编符号，实际走此回退；OnePlus Android 17 的完整符号表保留了该本地符号，直接解析成功。两条路径均通过调用测试。回退不扫描或写入 Runtime 私有字段。

Intrinsic 的编号与多项运行时标志复用。只清 `kAccIntrinsic` 会把编号误当成方法状态。现在只通过实际 ART 的 `ArtMethod::SetNotIntrinsic` 转换；缺少该能力时拒绝修改 intrinsic 方法，普通方法仍可使用。Android 16 起 intrinsic 位集合有所扩展，交给实际运行时处理可避免按平台 API 猜掩码。

### 不透明 JNI ID 与备份元数据

`jmethodID` 可为指针，也可为索引，不能直接作为 ArtMethod 内存地址。ArtMethod 地址只来自已探测的 `Executable.artMethod`；失效时停止操作，不再以 `FromReflectedMethod` 的返回值兜底当指针。

备份方法的 JNI ID 必须在复制目标 ArtMethod **之前**生成并保存。索引编码器会根据方法所属类和数组位置查表；覆盖后再编码，备份地址并不处于目标类的方法数组中。真机强制 `indices` 模式重现了该问题，修正后原方法调用和卸载通过。

传给 JNI 的声明类使用原目标的 `Member.declaringClass`。Java 反射备份对象缓存的类仍是生成桥类，复制原生方法结构不会同步更新它。CheckJNI 曾明确报出“不能用生成桥类调用目标静态方法”；传入原声明类后，静态和非虚实例调用均通过。

### 探测、信任和内存

- Throwable 构造器先按真实地址排序，再校验全部步长，不依赖反射枚举顺序或截断后的四个样本。标志和入口候选必须唯一，方法大小必须与步长严格一致；不再接受整除关系作为布局证据。
- 每个必需 ART 函数地址须位于可执行映射。暂停和恢复必须成对可用。
- 删除未被调用的 `nativeTrustDexFile` 与 `art_dex_trust.cpp`。它此前拿 4096 字节假对象调用 Runtime setter 猜偏移，并把调试状态写回固定值。Android 13 与 14+ 的 `setTrusted` 门槛已有变化；实际桥不使用此接口，也不需要修改整个进程的调试状态。
- 原生页面操作继续使用 `sysconf(_SC_PAGESIZE)`，不写死 4096。memfd 跳板保留写入/执行双映射，分别处理两个别名的缓存同步；增加 BTI 落点并校验指令偏移。PAC 去签名改用兼容旧 ARM64 CPU 的 `XPACLRI` hint；旧的 HINT #32 实际是 BTI，不能去签名。
- InMemoryDexClassLoader 和独立原生库提取方案保留。包内 16 KiB 对齐与运行时页面大小是不同检查，包校验不能替代 16 KiB 内核实测。

## 验证与限制

2026-11-14 在以下两台设备上运行本轮编译的实际 ART 实现，均为 Android 17/API 37、arm64、4 KiB 页面。测试通过 ADB 启动独立 `app_process`，没有安装新模块、替换 QQ/TIM 载荷或重启设备。

| 设备                                         | 系统指纹                                                                                | 安全补丁   | 实测 ArtMethod 布局（字节） |
|----------------------------------------------|-----------------------------------------------------------------------------------------|------------|-----------------------------|
| Redmi 25102RKBEC / myron                     | `Redmi/myron/myron:17/CP2A.260605.016/OS4.0.6.0.XPMCNXM:user/release-keys`              | 2026-10-01 | size=32，flags=4，entry=24  |
| OnePlus PKX110 / OP60F5L1，序列号 `7ef9cfd2` | `OnePlus/PKX110/OP60F5L1:17/AP3A.240617.008/V.f7c25c_16dedc9_16ad148:user/release-keys` | 2026-09-01 | size=32，flags=4，entry=24  |

两台实际加载的 ART 路径均为 `/apex/com.android.art/lib64/libart.so`，但二进制不同：

| 设备    | libart.so SHA-256                                                  | ELF Build ID                       | 解释器入口                            |
|---------|--------------------------------------------------------------------|------------------------------------|---------------------------------------|
| Redmi   | `2d4a3575e5b71b537d89c176ac82ad3e32a56a3d43161009de3e728c962fab13` | `1e1b74c56bf2a881aa15e6aad3d7317b` | 抽象方法交叉校验回退                  |
| OnePlus | `90deef3994a30f9e55946a11da08999dd15df54122bffb00bab623ac7240edc7` | `29ece54b6843ddbc934ec9b475f67bd8` | 完整符号表中的本地符号，值 `0x570c80` |

OnePlus 的 `ScopedSuspendAll` 构造/析构、`ArtMethod::SetNotIntrinsic` 均存在于动态符号表。仅查看 `llvm-nm -D` 会漏掉它的解释器入口；完整符号表和实际初始化日志共同确认此次使用直接解析路径。这些地址仅用于记录本次二进制，代码没有按厂商写死地址。

OnePlus 补测的 10 组补丁、6 组内存/PAC 和四种真实 ART 运行配置均通过，未发现需要新增厂商分支的兼容问题。四个 ART 测试进程 PID 分别为 `24852`（pointer）、`24872`（indices）、`24976`（pointer + CheckJNI）、`24995`（indices + CheckJNI）。

OnePlus 在测试 `main` 之前出现 `DexLoadReporterExtImpl`/`Log.isLoggable` 初始化告警；开启 CheckJNI 后还有厂商绘图 native 方法注册告警。另起完全不加载 TCQT 的空 Java 进程（PID `25404`）复现了相同告警，并正常退出。四个测试进程进入 `ArtRuntimeRegression.main` 后没有 CheckJNI 告警、JNI 致命错误或崩溃，不能把这组框架启动告警归为 TCQT Hook 回归。

运行身份也有区别：Redmi 为 UID 2000 / `u:r:shell:s0`，OnePlus 当前 ADB 环境为 UID 0 / `u:r:ksu:s0`。本轮结果验证实际 OEM ART 的结构和调用行为，不代表 QQ/TIM 应用 UID 与 SELinux 域下的注入、加载和内存权限已经验收。

可复用命令：

```powershell
.\tools\test-art-method-patch.ps1 -Serial <serial>
.\tools\test-art-memory.ps1 -Serial <serial>
.\tools\test-art-runtime.ps1 -Serial <serial>
.\tools\test-art-runtime.ps1 -Serial <serial> -CheckJni
```

运行时测试覆盖 pointer/indices JNI ID、静态与实例方法、long/float/double/reference 混合参数、JNI 原生方法、异常传播、重复调用与卸载，并检查启动类库 intrinsic 的备份和还原。测试桥使用预编译 Java 类，不能替代 DexMaker 生成桥、R8 后的 JNI 注册、Zygote 注入与 QQ/TIM 全场景验收。

原生补丁回归为 10 组，内存/PAC 回归为 6 组。安装脚本测试拒绝空值、非数字、API 27/32/38，并接受 API 33–37，旧有热更新/重启激活/损坏包检查继续执行。JNI 注册表使用真实 JVM 验证成功与签名不匹配后的异常清理。

本轮最终结果：

| 检查                                                                | 结果                                                             |
|---------------------------------------------------------------------|------------------------------------------------------------------|
| 真实 ART 调用                                                       | 两台设备各运行 pointer/indices × 默认/CheckJNI，四种组合全部通过 |
| 原生补丁、内存/PAC、JNI 注册、安装脚本                              | 全部通过                                                         |
| `assembleDebug`、`packageZygiskModule`（含 Release/R8）             | 通过                                                             |
| `testDebugUnitTest`                                                 | 245 项，242 通过、3 跳过，0 失败                                 |
| `lintDebug` / Release vital lint                                    | 通过；Debug 报告 0 errors、25 warnings                           |
| 双格式内容、APK/ZIP 等同性、apksigner、16 KiB zipalign、Linux unzip | Debug/Release 对应检查通过                                       |

日志：`app/build/art-audit/final-gradle.log`。运行时报告已按设备保留在 `app/build/native-tests/art-runtime/myron/` 与 `app/build/native-tests/art-runtime/7ef9cfd2/`，文件名为 `api-37-{pointer,indices}[-checkjni].txt`。目录还包含设备信息、ART 初始化日志与二进制身份；OnePlus 目录另含补丁/内存结果、按测试 PID 提取的日志，以及 `baseline/` 空进程对照。当前脚本仍输出到公共的 `art-runtime/` 目录，换设备运行前应先归档报告，避免覆盖证据。

最终构建为 `4.0.2.r732.1fe6c3a`，包含未提交的本轮修改，版本号本身不标识这些修改。Release APK/模块 ZIP 的 SHA-256 为 `c206de8f5175aa7aae7d6380102166a64682231a56ea7f0afa6ba6c4e6cd3c55`，使用本机 Android Debug 证书，仅用于本地验证。OnePlus 补测没有修改生产代码，沿用该构建。

以上哈希记录 ART 审计阶段的测试包；随后同日的 PLT/GOT 审计修改了原生引擎并重新构建，最新产物见 [PLT/GOT 构建记录](zygisk-plt-compatibility.md#构建与包校验)。

本轮不把 Android 13–16 的源码核对写成实机通过；尚无 16 KiB 页面设备结果。已有 AOT/JIT 调用点内联、JVMTI 方法重定义、其他框架修改入口与 OEM 后续 ART 更新，仍需要对应场景回归；这里不宣称完整的编译器去优化或重定义支持。

本次改变注入器原生代码及 JNI 签名。部署时必须用新的完整双格式包正常安装 Zygisk 模块并重启，不能只替换 `main.apk`。安装器的 injector identity 会阻止把不匹配的载荷作为热更新发布。
