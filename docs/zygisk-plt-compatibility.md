# Zygisk / PLT-GOT：Android 13–17 兼容记录

核对及设备测试日期：2026-11-14。范围为 Android 13–17 / API 33–37 / arm64，与 Zygisk 注入入口一致。PLT/GOT 依赖 ELF 和 Bionic 动态链接器，独立于 ART 方法布局。

## Google 链接器基线

直接核对以下 Bionic release 分支的固定提交，读取 `linker.cpp`、`dlfcn.cpp`、`linker_soinfo.cpp`、`linker_reloc_iterators.h`、`linker_sleb128.h`：

| Android | API | Bionic 提交                                                                                                                                |
|---------|----:|--------------------------------------------------------------------------------------------------------------------------------------------|
| 13      |  33 | [`0b981d1ec055efaef86d8a5d402cf1b3167ba10b`](https://android.googlesource.com/platform/bionic/+/0b981d1ec055efaef86d8a5d402cf1b3167ba10b/) |
| 14      |  34 | [`e4df46f213d8d363017dce7ded5d1f2159614425`](https://android.googlesource.com/platform/bionic/+/e4df46f213d8d363017dce7ded5d1f2159614425/) |
| 15      |  35 | [`361ba86734fb2821a6adcfdf775db8abd04e0de0`](https://android.googlesource.com/platform/bionic/+/361ba86734fb2821a6adcfdf775db8abd04e0de0/) |
| 16      |  36 | [`09a271af557444c9a6b3f3146d6d474156fd6cdb`](https://android.googlesource.com/platform/bionic/+/09a271af557444c9a6b3f3146d6d474156fd6cdb/) |
| 17      |  37 | [`06356e41c5ed7b12220b24c05ba4fdb873b126a7`](https://android.googlesource.com/platform/bionic/+/06356e41c5ed7b12220b24c05ba4fdb873b126a7/) |

五个基线都支持 `DT_RELA`、`DT_JMPREL`、`DT_ANDROID_RELA`（APS2）、`DT_RELR` / `DT_ANDROID_RELR`。五份 APS2 relocation iterator 内容一致。动态表指针和重定位槽位地址以 `dlpi_addr` 加 ELF 虚拟偏移计算；arm64 使用 RELA。RELR 只编码相对重定位，没有需要按名称拦截的导入符号。

五个基线的 `__loader_dl_iterate_phdr` 都持有 `g_dl_mutex` 调用扫描回调。因此继续在回调内读取和修改槽位，不把 `dl_phdr_info` 指针留给异步线程。后台每 50 ms 调用一次遍历；不安装 `dlopen` / `android_dlopen_ext` 的 GOT 触发器，避免改变加载者命名空间或在加载过程中重入扫描。

页面大小从 `sysconf(_SC_PAGESIZE)` 读取。只临时放开 GOT 所在页的写权限，再恢复原权限。16 KiB ELF 对齐和真实 16 KiB 内核运行是不同的验收项，见 [Android 页面大小文档](https://developer.android.com/guide/practices/page-sizes)。

## 本次复现与修复

修复前在 OnePlus 上用实际 NDK 构建的独立测试库复现：

- 普通 RELA、并发安装场景及现有默认 `fopen` 功能通过。
- APS2 和 APS2 + RELR 库的 `getpid` PLT 调用被拦截，但 `getuid` 的 `GLOB_DAT` 未被拦截。原实现没有扫描 `DT_ANDROID_RELA`，不是仅 Android 17 才会触发的差异。
- 先出现名称符合子串规则、但没有目标导入的库时，原实现把“见到库”计为成功并停止轮询；随后加载的真正目标库未被拦截。

修复后：

1. `plt_elf.cpp` 统一收集普通 RELA、PLT RELA 和 APS2 中的 `R_AARCH64_JUMP_SLOT` / `R_AARCH64_GLOB_DAT`。正确处理分组字段、负偏移增量、addend 字段和重复槽位；保留 RELR 已完成的重定位结果。
2. 动态表遍历受 `PT_DYNAMIC` 大小限制，表、符号、字符串及槽位受可读 `PT_LOAD` 范围限制。校验条目大小、字符串终止、指针对齐和算术溢出；先完成解析再写槽位，格式错误不发布部分解析结果。
3. 只有完整解析并成功写入至少一个目标槽位，才减少待安装计数。无槽位、无效元数据或写入失败继续重试，保留约五分钟的启动期轮询上限。
4. 安装初始化串行化；快照持有库名和符号名，拒绝无效描述项。GOT 使用对齐的原子读写；恢复页面权限失败会记录错误。删除了不适用于 ELF 槽位地址的旧 PAC 辅助代码。

默认 Hook 的名称、开关键和业务行为未改变。

## 两台设备实测

两台均为 Android 17/API 37、arm64、4096 字节页面。设备系统指纹与 ART 审计相同，详见 [ART 设备记录](zygisk-art-compatibility.md#验证与限制)。

| 设备                      | ADB 标识                                                                      | 测试进程身份             |
|---------------------------|-------------------------------------------------------------------------------|--------------------------|
| OnePlus PKX110 / OP60F5L1 | `7ef9cfd2`                                                                    | UID 0，`u:r:ksu:s0`      |
| Redmi 25102RKBEC / myron  | `192.168.0.136:6677`，原无线设备服务 `altdb-dd5e2bdb329a00b1989f1d14c8fbeb41` | UID 2000，`u:r:shell:s0` |

测试链接项目实际的 PLT 生产实现；测试库由 NDK `30.0.16248370` 构建，启用 RELRO/NOW、BTI/PAC 编译标记和 16 KiB 最大页面对齐，并保留 readelf 报告确认实际重定位类型。

| 场景                                                                 | OnePlus | Redmi |
|----------------------------------------------------------------------|---------|-------|
| ELF/APS2 解析：正常分组、负增量、截断、溢出、边界与原子发布，共 5 组 | 通过    | 通过  |
| 普通 RELA：JUMP_SLOT + GLOB_DAT、原函数调用                          | 通过    | 通过  |
| Android APS2：压缩的 GLOB_DAT                                        | 通过    | 通过  |
| APS2 + Android RELR：拦截导入并保留相对数据                          | 通过    | 通过  |
| 安装后延迟加载，同时反复加载/卸载其他库                              | 通过    | 通过  |
| 先见到不含目标符号的同名片段库，随后继续发现真正目标                 | 通过    | 通过  |
| 8 个线程同时安装同一组 Hook                                          | 通过    | 通过  |
| 生产默认 fopen Hook：匹配路径、原函数、库作用域和 RELRO 恢复         | 通过    | 通过  |
| 默认开关禁用：不拦截目标库                                           | 通过    | 通过  |

每个设备的 6 个通用运行场景各执行 4 线程 × 10,000 次 × 2 个导入调用，共至少 480,000 次并发调用。已加载目标库在安装前后的映射权限一致；主测试程序和非目标库的调用保持原行为。原始 ELF/APS2 失败证据保留在 OnePlus 的首次运行目录。

可复用命令：

```powershell
.\tools\test-plt-hook.ps1 -Serial <serial>
```

脚本按设备和时间创建独立目录 `app/build/native-tests/plt/<serial>/<time>/`，保存设备身份、ELF 重定位表、结果及按测试 PID 提取的 logcat；测试结束删除本次推送的临时文件。复查目录：

- 修复前 OnePlus：`7ef9cfd2/20261114-165653-795/`，4 个运行场景失败。
- 修复后 OnePlus：`7ef9cfd2/20261114-170103-673/`，9 个场景全部通过。
- 修复后 Redmi：`192.168.0.136_6677/20261114-170103-703/`，9 个场景全部通过。

## 构建与包校验

`./gradlew :app:assembleDebug :app:packageZygiskModule --configuration-cache --no-parallel` 成功，包含 Release 原生编译、R8 和 vital lint。Debug/Release 双格式内容、APK V2 签名、16 KiB zipalign 和 Linux `unzip -t` 均通过，Release APK 与模块 ZIP 字节一致。日志为 `app/build/plt-audit/gradle.log`，产物记录为 `app/build/plt-audit/artifacts.json`。

- 版本：`4.0.2.r732.1fe6c3a`。修改尚未提交，不能仅靠版本号区别先前 ART 测试包。
- 模块：`app/build/outputs/zygisk/TCQT-zygisk-4.0.2.r732.1fe6c3a.zip`，3,917,844 字节。
- SHA-256：`69c66c8a8b90020b7fd46d759b0c45eea412f212965e9c6ce74529d715ebb3df`。
- Injector identity：`4e1e5164197a68d385597b270b89bb7207b8756aaa1797d535879f6e0dc031a1`。
- 本机构建使用 Android Debug 证书，供本地验证。

## 验证范围

Android 13–16 本轮是 Google 源码核对，没有对应版本实机；也没有 16 KiB 页面设备。两台 Android 17 结果来自独立原生测试进程，不等同于 QQ/TIM 应用 UID/SELinux 域内的完整 Zygisk 部署验收。没有安装模块、替换宿主载荷、停止 QQ/TIM 或重启设备。

引擎拦截所选库的导入槽位。库内直接调用、已缓存的函数指针、内联代码和直接系统调用不属于此路径。原函数通过 `dlsym(RTLD_DEFAULT)` 解析，本次验证的是 libc 的默认版本符号，不承诺任意私有命名空间或同名多版本 provider 的链式 Hook。

50 ms 轮询有发现窗口，不能保证拦截目标库构造器或刚完成 `dlopen` 后的第一次调用。所有 spec 成功或约五分钟超时后停止扫描；卸载再加载目标库、超时后才加载等长生命周期场景不在当前启动期机制的保证范围内。

本次修改注入器原生实现，更新时应安装完整新模块并重启，不能只替换 `main.apk`。
