#include "zygisk/art/art_runtime.h"
#include "zygisk/art/art_memory.h"
#include <sys/system_properties.h>
#include <cstdlib>
#include <cstring>
namespace tcqt::zygisk::art_detail {
namespace {
int get_api_level() {
    static int api = -1;
    if (api >= 0) return api;
    char buf[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.build.version.sdk", buf);
    api = atoi(buf);
    if (api <= 0) api = 35;  // assume modern API when detection fails
    return api;
}

bool init_reflection_fields(JNIEnv *env, MethodLayout &layout) {
    jclass exec_cls = env->FindClass("java/lang/reflect/Executable");
    if (exec_cls == nullptr) {
        env->ExceptionClear();
        return false;
    }
    jfieldID art_fid = env->GetFieldID(exec_cls, "artMethod", "J");
    if (art_fid == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(exec_cls);
        return false;
    }
    jfieldID flags_fid = env->GetFieldID(exec_cls, "accessFlags", "I");
    if (flags_fid == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(exec_cls);
        return false;
    }
    env->DeleteLocalRef(exec_cls);
    layout.art_method_field = art_fid;
    layout.access_flags_field = flags_fid;
    return true;
}

// Any ambiguity fails the probe and the caller refuses to hook: writing at a
// guessed offset would corrupt neighbouring ArtMethods and turn into
// "GC tried to mark invalid reference" crashes.
bool probe_art_method_layout(JNIEnv *env, const MethodLayout &layout, size_t *method_size, size_t *flags_offset,
                             uintptr_t *entry_point_out) {
    jclass throwable = env->FindClass("java/lang/Throwable");
    jclass clazz = env->FindClass("java/lang/Class");
    if (throwable == nullptr || clazz == nullptr) {
        env->ExceptionClear();
        return false;
    }
    jmethodID get_ctors = env->GetMethodID(clazz, "getDeclaredConstructors",
                                           "()[Ljava/lang/reflect/Constructor;");
    env->DeleteLocalRef(clazz);
    if (get_ctors == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(throwable);
        return false;
    }
    jobjectArray ctors =
            static_cast<jobjectArray>(env->CallObjectMethod(throwable, get_ctors));
    env->DeleteLocalRef(throwable);
    if (ctors == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    jsize len = env->GetArrayLength(ctors);
    if (len < 3) {
        LOGE("probe_art_method_layout: need >=3 constructors, got %d", len);
        env->DeleteLocalRef(ctors);
        return false;
    }

    constexpr jsize MAX_CTORS = 4;
    const jsize n = len < MAX_CTORS ? len : MAX_CTORS;
    uintptr_t arts[MAX_CTORS] = {0};
    uint32_t flags_arr[MAX_CTORS] = {0};
    for (jsize i = 0; i < n; ++i) {
        jobject c = env->GetObjectArrayElement(ctors, i);
        if (c == nullptr) {
            env->DeleteLocalRef(ctors);
            return false;
        }
        if (layout.art_method_field != nullptr)
            arts[i] = static_cast<uintptr_t>(env->GetLongField(c, layout.art_method_field));
        if (layout.access_flags_field != nullptr)
            flags_arr[i] = static_cast<uint32_t>(env->GetIntField(c, layout.access_flags_field));
        env->DeleteLocalRef(c);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            env->DeleteLocalRef(ctors);
            return false;
        }
        if (arts[i] == 0) {
            LOGE("probe_art_method_layout: ctor[%d] has no ArtMethod", static_cast<int>(i));
            env->DeleteLocalRef(ctors);
            return false;
        }
    }
    env->DeleteLocalRef(ctors);
    env->ExceptionClear();

    // Constructors must come in ascending address order (methods_ array order).
    for (jsize i = 1; i < n; ++i) {
        if (arts[i] <= arts[i - 1]) {
            LOGE("probe_art_method_layout: constructors not in ascending order at %d", i);
            return false;
        }
    }

    // All consecutive deltas must agree; a single mismatch means the ctor list
    // is not a run of adjacent ArtMethods and the size cannot be trusted.
    size_t delta = arts[1] - arts[0];
    if (delta < sizeof(void *) * 4 || delta > 256 || delta % sizeof(void *) != 0) {
        LOGE("probe_art_method_layout: implausible constructor delta %zu", delta);
        return false;
    }
    for (jsize i = 2; i < n; ++i) {
        size_t d = arts[i] - arts[i - 1];
        if (d != delta) {
            LOGE("probe_art_method_layout: constructor deltas disagree (%zu vs %zu) — "
                 "constructors are not adjacent, refusing to probe", d, delta);
            return false;
        }
    }

    auto dump_bytes = [&](const char *why) {
        char hex[256];
        size_t pos = 0;
        const size_t dump_len = delta < 64 ? delta : 64;
        for (size_t i = 0; i < dump_len && pos + 3 < sizeof(hex); ++i) {
            pos += static_cast<size_t>(snprintf(hex + pos, sizeof(hex) - pos, "%02x ",
                                                *reinterpret_cast<const uint8_t *>(arts[0] + i)));
        }
        LOGE("probe_art_method_layout: %s (delta=%zu, flags ctor0=%#x ctor1=%#x ctor2=%#x)",
             why, delta, flags_arr[0], flags_arr[1], flags_arr[2]);
        LOGE("probe_art_method_layout: ctor0 bytes: %s", hex);
    };

    // ── Locate access_flags_: the offset where every ctor's ArtMethod holds
    // its own reflection flags value (with ACC_CONSTRUCTOR). A coincidence
    // cannot repeat at the same offset in three different ArtMethods.
    constexpr uint32_t ACC_CONSTRUCTOR = 0x10000;
    long af_off = -1;
    for (size_t off = 0; off + 4 <= delta && off + 4 <= 64; off += 4) {
        bool all = true;
        for (jsize i = 0; i < n; ++i) {
            uint32_t v = 0;
            memcpy(&v, reinterpret_cast<const void *>(arts[i] + off), sizeof(v));
            if (v != flags_arr[i] || (v & ACC_CONSTRUCTOR) == 0) {
                all = false;
                break;
            }
        }
        if (all) {
            if (af_off >= 0) {
                dump_bytes("multiple consistent access_flags offsets");
                return false;
            }
            af_off = static_cast<long>(off);
        }
    }
    if (af_off < 0) {
        // Fallback: OEM builds where the reflection flags field does not mirror
        // the ArtMethod word. Accept the offset where every ctor shows clean
        // dex-level constructor flags (public/private/protected/final/varargs/
        // synthetic + constructor, nothing else).
        for (size_t off = 0; off + 4 <= delta && off + 4 <= 64; off += 4) {
            bool all = true;
            for (jsize i = 0; i < n; ++i) {
                uint32_t v = 0;
                memcpy(&v, reinterpret_cast<const void *>(arts[i] + off), sizeof(v));
                if ((v & ACC_CONSTRUCTOR) == 0 || (v & ~0x11097u) != 0) {
                    all = false;
                    break;
                }
            }
            if (all) {
                af_off = static_cast<long>(off);
                break;
            }
        }
    }
    if (af_off < 0) {
        dump_bytes("access_flags not found at a consistent offset");
        return false;
    }

    // ── Entry point: the first 8-byte slot after the flags that holds an
    // executable address, consistently across all constructors. ArtMethod's
    // entry point is always its last field, so this directly yields the size.
    // Scan every 8-byte-aligned slot (data_/dex fields are not executable, so
    // the entry point is the first executable slot after the flags).
    long ep_off = -1;
    uintptr_t ep_value = 0;
    size_t scan_start = (static_cast<size_t>(af_off) + 4 + sizeof(void *) - 1) &
                        ~(sizeof(void *) - 1);
    for (size_t off = scan_start;
         off + sizeof(void *) <= delta && off + sizeof(void *) <= 64;
         off += sizeof(void *)) {
        bool all = true;
        uintptr_t v0 = 0;
        for (jsize i = 0; i < n; ++i) {
            uintptr_t v = 0;
            memcpy(&v, reinterpret_cast<const void *>(arts[i] + off), sizeof(v));
            if (!is_executable_address(v)) {
                all = false;
                break;
            }
            v0 = v;
        }
        if (all) {
            ep_off = static_cast<long>(off);
            ep_value = v0;
            break;
        }
    }
    if (ep_off < 0 || static_cast<size_t>(af_off) + 4 > static_cast<size_t>(ep_off)) {
        dump_bytes("entry point not found after access_flags");
        return false;
    }

    size_t size = static_cast<size_t>(ep_off) + sizeof(void *);
    if (size < sizeof(void *) * 4 || size > sizeof(void *) * 8 ||
        size % sizeof(void *) != 0 || delta % size != 0) {
        dump_bytes("implausible method size derived from entry point");
        return false;
    }

    *method_size = size;
    *flags_offset = static_cast<size_t>(af_off);
    memcpy(entry_point_out, &ep_value, sizeof(*entry_point_out));
    return true;
}

} // namespace
bool probe_layout(JNIEnv *env, MethodLayout *result) {
    MethodLayout layout;
    // 2. Probe reflection field IDs.
    if (!init_reflection_fields(env, layout)) {
        LOGE("art_hook_init: failed to probe reflection fields");
        return false;
    }

    // 3. Probe ArtMethod layout.
    size_t method_size = 0, flags_offset = 0;
    uintptr_t probed_ep = 0;
    if (!probe_art_method_layout(env, layout, &method_size, &flags_offset, &probed_ep)) {
        LOGE("art_hook_init: failed to probe ArtMethod layout");
        return false;
    }

    // 4. Entry point offset is at the end of ArtMethod.
    size_t entry_point_offset = method_size - sizeof(void *);
    if (flags_offset + 4 > entry_point_offset) {
        LOGE("art_hook_init: invalid layout flags=%zu entry=%zu size=%zu", flags_offset,
             entry_point_offset, method_size);
        return false;
    }

    // Sanity check: the entry point of the probed ArtMethod must point into
    // executable memory. A wrong method_size would place the offset inside a
    // neighbouring object (e.g. a heap pointer), and later entry-point writes
    // would then corrupt that object instead of the ArtMethod.
    if (!is_executable_address(probed_ep)) {
        LOGE("art_hook_init: probed entry point %#lx at offset %zu is not executable",
             probed_ep, entry_point_offset);
        return false;
    }

    // 6. Access flag masks by API level.
    int api = get_api_level();
    uint32_t acc_precompiled;
    if (api < 30) {
        acc_precompiled = 0;
    } else if (api >= 31) {
        acc_precompiled = 0x00800000;
    } else {
        acc_precompiled = 0x00200000;
    }
    uint32_t acc_fast_interp = api < 29 ? 0 : ACC_FAST_INTERPRETER;

    layout.method_size = method_size;
    layout.entry_point_offset = entry_point_offset;
    layout.access_flags_offset = flags_offset;
    layout.acc_precompiled = acc_precompiled;
    layout.acc_fast_interp = acc_fast_interp;
    *result = layout;
    return true;
}
} // namespace tcqt::zygisk::art_detail
