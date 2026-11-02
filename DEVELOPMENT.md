# 开发与验证

架构和边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。功能 UI 和 WebUI 修改前阅读 [界面规范](docs/ui-guidelines.md)。

## 环境与构建

使用 JDK 21、Git、Android SDK，并在 `local.properties` 设置 `sdk.dir`。compile/target SDK、NDK、CMake 版本以根 [build.gradle.kts](build.gradle.kts) 为准；依赖版本由 [版本目录](gradle/libs.versions.toml) 管理。当前 ABI 为 arm64-v8a，minSdk 27。

Linux CI 使用 `bash tools/prepare-android-sdk.sh` 准备 SDK。脚本读取主/次 API 版本，安装对应平台包（当前为 `platforms;android-37.0`）、QQ stubs 平台以及固定的 CMake/NDK；重用完整的已安装工具，下载失败最多重试三次。两条 Android 工作流共用此脚本。

Kotlin 测试库和 `protoc` 优先从 Google 的 Maven Central 镜像解析，版本仍由版本目录控制。CI 回归刷新依赖缓存，并实际下载对应系统的 `protoc`，不能仅用 Windows 缓存判断 Linux 依赖是否可用。

以下是 PowerShell 命令；Linux/macOS 将 `.\gradlew.bat` 换为 `./gradlew`。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:packageZygiskModule
```

所有输出 APK 都是双格式。Debug 自动使用调试证书。发布时配置 `KEYSTORE_PATH`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，或使用 Android Studio 注入的签名配置；没有发布证书时，双格式 Release 回退到本机调试证书，仅供本地测试，不能作为官方签名发布。

输出位置：

- `app/build/outputs/apk/{debug,release}/TCQT-<版本>-<类型>.apk`
- `app/build/outputs/zygisk/TCQT-zygisk-<版本>.zip`
- 原始 AGP APK：`app/build/intermediates/apk/<类型>/package<类型首字母大写>/`，仅供包内容比对。

版本来自根脚本的基础版本、Git 提交数与短哈希。未提交的源码修改不会改变版本号；验证部署时还要比较包和原生库哈希。

## 新增功能

完整的 [SampleFeature.kt](examples/SampleFeature.kt) 已加入 Debug Kotlin 源目录。它演示 `Feature`、字符串配置委托、进程/优先级注解和通用 Hook；`enabled=false` 使 KSP 不将它加入注册表。Release 不编译此源目录。

新增实际功能时：

1. 将示例复制到 `app/src/main/java/com/owo233/tcqt/features/<分类>/`，更新包名、类名、功能 key、显示名称与逻辑。
2. 将注解的 `enabled` 改为 true 或删除该参数。它控制是否注册；`Feature` 的 `defaultEnabled` 控制新用户的开关默认值，两者不同。
3. 进程和优先级只在 `@RegisterAction` 中声明。默认是 `MAIN` 和 `DEFERRED`；只有宿主 onCreate 内第一次调用不可错过时才使用 `CRITICAL`。
4. 用 `by stringOption/intOption/...` 声明配置。委托属性直接返回 String/Int，不再访问 `.value`；持久化 key 由功能 key 与子项 key 组合，发布后不要改名。
5. 在 `install()` 使用 `core.hook` 扩展；宿主类型查找和能力适配放在 `host/`。不要在属性初始化器、`Requires` 或设置元数据读取中安装 Hook、扫描 DEX 或访问未初始化的宿主状态。
6. 运行相关测试并在 XP/Zygisk 各验证一次。构造类必须为 public/internal 的 object 或具备 JVM 无参构造的普通类；KSP 会拒绝不支持的声明。

无用户开关的运行任务继承 `api.InfraTask`。普通功能不要直接实现框架内部的 `ActionSpec`，也不要直接调用 Xposed 或 Zygisk 专用 API。

## 自动化检查

```powershell
# 纯 JVM 模块，可独立执行，不需要编译 app
.\gradlew.bat :libs:core:test :libs:core:jar

# 与 PR 门禁相同的检查；Debug 构建也编译示例
.\gradlew.bat :libs:core:test :libs:processor:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --configuration-cache

# 真实 KSP 正反例、多轮和增量编译
python tools/test-action-processor.py

# Windows + 明确指定的 Android 设备，独立原生可执行文件
.\tools\test-zygisk-companion.ps1 -Serial 7ef9cfd2
.\tools\test-art-memory.ps1 -Serial 7ef9cfd2
.\tools\test-art-method-patch.ps1 -Serial 7ef9cfd2
```

Linux/WSL 原生 IPC 检查：`bash tools/test-native-host.sh`。原生脚本从项目读取已固定的 NDK 版本；可用 `-SdkPath` 显式指定 SDK。ART 内存脚本要求 arm64 设备。

测试报告在 `app/build/reports/tests/`、`libs/core/build/reports/tests/`、`libs/processor/build/reports/tests/`；lint 报告在 `app/build/reports/lint-results-debug.html`。两项带前序阶段条件的 app 测试当前跳过，不能将跳过算作通过。门禁工作流见 [.github/workflows/checks.yml](.github/workflows/checks.yml)。

## APK 与 ZIP 校验

```powershell
python tools/verify-dual-apk.py <双格式.apk> --raw <原始AGP.apk> --zip <Zygisk.zip>
```

把占位符换为真实路径，Debug 不提供 Release ZIP 参数。脚本检查 APK 条目与原包一致、双格式模块条目、ZIP 等同性、local extra field 长度和原生库 16 KiB 对齐。打包任务使用固定的 DOS 时间、`zipalign -P 16`，并在签名后验证 V2 签名和对齐。Linux CI 在上传前另运行 `unzip -t`，发布任务下载后再验证一次；失败时保留原始解析错误。签名和对齐通过不代表宿主运行通过。

## 设备测试与模式互斥

先记录 QQ/TIM 配置、TCQT 与其他模块作用域、KernelSU 模块状态及 WebUI 范围。配置属于宿主，切换模式不要清空 QQ/TIM 数据。发布包与 Debug 签名不同时，可以卸载 TCQT 模块 APP 再安装；宿主配置保留。

| 目标模式 | 启动宿主前必须完成                                                                                                 |
|----------|--------------------------------------------------------------------------------------------------------------------|
| XP       | 在 KernelSU/Magisk/APatch 停用 TCQT Zygisk 模块；完全停止 QQ/TIM；安装目标 TCQT APK，在 LSPosed 启用相应宿主作用域 |
| Zygisk   | 在 LSPosed 取消 TCQT 对应作用域或卸载 TCQT APP；完全停止 QQ/TIM；安装/更新并启用 TCQT Zygisk 模块和相应宿主范围    |

KernelSU 停用模块后，重新启动宿主即可阻止下一次注入，无需为停用操作重启设备；正在运行的进程仍需要完全停止。若更改注入器原生库，必须完成管理器的真正模块安装，并按安装器要求重启；仅替换 payload APK 不足以验证新原生库。不要修改 `/data/adb` 的目录权限来规避 SELinux，载荷应保持安装器设置的 `system_file` 标签。

TCQT 的 Zygisk 注入可与其他 LSPosed 模块共存，TCQT 自身仍须保持两种注入方式互斥。ART 备份保留安装前已不可编译的方法入口、已识别的 ARM64 quick 跳板和 JNI 原生入口；普通托管方法使用解释器备份。新版框架的分发器可能是完整编译代码，不能只靠短跳板指令识别。若另一框架后来接管方法入口，卸载 TCQT 回调时保留空的透传桥，避免覆盖该框架的外层 hook。旧 `compat.enable` / `compat` 标记已不参与注入，安装器会清理它们；WebUI 不再提供兼容模式。

`test-art-method-patch.ps1` 使用映射内存中的 ArtMethod 测试对象验证实际补丁代码；它不代替宿主中的双框架验收。共存回归需确认 TCQT 仍以 Zygisk 注入、其他模块仍以 XP 注入，并检查双方设置入口能同时显示、分别打开、返回后再次打开，以及宿主冷启动后入口仍在。

2026-11-02 在设备 `7ef9cfd2`（一加 13T，Android 17/API 37）完成共存回归：QQ 9.3.70 (16410)、LSPosed v2.2.1 (7933)、QWish v3.0.3。TCQT Release 以 Zygisk 注入、QWish 以 XP 注入；三轮缓存命中的冷启动均显示 TCQT、QWish 和 Java脚本入口，双方设置页可打开并返回，主进程与 MSF 存活。Release 载荷与安装的注入器 SHA-256 均匹配构建产物。原生补丁 5 组、companion 18 组及 Debug 宿主 Hook/生命周期/JNI/设置回归通过；该结果仅覆盖此设备和版本组合。

每轮检查实际引擎、加载版本/哈希、主进程和 MSF 存活；观察退出原因与 crash buffer，区分宿主主动重启、force-stop 和新崩溃。进入 QQ/TIM 设置中的 **TCQT** 页面验证设置入口、开关、搜索和返回。性能测试分开冷启动与重复进入，并记录是否存在 DexKit 扫描、浮窗或预热；这些条件不能混作普通全屏启动基线。

Debug 载荷提供本轮回归接收器。例如在已确保互斥且加载 Debug 的宿主执行：

```powershell
adb -s 7ef9cfd2 shell am broadcast -a com.owo233.tcqt.DEBUG_VALIDATE -p com.tencent.mobileqq --es step 14
adb -s 7ef9cfd2 shell su -c 'cat /data/user/0/com.tencent.mobileqq/files/.tcqt/validation/14.json'
```

步骤 14 覆盖协议/缓存、8 项 Hook、4 项生命周期、22 项 JNI 类型/异常和 6 项设置检查，使用独立临时存储。将宿主包名换为 `com.tencent.tim` 可验证 TIM；步骤 07 检查首帧信号及 Deferred 时序。必须确认 JSON 的 `passed=true`、mode 正确、报告来自本轮且进程继续存活。Release 不提供这个广播入口，需实际检查启动、设置和功能。

本轮逐项记录在 [TODO.md](TODO.md)，设备证据在本地忽略的 `app/build/refactor-validation/`。不要运行 `clean` 删除尚需保留的证据，也不要提交用户聊天、截图、配置或签名文件。远程 CI 只有推送并实际运行完成后才能声明通过。
