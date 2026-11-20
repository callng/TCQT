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
inline constexpr uint32_t ACC_NATIVE = 0x0100;
inline constexpr uint32_t ACC_COMPILE_DONT_BOTHER = 0x02000000;
// AOSP libdexfile/dex/modifiers.h, Android 13..17. These bits have overlapping
// meanings: the Nterp entry bit is CriticalNative on native methods.
inline constexpr uint32_t ACC_PRECOMPILED = 0x00800000;
inline constexpr uint32_t ACC_NTERP_ENTRY_FAST_PATH = 0x00100000;
inline constexpr uint32_t ACC_NTERP_INVOKE_FAST_PATH = 0x00200000;
inline constexpr uint32_t ACC_MEMORY_SHARED_METHOD = 0x40000000;
inline constexpr uint32_t ACC_INTRINSIC = 0x80000000;

// Call only after SetNotIntrinsic: intrinsic ordinals overlap compiler flags.
inline uint32_t hooked_method_flags(uint32_t flags) {
    flags = (flags | ACC_COMPILE_DONT_BOTHER) & ~ACC_PRECOMPILED & ~ACC_MEMORY_SHARED_METHOD;
    if ((flags & ACC_NATIVE) == 0)
        flags &= ~(ACC_NTERP_ENTRY_FAST_PATH | ACC_NTERP_INVOKE_FAST_PATH);
    return flags;
}

struct MethodLayout {
    size_t method_size = 0;
    size_t entry_point_offset = 0;
    size_t access_flags_offset = 0;
    jfieldID art_method_field = nullptr;
    jfieldID access_flags_field = nullptr;
};
struct RuntimeSymbols {
    void *suspend_ctor = nullptr;
    void *suspend_dtor = nullptr;
    void *set_not_intrinsic = nullptr;
    void *quick_to_interpreter = nullptr;
};
// Layout and symbols are immutable after initialized is published with release semantics.
struct RuntimeState {
    std::atomic<bool> initialized{false};
    MethodLayout layout;
    RuntimeSymbols symbols;
};
RuntimeState &state();
bool probe_layout(JNIEnv *env, MethodLayout *result);
bool resolve_symbols(JNIEnv *env, const ArtLibrary &art, const MethodLayout &layout,
                     RuntimeSymbols *result);
} // namespace tcqt::zygisk::art_detail
