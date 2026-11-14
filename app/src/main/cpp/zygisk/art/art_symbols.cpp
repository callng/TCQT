#include "zygisk/art/art_runtime.h"
#include "zygisk/art/art_memory.h"
#include "zygisk/logging/log.h"
#include <cstring>

namespace tcqt::zygisk::art_detail {
namespace {
// ClassLinker::LinkCode assigns the quick-to-interpreter bridge to abstract
// methods in Android 13..17. The bridge can live in boot.oat and its assembly
// symbol is not necessarily present in a stripped libart.so. Cross-check two
// unrelated boot methods using the already validated reflection layout.
uintptr_t probe_interpreter_entry(JNIEnv *env, const MethodLayout &layout) {
    struct Probe { const char *clazz; const char *name; const char *signature; };
    constexpr Probe probes[] = {
            {"java/lang/Runnable", "run", "()V"},
            {"java/util/Iterator", "hasNext", "()Z"},
    };
    uintptr_t candidate = 0;
    for (const auto &probe : probes) {
        jclass clazz = env->FindClass(probe.clazz);
        if (clazz == nullptr) { env->ExceptionClear(); return 0; }
        jmethodID id = env->GetMethodID(clazz, probe.name, probe.signature);
        jobject reflected = id != nullptr ? env->ToReflectedMethod(clazz, id, JNI_FALSE) : nullptr;
        env->DeleteLocalRef(clazz);
        if (reflected == nullptr) { env->ExceptionClear(); return 0; }
        uintptr_t method = static_cast<uintptr_t>(env->GetLongField(reflected, layout.art_method_field));
        env->DeleteLocalRef(reflected);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return 0; }
        if (method == 0 || method > UINTPTR_MAX - layout.method_size) return 0;
        const int first = get_prot_for_addr(method);
        const int last = get_prot_for_addr(method + layout.method_size - 1);
        if (first < 0 || last < 0 || !(first & PROT_READ) || !(last & PROT_READ)) return 0;
        uint32_t flags = 0;
        uintptr_t entry = 0;
        memcpy(&flags, reinterpret_cast<void *>(method + layout.access_flags_offset), sizeof(flags));
        memcpy(&entry, reinterpret_cast<void *>(method + layout.entry_point_offset), sizeof(entry));
        entry = strip_pac(entry);
        constexpr uint32_t abstract_flag = 0x0400;
        if ((flags & (abstract_flag | ACC_NATIVE | ACC_INTRINSIC)) != abstract_flag ||
            !is_executable_address(entry) || (candidate != 0 && candidate != entry)) return 0;
        candidate = entry;
    }
    return candidate;
}
} // namespace

bool resolve_symbols(JNIEnv *env, const ArtLibrary &art, const MethodLayout &layout,
                     RuntimeSymbols *result) {
    // 5. Resolve libart.so symbols.
    auto resolve = [&art](const char *sym) -> uintptr_t {
        uintptr_t off = resolve_elf_symbol(art.path, sym);
        const uintptr_t address = off != 0 ? art.base + off : 0;
        return is_executable_address(address) ? address : 0;
    };
    uintptr_t suspend_ctor = resolve("_ZN3art16ScopedSuspendAllC2EPKcb");
    if (suspend_ctor == 0) suspend_ctor = resolve("_ZN3art16ScopedSuspendAllC1EPKcb");
    uintptr_t suspend_dtor = resolve("_ZN3art16ScopedSuspendAllD2Ev");
    if (suspend_dtor == 0) suspend_dtor = resolve("_ZN3art16ScopedSuspendAllD1Ev");
    uintptr_t set_not_intrinsic = resolve("_ZN3art9ArtMethod15SetNotIntrinsicEv");
    uintptr_t quick_to_interpreter = resolve("art_quick_to_interpreter_bridge");
    if (quick_to_interpreter == 0) {
        quick_to_interpreter = probe_interpreter_entry(env, layout);
        if (quick_to_interpreter != 0)
            LOGI("art_hook_init: interpreter entry verified from abstract boot methods");
    }

    if (suspend_ctor == 0 || suspend_dtor == 0 ||
        quick_to_interpreter == 0) {
        LOGE("art_hook_init: required ART entry missing: ctor=%#lx dtor=%#lx interpreter=%#lx",
             suspend_ctor, suspend_dtor, quick_to_interpreter);
        return false;
    }

    result->suspend_ctor = reinterpret_cast<void *>(suspend_ctor);
    result->suspend_dtor = reinterpret_cast<void *>(suspend_dtor);
    result->set_not_intrinsic = reinterpret_cast<void *>(set_not_intrinsic);
    result->quick_to_interpreter = reinterpret_cast<void *>(quick_to_interpreter);
    return true;
}
} // namespace tcqt::zygisk::art_detail
