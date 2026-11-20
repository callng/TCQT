#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_runtime.h"
#include "zygisk/art/trampoline.h"
#include "zygisk/logging/log.h"
#include "zygisk/android_support.h"
#include <android/api-level.h>
#include <mutex>
#include <unistd.h>

namespace tcqt::zygisk::art_detail {
RuntimeState &state() {
    static RuntimeState runtime;
    return runtime;
}
} // namespace tcqt::zygisk::art_detail
namespace tcqt::zygisk {
using namespace art_detail;
bool art_hook_init(JNIEnv *env) {
    static std::mutex initialization;
    std::lock_guard<std::mutex> lock(initialization);
    if (art_hook_initialized()) return true;
    if (env == nullptr) return false;
    const int api = android_get_device_api_level();
    if (!supports_android_api(api)) {
        LOGE("art_hook_init: unsupported Android API %d (requires 33..37)", api);
        return false;
    }
    ArtLibrary art;
    if (!find_art_library(&art)) {
        LOGE("art_hook_init: libart.so not found");
        return false;
    }
    MethodLayout layout;
    RuntimeSymbols symbols;
    if (!probe_layout(env, &layout) || !resolve_symbols(env, art, layout, &symbols)) return false;
    if (!TrampolinePool::instance()->is_ready()) {
        LOGE("art_hook_init: TrampolinePool unavailable");
        return false;
    }
    state().layout = layout;
    state().symbols = symbols;
    state().initialized.store(true, std::memory_order_release);
    LOGI("art_hook_init: api=%d art=%s page_size=%ld method_size=%zu entry_offset=%zu flags_offset=%zu",
         api, art.path.c_str(), sysconf(_SC_PAGESIZE), layout.method_size,
         layout.entry_point_offset, layout.access_flags_offset);
    return true;
}
bool art_hook_initialized() { return state().initialized.load(std::memory_order_acquire); }
uintptr_t art_get_method(JNIEnv *env, jobject executable) {
    if (!art_hook_initialized() || env == nullptr || executable == nullptr) return 0;
    if (state().layout.art_method_field != nullptr) {
        jlong val = env->GetLongField(executable, state().layout.art_method_field);
        if (!env->ExceptionCheck() && val != 0) return static_cast<uintptr_t>(val);
        env->ExceptionClear();
    }
    // JNI IDs can be odd indices (JniIdType::kIndices), never dereference them.
    // The reflection field is a prerequisite of initialization; fail closed.
    return 0;
}
} // namespace tcqt::zygisk
