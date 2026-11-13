#include "zygisk/bootstrap.h"

#include <cstdint>
#include <vector>

#include "zygisk/jni_bridge.h"
#include "zygisk/logging/log.h"
#include "zygisk/payload.h"

namespace tcqt::zygisk {
namespace {
constexpr const char *ENTRY_CLASS = "com.owo233.tcqt.loader.zygisk.ZygiskEntry";
}  // namespace

bool bootstrap_payload(JNIEnv *env, const std::string &apk_path,
                       const std::string &process_name, const std::string &data_dir) {
    // Read all classes*.dex from the copied package and build an
    // InMemoryDexClassLoader. An APK-path DexClassLoader is not usable
    // here: Android 10+ refuses to load dex from app-writable paths
    // ("Attempt to load writable dex file"), so the payload is loaded
    // from memory instead. kotlinx-coroutines' Main dispatcher discovery
    // (META-INF/services) is therefore unavailable; the Java side
    // compensates by hooking Dispatchers.getMain() in
    // ModuleLoader.installMainDispatcher().
    jobject loader = nullptr;
    {
        // ART copies direct-buffer DEX data into its own MemMap during the
        // InMemoryDexClassLoader constructor (including Android 8.1). Only
        // retain the source bytes until that synchronous call returns.
        // Scope ownership also releases partially read DEX on failure.
        std::vector<std::vector<uint8_t>> dex_bufs;
        if (!read_dex_from_apk(env, apk_path, &dex_bufs)) {
            LOGE("postAppSpecialize: failed to read dex from %s", apk_path.c_str());
            return false;
        }
        loader = build_dex_classloader(env, dex_bufs);
    }
    if (loader == nullptr) {
        LOGE("postAppSpecialize: failed to build InMemoryDexClassLoader");
        return false;
    }

    // Load the entry class and register its natives.
    jclass entry = load_class_from_loader(env, loader, ENTRY_CLASS);
    if (entry == nullptr) {
        LOGE("postAppSpecialize: ZygiskEntry class not found");
        env->DeleteLocalRef(loader);
        return false;
    }
    if (!register_entry_natives(env, loader)) {
        env->DeleteLocalRef(entry);
        env->DeleteLocalRef(loader);
        return false;
    }

    // Call ZygiskEntry.init(processName, dataDir, apkPath).
    jmethodID init = env->GetStaticMethodID(
            entry, "init", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (init == nullptr) {
        env->ExceptionClear();
        LOGE("postAppSpecialize: ZygiskEntry.init not found");
        env->DeleteLocalRef(entry);
        env->DeleteLocalRef(loader);
        return false;
    }
    jstring j_process = env->NewStringUTF(process_name.c_str());
    jstring j_data = env->NewStringUTF(data_dir.c_str());
    jstring j_apk = env->NewStringUTF(apk_path.c_str());
    if (j_process == nullptr || j_data == nullptr || j_apk == nullptr ||
        env->ExceptionCheck()) {
        env->ExceptionClear();
        LOGE("postAppSpecialize: string allocation failed");
        env->DeleteLocalRef(entry);
        env->DeleteLocalRef(loader);
        return false;
    }
    env->CallStaticVoidMethod(entry, init, j_process, j_data, j_apk);
    const bool initialized = !env->ExceptionCheck();
    if (!initialized) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("postAppSpecialize: ZygiskEntry.init failed");
    } else {
        LOGI("postAppSpecialize: ZygiskEntry.init completed for %s",
             process_name.c_str());
    }
    env->DeleteLocalRef(j_process);
    env->DeleteLocalRef(j_data);
    env->DeleteLocalRef(j_apk);
    env->DeleteLocalRef(entry);
    env->DeleteLocalRef(loader);
    return initialized;
}

}  // namespace tcqt::zygisk
