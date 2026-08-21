<div align="center">
    <h1> TCQT </h1>

[![GitHub release](https://img.shields.io/github/release/Xposed-Modules-Repo/com.owo233.tcqt.svg)](https://github.com/Xposed-Modules-Repo/com.owo233.tcqt/releases/latest)
[![main](https://github.com/callng/TCQT/actions/workflows/android_ci.yml/badge.svg)](https://github.com/callng/TCQT/actions/workflows/android_ci.yml)
[![Telegram](https://img.shields.io/badge/Telegram-Chat-0088cc?logo=telegram)](https://telegram.me/astcqt)
[![Telegram](https://img.shields.io/badge/Telegram-CI-0088cc?logo=telegram)](https://telegram.me/citcqt)
[![Discord](https://img.shields.io/badge/Discord-%E5%8A%A0%E5%85%A5%E7%BE%A4%E7%BB%84-5865F2?logo=discord)](https://discord.gg/PjYenv5F9s)

</div>

> 一个基于 **Xposed 框架** 的 **QQ / TIM 消息防撤回多功能模块**  

---

##  环境

| 项目       | 说明                                                |
|------------|-----------------------------------------------------|
| 适配客户端 | QQ / TIM Android（NT 架构）                         |
| 系统版本   | Android 8.1 ~ 17                                    |
| 框架支持   | LSPosed（默认）；也可通过 Zygisk 注入运行（见下文） |

---

##  Zygisk 模式（可选）

除 LSPosed 外，TCQT 也支持通过 **Zygisk** 直接注入 QQ / TIM 进程运行，
**无需安装任何 Xposed 框架**。

### 环境要求

| 项目      | 说明                                                    |
|-----------|---------------------------------------------------------|
| Root 方案 | Magisk（启用 Zygisk）、KernelSU 或 APatch（ZygiskNext） |
| 架构      | 仅支持 arm64-v8a（与 QQ 全量包一致）                    |
| 系统版本  | Android 8.1 ~ 16（高版本依赖 ART 布局探测，适配中）     |

### 安装

1. 下载 `TCQT-<版本>-release.apk`（见下方编译产物，或到 CI 构建中获取）。
2. 该文件是**双格式包**：直接安装即为 Xposed 模块 ；
   如需 Zygisk 模式，把它改名为 `.zip`，
   在 Magisk / KernelSU / APatch 应用中选择「从本地安装」刷入，然后重启。
3. 安装并打开 TCQT App 进行功能配置（配置与 LSPosed 模式完全相同，
   存储在宿主 QQ/TIM 的数据目录中）。
4. 完全结束后台 QQ / TIM 进程并重新启动，功能即生效。

### 卸载

在 Magisk / KernelSU / APatch 中直接卸载 `TCQT (Zygisk)` 模块并重启即可；

### 注意事项

- **不要同时启用 LSPosed 中的 TCQT 模块**：两种模式共用同一份配置，
  同时 hook 会造成冲突。使用 Zygisk 模式时请在 LSPosed 中停用 TCQT。
- 当前以 arm64-v8a 为主，Android 15/16 为实验性支持。
- 功能范围与 LSPosed 模式一致（包括 DexKit 方法查找、设置页联动等）。

### 注入控制（WebUI）

在支持模块 WebUI 的 KernelSU / APatch 管理器（模块详情页）中可打开
**TCQT WebUI**，按应用（QQ / TIM）× 用户独立控制是否注入；同时提供
**原生 Hook** 开关（Zygisk 模式下的 PLT/GOT hook)

---

##  设置

从 **v2.7 版本** 起，模块支持通过宿主 APP 的设置页面控制功能状态：

> 宿主设置页面 → **TCQT**

如果宿主设置中没有看到模块入口，也可以通过以下方式打开模块设置页面：

> 在聊天界面发送消息：`tcqt.qq.com`  
> 点击该消息中的链接，即可进入模块设置界面。

---

##  功能

模块功能持续更新中，  
请前往 **模块设置页面** 查看详细的功能列表与开关说明。

---

##  编译

### 环境要求

| 依赖        | 说明                                 |
|-------------|--------------------------------------|
| JDK         | **21** （由项目 toolchain 强制要求） |
| Android SDK | compileSdk 37、minSdk 27             |
| Git         | 用于生成版本号（提交数 + 短哈希）    |

> 请确保 `local.properties` 中的 `sdk.dir` 指向有效的 Android SDK 路径。  
> 以下命令在 Windows 下请使用 `gradlew.bat`，macOS / Linux 下使用 `./gradlew`。

### 编译调试版本

调试版本无需签名，可直接编译：

```bash
./gradlew :app:assembleDebug
```

### 编译发布版本

发布版本需要签名配置，可通过环境变量指定签名信息：

**Windows（PowerShell）：**

```powershell
$env:KEYSTORE_PATH = "path\to\keystore.jks"
$env:KEYSTORE_PASSWORD = "your_store_password"
$env:KEY_ALIAS = "your_key_alias"
$env:KEY_PASSWORD = "your_key_password"
.\gradlew.bat :app:assembleRelease
```

**macOS / Linux（bash）：**

```bash
KEYSTORE_PATH=/path/to/keystore.jks \
KEYSTORE_PASSWORD=your_store_password \
KEY_ALIAS=your_key_alias \
KEY_PASSWORD=your_key_password \
./gradlew :app:assembleRelease
```

> 未配置签名信息时，发布版本无法编译；CI 环境已自动注入以上变量。

### 编译 Zygisk 模块（可选）

`assembleDebug` / `assembleRelease` 产出的 APK 本身就是**双格式 APK**：
既是可安装的 APK，也是 Magisk / KernelSU 可刷入的模块 ZIP（改名 `.zip` 即可刷入），
Android Studio 里直接 Build / Generate Signed APK 也会得到同样的双格式产物。

如需额外的 `.zip` 文件，可执行：

```bash
./gradlew :app:packageZygiskModule
```

它会复制 release 双格式 APK 到：

```
app/build/outputs/zygisk/TCQT-zygisk-<版本>.zip   # 双格式 APK 的 .zip 副本
```

### 产物输出

编译完成后，APK 输出到以下目录：

```
app/build/outputs/apk/debug/    # 调试版本（TCQT-<版本>-debug.apk，双格式）
app/build/outputs/apk/release/  # 发布版本（TCQT-<版本>-release.apk，双格式）
```

### 清理构建

```bash
./gradlew clean :app:assembleDebug
```

---

##  声明

### 一切开发旨在学习，请勿用于非法用途

- 本项目开源，欢迎提交 PR，但是请不要提交用于非法用途的功能。
- 如果某功能被大量运用于非法用途，或对其他用户的正常使用造成严重影响，那么该功能将会被移除。
- 本模块完全免费开源，没有任何收费，请勿二次贩卖。
- 鉴于项目的特殊性，开发者可能在任何时间**停止更新**或**删除项目**。

---

##  不会增加的功能

- 抢红包及其他金钱相关功能
- 可能被恶意利用的功能
- 群发消息
- 可能干扰正常使用的功能（如闪退）

---

##  致谢

> **本项目的一些代码借鉴了一些开源项目，特别感谢有这样一群可爱的开发和贡献者，向他们致敬！**

| Name       | Source                                                |
|------------|-------------------------------------------------------|
| QAuxiliary | [QAuxiliary](https://github.com/cinit/QAuxiliary)     |
| XAutoDaily | [XAutoDaily](https://github.com/LuckyPray/XAutoDaily) |
| TimTool    | [TimTool](https://github.com/suzhelan/TimTool)        |

> **本项目的开发动力和灵感来源于 QwQ 项目，感谢 fuqiuluo ！**

| Name | Source                                 |
|------|----------------------------------------|
| QwQ  | [QwQ](https://github.com/fuqiuluo/QwQ) |

---

##  其他

> 本模块已发布到 LSPosed 模块仓库，也可到本仓库下载实时构建(CI)版本。

| Type                | Source                                                                  |
|---------------------|-------------------------------------------------------------------------|
| LSPosed Module Repo | [TCQT](https://github.com/Xposed-Modules-Repo/com.owo233.tcqt/releases) |
| Here                | [TCQT](https://github.com/callng/TCQT/actions/workflows/android_ci.yml) |

### 额外说明

- 不建议与其他 QQ / TIM 模块一同使用，避免功能冲突。
- 请**始终**在 LSPosed 仓库或本项目以及 [CI 频道](https://t.me/citcqt) 下载本模块。

---

<p align="center">
  <sub>Made with ❤️ by owo233</sub>
</p>
