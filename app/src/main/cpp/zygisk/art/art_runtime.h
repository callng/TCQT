#pragma once
#include <jni.h>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include "zygisk/art/elf_util.h"

namespace tcqt::zygisk::art_detail {
inline constexpr uint32_t ACC_PUBLIC = 0x0001;
inline constexpr uint32_t ACC_PRIVATE = 0x0002;
inline constexpr uint32_t ACC_PROTECTED = 0x0004;
inline constexpr uint32_t ACC_STATIC = 0x0008;
inline constexpr uint32_t ACC_COMPILE_DONT_BOTHER = 0x02000000;
inline constexpr uint32_t ACC_FAST_INTERPRETER = 0x40000000;
inline constexpr uint32_t ACC_INTRINSIC = 0x80000000;
inline constexpr uint32_t ACC_PROXY_METHOD = 0x00400000;

struct MethodLayout {
    size_t method_size = 0;
    size_t entry_point_offset = 0;
    size_t access_flags_offset = 0;
    uint32_t acc_precompiled = 0;
    uint32_t acc_fast_interp = 0;
    jfieldID art_method_field = nullptr;
    jfieldID access_flags_field = nullptr;
};
struct RuntimeSymbols {
    void *suspend_ctor = nullptr;
    void *suspend_dtor = nullptr;
    void *set_not_intrinsic = nullptr;
    void *set_dex_file_trusted = nullptr;
    jmethodID set_dex_file_trusted_method = nullptr;
    void *runtime_instance_addr = nullptr;
    void *set_runtime_debug_state = nullptr;
    void *set_java_debuggable = nullptr;
};
// Layout and symbols are immutable after initialized is published with release semantics.
struct RuntimeState {
    std::atomic<bool> initialized{false};
    MethodLayout layout;
    RuntimeSymbols symbols;
};
RuntimeState &state();
bool probe_layout(JNIEnv *env, MethodLayout *result);
bool resolve_symbols(JNIEnv *env, const ArtLibrary &art, RuntimeSymbols *result);
} // namespace tcqt::zygisk::art_detail
