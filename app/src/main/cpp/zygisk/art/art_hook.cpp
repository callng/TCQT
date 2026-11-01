#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_runtime.h"
#include "zygisk/art/trampoline.h"
#include "zygisk/logging/log.h"
#include <mutex>

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
    ArtLibrary art;
    if (!find_art_library(&art)) {
        LOGE("art_hook_init: libart.so not found");
        return false;
    }
    MethodLayout layout;
    RuntimeSymbols symbols;
    if (!probe_layout(env, &layout) || !resolve_symbols(env, art, &symbols)) return false;
    if (!TrampolinePool::instance()->is_ready()) {
        LOGE("art_hook_init: TrampolinePool unavailable");
        return false;
    }
    state().layout = layout;
    state().symbols = symbols;
    state().initialized.store(true, std::memory_order_release);
    LOGI("art_hook_init: method_size=%zu entry_offset=%zu flags_offset=%zu",
         layout.method_size, layout.entry_point_offset, layout.access_flags_offset);
    return true;
}
bool art_hook_initialized() { return state().initialized.load(std::memory_order_acquire); }
uintptr_t art_get_method(JNIEnv *env, jobject executable) {
    if (env == nullptr || executable == nullptr) return 0;
    if (state().layout.art_method_field != nullptr) {
        jlong val = env->GetLongField(executable, state().layout.art_method_field);
        if (!env->ExceptionCheck() && val != 0) return static_cast<uintptr_t>(val);
        env->ExceptionClear();
    }
    // Fallback: FromReflectedMethod.
    return reinterpret_cast<uintptr_t>(env->FromReflectedMethod(executable));
}


} // namespace tcqt::zygisk
