# AGENTS.md

## Project Overview

TCQT provides message anti-recall and other features for QQ/TIM Android (NT architecture), through LSPosed or Zygisk. minSdk is Android 8.1; Android 17 arm64 has device regression coverage, while ART compatibility still depends on the runtime/OEM. See [ARCHITECTURE.md](ARCHITECTURE.md) and [DEVELOPMENT.md](DEVELOPMENT.md).

## Build Commands

```bash
# Build debug APK (automatic debug certificate)
./gradlew :app:assembleDebug

# Build release APK (configure release credentials for distribution)
./gradlew :app:assembleRelease

# Build both
./gradlew :app:assembleDebug :app:assembleRelease

# JVM tests, architecture checks, lint and the compiled Debug example
./gradlew :libs:core:test :libs:processor:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --configuration-cache

# Compile real KSP positive/negative, multi-round and incremental fixtures
python tools/test-action-processor.py

# Build Zygisk module ZIP (Magisk/KernelSU; a copy of the dual-format release APK)
./gradlew :app:packageZygiskModule

# Build dual-format APK for a specific build type (.apk installs, rename to .zip to flash)
./gradlew :app:buildDualApkRelease
./gradlew :app:buildDualApkDebug

# Clean build
./gradlew clean :app:assembleDebug
```

Output APKs: `app/build/outputs/apk/{debug,release}/TCQT-*.apk` (dual-format: installable APK + flashable Zygisk module in one file, no nested payload APK)
Output Zygisk module: `app/build/outputs/zygisk/TCQT-zygisk-*.zip` (byte-identical copy of the dual-format release APK)

Raw AGP APKs remain in `app/build/intermediates/apk/<variant>/package<Variant>/` and must not be distributed as dual packages.
`SingleArtifact.APK` is transformed by `TransformDualApkTask`; install and IDE consumers use the transformed artifact.
Shared packaging implementation lives in `buildSrc/`. Signing precedence is Android Studio injected settings,
environment settings, then the local debug keystore. Without release credentials the dual release package uses
the debug certificate, so it is suitable for local validation only. Tool versions come from the configured AGP SDK/NDK.
Package content checks: `python tools/verify-dual-apk.py <dual.apk> --raw <raw.apk> --zip <module.zip>`;
also run the configured SDK's `apksigner verify`, `zipalign -c -P 16 4`, and Linux `unzip -t`.

## Requirements

- **JDK 21** (enforced via toolchain)
- **Android SDK** with compileSdk 37, minSdk 27
- **Git** required for version generation (commit count + short hash)
- `local.properties` must point to valid `sdk.dir`

## Project Structure

```
app/                    # Main module (com.owo233.tcqt)
├── src/main/
│   ├── cpp/            # Native CMake entry + source32 scanner
│   │   └── zygisk/    # Injector/bootstrap, ART, PLT, logging, framework API
│   ├── java/
│   │   ├── com/owo233/tcqt/
│   │   │   ├── api/        # Feature / InfraTask / declarative options
│   │   │   ├── core/       # Android adapters, configuration, runtime services
│   │   │   ├── features/   # Hook implementations, categorized by package
│   │   │   ├── host/       # QQ/TIM capabilities and type adapters
│   │   │   ├── loader/     # Xposed entry points (legacy + modern + zygisk)
│   │   │   └── ui/         # Compose settings UI and parasitic Activity
│   │   └── top/artmoe/inao/# QQ protobuf message models
│   ├── proto/         # Protobuf definitions
│   └── zygisk-template/ # Magisk/KernelSU module template for Zygisk mode
libs/
├── annotations/        # @RegisterAction annotation
├── core/               # Pure JVM protocol, DEX policies, Hook/startup state
├── processor/          # KSP code generator
└── qqinterface/        # QQ class stubs (compileOnly)
webui/                 # Offline WebUI validation/staging
buildSrc/              # Typed dual-format package tasks
examples/              # Compiled Debug-only examples; registration disabled
```

## Key Architecture Patterns

### Action Registration (KSP)

1. Create a `Feature` subclass in `app/src/main/java/com/owo233/tcqt/features/<category>/`; infrastructure tasks use `InfraTask`, not direct `ActionSpec` implementations.
2. Annotate with `@RegisterAction` (from `libs:annotations`)
3. KSP processor generates `GeneratedActionList` at build time
4. `ActionRegistry` filters lightweight descriptors by process and priority before instantiating selected actions. `StartupScheduler` executes the plan.

Example:
```kotlin
import com.owo233.tcqt.annotations.ActionProcess
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature

@RegisterAction(processes = [ActionProcess.MAIN])
object MyHook : Feature(key = "my_feature", name = "My Feature") {
    override fun install() {
        // Hook logic here
    }
}
```

The complete [SampleFeature.kt](examples/SampleFeature.kt) is compiled by Debug builds, excluded from Release, and uses `enabled=false` to keep it out of the runtime registry. Process/priority metadata belongs only in the annotation; category comes from the feature package. Option delegates return values directly (for example, `mode`, not `mode.value`). Never rename published configuration keys.

KSP accepts public/internal objects or concrete classes with a JVM no-argument constructor. It rejects inaccessible, abstract, inner, generic and companion declarations, normalizes process metadata, and emits one stable sorted table across rounds.

Only CRITICAL hooks run in the original Application.onCreate Before callback. `StartupAfterCreate` submits the remaining plan after the original call succeeds. MAIN Deferred hooks wait for an actual Android frame signal; do not initialize QQ's static Activity helpers to detect a frame. `:libs:core` must remain independent of Android/QQ/Xposed/UI types; app `core` consumes injected host capabilities rather than concrete host types.

### Process Model

QQ runs multiple processes. Hooks specify which process(es) to run in:
- `MAIN` - Main UI process (default)
- `MSF` - Message service
- `TOOL`, `OPENSDK`, `QZONE`, `QQFAV` - Other processes
- `ALL` - All processes

### Zygisk Mode (no Xposed framework needed)

TCQT can also run by injecting into QQ/TIM through a Zygisk module
(`./gradlew :app:buildDualApkRelease` or `:app:packageZygiskModule`). The build
emits a **dual-format package** (FunBox-style): one file that is simultaneously
an installable APK and a flashable Zygisk module zip — the module files
(`customize.sh`, `module.prop`, `zygisk/arm64-v8a.so`, `META-INF/...`) sit at the
zip root next to the APK entries (`classes*.dex`, `AndroidManifest.xml`, ...).
No payload APK is nested inside, keeping the package ≈ the plain APK size.
Architecture:

- `app/src/main/zygisk-template/customize.sh` — `SKIPUNZIP=1`; extracts only the
  module files (incl. `webroot/*`) and stages the whole `$ZIPFILE` (which is
  the APK itself) under `/data/adb/tcqt/pending/<sha256>.apk`. `payload.sha256`
  in the module selects the exact package. `post-fs-data.sh` publishes it as
  `/data/adb/tcqt/main.apk` and records the boot ID plus embedded injector identity.
  `cpp/cmake/InjectorIdentity.cmake` hashes normalized Zygisk native sources,
  headers, CMake rules and effective toolchain/configuration, excluding Java,
  app versions, checkout paths and line endings. It embeds `TCQT_INJECTOR_ID_V1`
  in the actual library; packaging rejects missing/duplicate IDs. Whole-file
  SHA-256 remains for integrity, not native compatibility. Only updates
  matching the boot identity record and live injector identity may publish during
  installation; native changes and upgrades from older installers wait for a
  full reboot. `payload.sh` shares atomic copy and SHA-256 publication helpers. The
  installed module dir holds no payload; `zygisk/arm64-v8a.so` is the injector.
- `app/src/main/zygisk-template/webroot/` — KernelSU/APatch WebUI: per-app
  injection toggles backed by realtime marker files
  `/data/adb/tcqt/{qq,tim}.disable` (per-user: `user_<id>/` subdir); plus a
  **原生 Hook** section whose toggles are also per host × user, using markers
  `<host>.<hookId>.disable` (e.g. `qq.fekit.disable`, default enabled); the
  injector re-checks them in `preAppSpecialize` on every fork, so changes take
  effect on the next app start without rebooting (mirrors FunBox's scope
  markers, but default-enabled). The UI uses native HTML/CSS/JS following Miuix styling, with a 64 KiB raw
  asset budget. `:webui:assembleWebUi` checks and stages the offline assets;
  no Node/npm, WASM runtime or bundled font is required for builds.
  「导出并分享日志」 calls `share-logs.sh` and a standalone `logshare.dex`
  compiled from `LogShare.java`, independent of `main.apk` and app R8 output.
  The helper runs as shell UID with an explicit VM classpath, publishes a
  MediaStore attachment and opens the Android chooser with a content URI/read
  grant; APK installation is not required. Failed sharing retains the report
  at `/data/adb/tcqt/last-share-report.txt`. Boot payload activation stages are
  recorded in `/data/adb/tcqt/activation.log` and included in exported reports.
- `app/src/main/cpp/zygisk/plt/plt_hook.h/.cpp` — generic PLT/GOT hook engine with a
  declarative spec table in `zygisk/plt/default_plt_hooks.cpp`:
  new hooks are added by writing one same-signature
  replacement function and appending one `PLT_HOOK_SPEC(id, lib, symbol,
  hook_fn, real)` row to `kDefaultPltHooks` (see `plt_hook.h`); the engine
  handles library discovery (background poller, 50ms — do NOT add a
  dlopen/android_dlopen_ext GOT trigger: it redirects system libs' loads into
  the module and reentrantly scans in-flight link_maps on Android 17+,
  crashing `System.load`), GOT slot patching and real symbol resolution. The
  `id` doubles as the WebUI marker
  suffix (`<host>.<id>.disable`): `zygisk_entry` collects disabled ids per
  host/user in `preAppSpecialize` and `install_default_plt_hooks(disabled_ids)`
  skips them, so each hook can be toggled per host × user. Currently contains
  the `libfekit.so` `fopen` detection evasion (`/proc/self/smaps` → `/dev/null`),
  installed only in the `:MSF` process.
- `app/src/main/cpp/zygisk/logging/log_file.h/.cpp` — logs in `files/.tcqt/logs/`, separated
  by process: INFO+ in `*.events.log` (1 MiB + 3 backups), optional DEBUG in
  `*.debug.log` (512 KiB + 1 backup). `debug.enable` globally opts in at next
  app start. A stable per-file flock protects append/rotation across overlapping
  process instances; every rotated file has a device header. A bounded 64 KiB
  buffer replays early injection events after specialization. Java framework,
  lifecycle and callback failures use priority-aware `ZygiskEntry.nativeLog`.
  Export includes new and legacy rotations (`log.txt.1`) plus recent injection logcat.
- `app/src/main/cpp/zygisk/zygisk_entry.cpp` and `zygisk_companion.h/.cpp` —
  Zygisk injector with a root companion. `preAppSpecialize` runs in the
  Zygote SELinux domain, which may not traverse `/data/adb` even at UID 0.
  It calls `connectCompanion()`; the companion checks the live module's
  disable/remove markers, host/user scope, debug and PLT switches,
  opens `/data/adb/tcqt/main.apk`, and reads its SHA-256 fingerprint. The
  read-only APK fd is transferred with `SCM_RIGHTS` and exempted from fd
  sanitation. Configuration access errors abort injection instead of being
  mistaken for missing markers. In `postAppSpecialize`, the payload is copied
  into the app's `files/.tcqt` dir when the fingerprint changes (size alone
  must never identify a version). `zygisk/bootstrap.h/.cpp` owns the payload
  class-loader and JNI bootstrap. All `classes*.dex` entries are then read
  via JNI `java.util.zip.ZipFile`, loaded with `InMemoryDexClassLoader`,
  and passed to
  `com.owo233.tcqt.loader.zygisk.ZygiskEntry.init(processName, dataDir, apkPath)`.
  See `docs/zygisk-companion.md` for protocol and regression checks.
- `app/src/main/cpp/zygisk/art/` — self-made ArtMethod hook engine. `art_hook.cpp`
  serializes initialization and publishes probed state once; `art_layout.cpp`,
  `art_symbols.cpp`, `art_memory.cpp`, `art_method_patch.cpp`, `art_dex_trust.cpp`
  and `art_invoke.cpp` own layout, symbols, memory protection, mutation,
  DEX trust and JNI original invocation respectively:
  JNI-probed ArtMethod layout (method_size / access_flags offset / entry point),
  `ScopedSuspendAll` + `WritableArtMethod` for safe mutation, ELF-symbol-resolved
  libart.so helpers, memfd dual-mapped trampoline pool (arm64).
- Java side (`loader/zygisk/`): `ZygiskEntry` bootstraps the host ClassLoader
  (hook `LoadedApk.createAppFactory` → `AppComponentFactory.instantiateClassLoader`
  → `ModuleLoader.initialize`); `ZygiskHookBridge` uses DexMaker to generate a
  same-signature `bridge`/`backup` method pair per hooked method and dispatches
  before/original/after callbacks in Java; `ZygiskHookEngine` implements
  `IHookEngine` so all existing hooks run unchanged.
- Module template at `app/src/main/zygisk-template/`; the staging task pulls
  the current build's `libtcqtzygisk.so` from the CMake obj output
  (`intermediates/cmake/<variant>/obj`) and strips it with the NDK's
  `llvm-strip` into `zygisk/arm64-v8a.so`. The injector is excluded from the
  APK's `lib/` (`packaging.jniLibs.excludes`) and from `ZygiskEntry.loadNativeLibs`
  — it is compiled only for the module, never shipped inside the APK.
  `buildDualApk*` merge the staged module files into the signed APK, re-zip
  (`.so` under `lib/` + `resources.arsc` STORED), `zipalign -p 4` and re-sign
  (v2 only; debug keystore fallback). `packageZygiskModule` just copies the
  dual-format release APK to `outputs/zygisk/TCQT-zygisk-*.zip`.

Native implementation code in cpp/zygisk/ uses namespace tcqt::zygisk.
The framework header in zygisk/api/zygisk.hpp retains its upstream
::zygisk namespace; framework registration macros stay at global scope.
source32_scanner.h/.cpp stays independent in tcqt::source32, with the
existing GetSign JNI entry and libtcqtmem.so name preserved.

Do NOT enable the TCQT module in LSPosed and Zygisk mode simultaneously
(shared config, double hooking).

### Generated Code

- `GeneratedActionList` - Auto-generated by KSP, lists enabled registrations and lightweight descriptors; do not edit generated output
- `BuildTime.kt` - Generated at build time with timestamp
- Proto classes - Generated from `.proto` files via protobuf plugin

## Build Configuration

- **Version**: Base version in root `build.gradle.kts` + git (`4.0.2.r<commit-count>.<short-hash>`); uncommitted changes do not change this identifier
- **ABI**: arm64-v8a only
- **Signing**: CI uses env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- **R8**: Full mode enabled
- **Resource optimization**: Custom package ID `0x53`, reserved ID allowed

## Important Notes

- `libs:qqinterface` is `compileOnly` - QQ's actual classes used at runtime
- Xposed API (`de.robv.android.xposed:api`) and libxposed API are `compileOnly`
- Module UI uses Jetpack Compose with Miuix 0.9.4. Read [docs/ui-guidelines.md](docs/ui-guidelines.md) before changing settings pages, dialogs, or the Zygisk WebUI; reuse `UiLayout.kt` and `UiDialog.kt`.
- DexKit used for runtime method finding in QQ's dex files
- Configuration cache enabled (`org.gradle.configuration-cache=true`)
- Kotlin code style: `official`
