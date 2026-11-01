#include "zygisk/art/art_runtime.h"
#include "zygisk/logging/log.h"
namespace tcqt::zygisk::art_detail {
bool resolve_symbols(JNIEnv *env, const ArtLibrary &art, RuntimeSymbols *result) {
    // 5. Resolve libart.so symbols.
    auto resolve = [&art](const char *sym) -> uintptr_t {
        uintptr_t off = resolve_elf_symbol(art.path, sym);
        return off != 0 ? art.base + off : 0;
    };
    uintptr_t suspend_ctor = resolve("_ZN3art16ScopedSuspendAllC2EPKcb");
    if (suspend_ctor == 0) suspend_ctor = resolve("_ZN3art16ScopedSuspendAllC1EPKcb");
    uintptr_t suspend_dtor = resolve("_ZN3art16ScopedSuspendAllD2Ev");
    if (suspend_dtor == 0) suspend_dtor = resolve("_ZN3art16ScopedSuspendAllD1Ev");
    uintptr_t set_not_intrinsic = resolve("_ZN3art9ArtMethod15SetNotIntrinsicEv");
    uintptr_t set_trusted =
            resolve("_ZN3artL18DexFile_setTrustedEP7_JNIEnvP7_jclassP8_jobject");
    uintptr_t runtime_instance = resolve("_ZN3art7Runtime9instance_E");
    uintptr_t set_runtime_debug_state =
            resolve("_ZN3art7Runtime20SetRuntimeDebugStateENS0_17RuntimeDebugStateE");
    uintptr_t set_java_debuggable = resolve("_ZN3art7Runtime17SetJavaDebuggableEb");

    // DexFile.setTrusted(Object) JNI fallback (package-private static).
    jmethodID set_trusted_method = nullptr;
    if (set_trusted == 0) {
        jclass dex_cls = env->FindClass("dalvik/system/DexFile");
        if (dex_cls != nullptr) {
            set_trusted_method =
                    env->GetStaticMethodID(dex_cls, "setTrusted", "(Ljava/lang/Object;)V");
            if (set_trusted_method == nullptr) env->ExceptionClear();
            env->DeleteLocalRef(dex_cls);
        } else {
            env->ExceptionClear();
        }
    }

    if (suspend_ctor == 0 || suspend_dtor == 0 ||
        (set_trusted == 0 && set_trusted_method == nullptr)) {
        LOGE("art_hook_init: required ART entry missing: ctor=%#lx dtor=%#lx trusted=%#lx",
             suspend_ctor, suspend_dtor, set_trusted);
        return false;
    }

    result->suspend_ctor = reinterpret_cast<void *>(suspend_ctor);
    result->suspend_dtor = reinterpret_cast<void *>(suspend_dtor);
    result->set_not_intrinsic = reinterpret_cast<void *>(set_not_intrinsic);
    if (set_trusted != 0) {
        result->set_dex_file_trusted = reinterpret_cast<void *>(set_trusted);
    } else {
        result->set_dex_file_trusted_method = set_trusted_method;
    }
    result->runtime_instance_addr = reinterpret_cast<void *>(runtime_instance);
    result->set_runtime_debug_state = reinterpret_cast<void *>(set_runtime_debug_state);
    result->set_java_debuggable = reinterpret_cast<void *>(set_java_debuggable);
    return true;
}
} // namespace tcqt::zygisk::art_detail
