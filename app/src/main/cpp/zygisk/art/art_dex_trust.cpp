#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_runtime.h"
#include <cstring>
#include <vector>
#include "zygisk/logging/log.h"
namespace tcqt::zygisk {
using namespace art_detail;
namespace {
std::atomic<size_t> g_debug_state_offset{static_cast<size_t>(-1)};
void set_trust_debug_state(bool enabled) {
    if (state().symbols.runtime_instance_addr == nullptr) return;
    void **instance = *reinterpret_cast<void ***>(state().symbols.runtime_instance_addr);
    if (instance == nullptr) return;

    size_t offset = g_debug_state_offset.load();
    if (offset == static_cast<size_t>(-1)) {
        // Probe debug_state_ offset once using a scratch buffer.
        if (state().symbols.set_runtime_debug_state != nullptr) {
            auto f = reinterpret_cast<void (*)(void *, int)>(state().symbols.set_runtime_debug_state);
            uint8_t scratch[4096] = {0};
            f(scratch, 1);
            offset = static_cast<size_t>(-1);
            for (size_t i = 1; i + 4 <= sizeof(scratch); ++i) {
                uint32_t v;
                memcpy(&v, scratch + i, sizeof(v));
                if (v == 1) {
                    offset = i;
                    break;
                }
            }
        } else {
            offset = 0;  // function unavailable
        }
        g_debug_state_offset.store(offset);
    }
    if (offset != static_cast<size_t>(-1) && offset != 0) {
        uint32_t state = enabled ? 2 : 0;
        auto *ptr = reinterpret_cast<uint32_t *>(reinterpret_cast<uint8_t *>(instance) + offset);
        __atomic_store_n(ptr, state, __ATOMIC_RELAXED);
    }
    if (state().symbols.set_java_debuggable != nullptr) {
        auto f = reinterpret_cast<void (*)(void *, bool)>(state().symbols.set_java_debuggable);
        f(instance, enabled);
    }
}

} // namespace
bool art_trust_dex_file(JNIEnv *env, jobject dex_file) {
    if (!art_hook_initialized() || env == nullptr || dex_file == nullptr) return false;
    if (state().symbols.set_dex_file_trusted == nullptr && state().symbols.set_dex_file_trusted_method == nullptr)
        return false;

    jclass dex_cls = env->FindClass("dalvik/system/DexFile");
    if (dex_cls == nullptr) {
        env->ExceptionClear();
        return false;
    }
    jfieldID cookie_fid = env->GetFieldID(dex_cls, "mCookie", "Ljava/lang/Object;");
    if (cookie_fid == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(dex_cls);
        return false;
    }
    jobject cookie = env->GetObjectField(dex_file, cookie_fid);
    if (env->ExceptionCheck() || cookie == nullptr) {
        env->ExceptionClear();
        if (cookie != nullptr) env->DeleteLocalRef(cookie);
        env->DeleteLocalRef(dex_cls);
        return false;
    }

    set_trust_debug_state(true);
    if (state().symbols.set_dex_file_trusted != nullptr) {
        auto f = reinterpret_cast<void (*)(JNIEnv *, jclass, jobject)>(state().symbols.set_dex_file_trusted);
        f(env, dex_cls, cookie);
    } else {
        env->CallStaticVoidMethod(dex_cls, state().symbols.set_dex_file_trusted_method, cookie);
    }
    set_trust_debug_state(false);

    bool ok = !env->ExceptionCheck();
    if (!ok) {
        LOGE("art_trust_dex_file: DexFile.setTrusted failed");
        env->ExceptionClear();
    }
    env->DeleteLocalRef(cookie);
    env->DeleteLocalRef(dex_cls);
    return ok;
}


} // namespace tcqt::zygisk
