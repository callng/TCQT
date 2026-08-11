# AGENTS.md

## Project Overview

TCQT is an Xposed module for QQ/TIM Android (NT architecture) providing message anti-recall and other features. Targets LSPosed framework on Android 8.1-16.

## Build Commands

```bash
# Build debug APK (no signing required)
./gradlew :app:assembleDebug

# Build release APK (requires signing config)
./gradlew :app:assembleRelease

# Build both
./gradlew :app:assembleDebug :app:assembleRelease

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

## Requirements

- **JDK 21** (enforced via toolchain)
- **Android SDK** with compileSdk 37, minSdk 27
- **Git** required for version generation (commit count + short hash)
- `local.properties` must point to valid `sdk.dir`

## Project Structure

```
app/                    # Main module (com.owo233.tcqt)
├── src/main/
│   ├── cpp/            # Zygisk native injector + self-made ArtMethod hook engine
│   ├── java/
│   │   ├── com/owo233/tcqt/
│   │   │   ├── hooks/      # Hook implementations
│   │   │   ├── ext/        # Core interfaces (IAction, Setting)
│   │   │   ├── loader/     # Xposed entry points (legacy + modern + zygisk)
│   │   │   ├── ui/         # Compose settings UI
│   │   │   └── utils/      # DexKit, reflection, logging
│   │   └── top/artmoe/inao/# QQ protobuf message models
│   ├── proto/         # Protobuf definitions
│   └── zygisk-template/ # Magisk/KernelSU module template for Zygisk mode
libs/
├── annotations/        # @RegisterAction annotation
├── processor/          # KSP code generator
└── qqinterface/        # QQ class stubs (compileOnly)
```

## Key Architecture Patterns

### Action Registration (KSP)

1. Create hook class implementing `IAction` in `app/src/main/java/com/owo233/tcqt/hooks/`
2. Annotate with `@RegisterAction` (from `libs:annotations`)
3. KSP processor generates `GeneratedActionList` at build time
4. `ActionManager` loads and executes registered actions per-process

Example:
```kotlin
@RegisterAction
object MyHook : IAction {
    override val key = "my_feature"
    override val name = "My Feature"
    override val processes = setOf(ActionProcess.MAIN) // which QQ processes

    override fun onRun(app: Application, process: ActionProcess) {
        // Hook logic here
    }
}
```

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
  module files (incl. `webroot/*`) and copies the whole `$ZIPFILE` (which is
  the APK itself) to `/data/adb/tcqt/main.apk` (atomic tmp+mv). The installed
  module dir holds no payload; `zygisk/arm64-v8a.so` is the injector.
- `app/src/main/zygisk-template/webroot/` — KernelSU/APatch WebUI: per-app
  injection toggles backed by realtime marker files
  `/data/adb/tcqt/{qq,tim}.disable` (per-user: `user_<id>/` subdir); the
  injector re-checks them in `preAppSpecialize` on every fork, so changes take
  effect on the next app start without rebooting (mirrors FunBox's scope
  markers, but default-enabled).
- `app/src/main/cpp/zygisk_entry.cpp` — Zygisk API v4 injector: in
  `preAppSpecialize` (still root) opens `/data/adb/tcqt/main.apk` and keeps the
  fd; in `postAppSpecialize` copies it into the app's `files/.tcqt` dir
  (size-checked), reads every `classes*.dex` entry from that copy via JNI
  `java.util.zip.ZipFile` (mirrors FunBox's loader), loads them via
  `InMemoryDexClassLoader` and calls
  `com.owo233.tcqt.loader.zygisk.ZygiskEntry.init(processName, dataDir, apkPath)`.
- `app/src/main/cpp/art_hook.cpp` — self-made ArtMethod hook engine:
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
  `libtcqtzygisk.so` from AGP's stripped native libs as `zygisk/arm64-v8a.so`.
  `buildDualApk*` merge the staged module files into the signed APK, re-zip
  (`.so` under `lib/` + `resources.arsc` STORED), `zipalign -p 4` and re-sign
  (v2 only; debug keystore fallback). `packageZygiskModule` just copies the
  dual-format release APK to `outputs/zygisk/TCQT-zygisk-*.zip`.

Do NOT enable the TCQT module in LSPosed and Zygisk mode simultaneously
(shared config, double hooking).

### Generated Code

- `GeneratedActionList` - Auto-generated by KSP, lists all `@RegisterAction` classes
- `BuildTime.kt` - Generated at build time with timestamp
- Proto classes - Generated from `.proto` files via protobuf plugin

## Build Configuration

- **Version**: Derived from git (`3.6.5.r<commit-count>.<short-hash>`)
- **ABI**: arm64-v8a only
- **Signing**: CI uses env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)
- **R8**: Full mode enabled
- **Resource optimization**: Custom package ID `0x53`, reserved ID allowed

## Important Notes

- `libs:qqinterface` is `compileOnly` - QQ's actual classes used at runtime
- Xposed API (`de.robv.android.xposed:api`) and libxposed API are `compileOnly`
- Settings UI uses Jetpack Compose with Material3
- DexKit used for runtime method finding in QQ's dex files
- Configuration cache enabled (`org.gradle.configuration-cache=true`)
- Kotlin code style: `official`
