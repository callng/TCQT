#include "art_hook.h"

#include <cerrno>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "elf_util.h"
#include "log.h"
#include "trampoline.h"

namespace tcqt {

// ── acc_flags constants (matching ART source) ────────────────────────────────
constexpr uint32_t ACC_PUBLIC = 0x0001;
constexpr uint32_t ACC_PRIVATE = 0x0002;
constexpr uint32_t ACC_PROTECTED = 0x0004;
constexpr uint32_t ACC_STATIC = 0x0008;
constexpr uint32_t ACC_COMPILE_DONT_BOTHER = 0x02000000;
constexpr uint32_t ACC_FAST_INTERPRETER = 0x40000000;  // kAccFastInterpreterToInterpreterInvoke
constexpr uint32_t ACC_INTRINSIC = 0x80000000;
constexpr uint32_t ACC_PROXY_METHOD = 0x00400000;  // kAccProxyMethod

namespace {

// ── Global state ─────────────────────────────────────────────────────────────
std::atomic<bool> g_initialized{false};

std::atomic<size_t> g_method_size{0};
std::atomic<size_t> g_entry_point_offset{0};
std::atomic<size_t> g_access_flags_offset{0};
std::atomic<uint32_t> g_acc_precompiled{0};      // kAccPreCompiled per API level
std::atomic<uint32_t> g_acc_fast_interp{0};      // kAccFastInterpreterToInterpreterInvoke per API

jfieldID g_art_method_field = nullptr;
jfieldID g_access_flags_field = nullptr;

void *g_suspend_ctor = nullptr;
void *g_suspend_dtor = nullptr;
void *g_set_not_intrinsic = nullptr;
void *g_set_dex_file_trusted = nullptr;        // native symbol
jmethodID g_set_dex_file_trusted_method = nullptr;  // JNI fallback

void *g_runtime_instance_addr = nullptr;       // address of Runtime::instance_ variable
void *g_set_runtime_debug_state = nullptr;
void *g_set_java_debuggable = nullptr;
std::atomic<size_t> g_debug_state_offset{static_cast<size_t>(-1)};

struct HookRecord {
    uintptr_t backup_art;
    uint32_t original_access_flags;
};
std::mutex g_hook_records_mutex;
std::unordered_map<uintptr_t, HookRecord> g_hook_records;

// ── API level ────────────────────────────────────────────────────────────────
int get_api_level() {
    static int api = -1;
    if (api >= 0) return api;
    char buf[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.build.version.sdk", buf);
    api = atoi(buf);
    if (api <= 0) api = 35;  // assume modern API when detection fails
    return api;
}

// ── ScopedSuspendAll RAII (symbols resolved from libart.so) ─────────────────
class ScopedSuspend {
public:
    explicit ScopedSuspend(const char *reason) {
        if (g_suspend_ctor != nullptr) {
            auto ctor = reinterpret_cast<void (*)(void *, const char *, bool)>(g_suspend_ctor);
            ctor(storage_, reason, false);
        }
    }
    ~ScopedSuspend() {
        if (g_suspend_dtor != nullptr) {
            auto dtor = reinterpret_cast<void (*)(void *)>(g_suspend_dtor);
            dtor(storage_);
        }
    }
    bool active() const { return g_suspend_ctor != nullptr && g_suspend_dtor != nullptr; }

private:
    alignas(16) uint8_t storage_[256];
};

// ── Memory mapping helpers ────────────────────────────────────────────────────
inline uintptr_t strip_pac(uintptr_t addr) {
#ifdef __aarch64__
    register uintptr_t x16 __asm__("x16") = addr;
    __asm__ __volatile__("hint #32" : "+r"(x16));
    return x16;
#else
    return addr;
#endif
}

// Protection bits of the mapping containing `addr`, or -1 when unmapped.
int get_prot_for_addr(uintptr_t addr) {
    addr = strip_pac(addr);
    int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char buf[8192];
    ssize_t n;
    std::string data;
    while ((n = read(fd, buf, sizeof(buf))) > 0) data.append(buf, static_cast<size_t>(n));
    close(fd);
    if (n < 0) return -1;

    size_t pos = 0;
    while (pos < data.size()) {
        size_t eol = data.find('\n', pos);
        if (eol == std::string::npos) eol = data.size();
        const std::string line = data.substr(pos, eol - pos);
        pos = eol + 1;

        size_t dash = line.find('-');
        if (dash == std::string::npos) continue;
        uintptr_t start = strtoul(line.substr(0, dash).c_str(), nullptr, 16);
        size_t sp1 = line.find(' ', dash);
        if (sp1 == std::string::npos) continue;
        size_t sp2 = line.find(' ', sp1 + 1);
        if (sp2 == std::string::npos) continue;
        uintptr_t end = strtoul(line.substr(dash + 1, sp1 - dash - 1).c_str(), nullptr, 16);
        const std::string perms = line.substr(sp1 + 1, sp2 - sp1 - 1);
        if (addr >= start && addr < end) {
            int prot = 0;
            if (perms.find('r') != std::string::npos) prot |= PROT_READ;
            if (perms.find('w') != std::string::npos) prot |= PROT_WRITE;
            if (perms.find('x') != std::string::npos) prot |= PROT_EXEC;
            return prot;
        }
    }
    return -1;
}

// Whether `addr` points into executable memory (a plausible code pointer).
// NOTE: get_prot_for_addr() returns -1 for addresses that are NOT present in
// /proc/self/maps at all (unmapped, or a small non-pointer value like a dex
// method index). Such an address must NEVER be treated as executable:
// (-1 & PROT_EXEC) is nonzero, so this check must reject -1 explicitly.
bool is_executable_address(uintptr_t addr) {
    addr = strip_pac(addr);
    if (addr == 0) return false;
    int prot = get_prot_for_addr(addr);
    return prot >= 0 && (prot & PROT_EXEC) != 0;
}

// ── WritableArtMethod: page-by-page mprotect with restoration ────────────────
constexpr size_t MAX_PAGES = 257;

struct PageState {
    uintptr_t address = 0;
    size_t length = 0;
    int original_protection = 0;
    bool changed = false;
};

class WritableArtMethod {
public:
    bool acquire(uintptr_t address, size_t length) {
        if (address == 0 || length == 0) return false;
        long page_size_long = sysconf(_SC_PAGESIZE);
        if (page_size_long <= 0) return false;
        size_t page_size = static_cast<size_t>(page_size_long);

        uintptr_t first_page = address - address % page_size;
        uintptr_t last_address = address + length - 1;
        uintptr_t last_page = last_address - last_address % page_size;
        size_t page_count = (last_page - first_page) / page_size + 1;
        if (page_count > MAX_PAGES) {
            LOGE("WritableArtMethod: page span too large: %zu", page_count);
            return false;
        }
        count_ = 0;
        for (uintptr_t page = first_page;; page += page_size) {
            int prot = get_prot_for_addr(page);
            if (prot < 0) {
                LOGE("WritableArtMethod: cannot read protection for %#lx", page);
                restore();
                return false;
            }
            pages_[count_] = PageState{page, page_size, prot, false};
            count_++;
            if ((prot & PROT_WRITE) == 0) {
                if (mprotect(reinterpret_cast<void *>(page), page_size, prot | PROT_WRITE) != 0) {
                    LOGE("WritableArtMethod: mprotect failed at %#lx (errno=%d)", page, errno);
                    restore();
                    return false;
                }
                pages_[count_ - 1].changed = true;
            }
            if (page == last_page) break;
        }
        return true;
    }

    void restore() {
        for (size_t i = count_; i > 0; --i) {
            const PageState &p = pages_[i - 1];
            if (p.changed) {
                mprotect(reinterpret_cast<void *>(p.address), p.length, p.original_protection);
                pages_[i - 1].changed = false;
            }
        }
    }

    ~WritableArtMethod() { restore(); }

private:
    PageState pages_[MAX_PAGES];
    size_t count_ = 0;
};

// ── JNI reflection helpers ───────────────────────────────────────────────────
bool init_reflection_fields(JNIEnv *env) {
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
    g_art_method_field = art_fid;
    g_access_flags_field = flags_fid;
    return true;
}

// Any ambiguity fails the probe and the caller refuses to hook: writing at a
// guessed offset would corrupt neighbouring ArtMethods and turn into
// "GC tried to mark invalid reference" crashes.
bool probe_art_method_layout(JNIEnv *env, size_t *method_size, size_t *flags_offset,
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
        if (g_art_method_field != nullptr)
            arts[i] = static_cast<uintptr_t>(env->GetLongField(c, g_art_method_field));
        if (g_access_flags_field != nullptr)
            flags_arr[i] = static_cast<uint32_t>(env->GetIntField(c, g_access_flags_field));
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

void call_set_not_intrinsic(uintptr_t art_method) {
    if (g_set_not_intrinsic != nullptr) {
        auto f = reinterpret_cast<void (*)(void *)>(g_set_not_intrinsic);
        f(reinterpret_cast<void *>(art_method));
    } else {
        // Fallback: manually clear ACC_INTRINSIC.
        uint32_t *af = reinterpret_cast<uint32_t *>(art_method + g_access_flags_offset.load());
        __atomic_store_n(af, __atomic_load_n(af, __ATOMIC_RELAXED) & ~ACC_INTRINSIC,
                         __ATOMIC_RELAXED);
    }
}

// Toggle Runtime debug state around DexFile_setTrusted for OEM compatibility.
void set_trust_debug_state(bool enabled) {
    if (g_runtime_instance_addr == nullptr) return;
    void **instance = *reinterpret_cast<void ***>(g_runtime_instance_addr);
    if (instance == nullptr) return;

    size_t offset = g_debug_state_offset.load();
    if (offset == static_cast<size_t>(-1)) {
        // Probe debug_state_ offset once using a scratch buffer.
        if (g_set_runtime_debug_state != nullptr) {
            auto f = reinterpret_cast<void (*)(void *, int)>(g_set_runtime_debug_state);
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
    if (g_set_java_debuggable != nullptr) {
        auto f = reinterpret_cast<void (*)(void *, bool)>(g_set_java_debuggable);
        f(instance, enabled);
    }
}

}  // namespace

// ── Public API ───────────────────────────────────────────────────────────────

bool art_hook_init(JNIEnv *env) {
    if (g_initialized.load()) return true;
    if (env == nullptr) return false;

    // 1. Locate libart.so.
    ArtLibrary art;
    if (!find_art_library(&art)) {
        LOGE("art_hook_init: libart.so not found");
        return false;
    }

    // 2. Probe reflection field IDs.
    if (!init_reflection_fields(env)) {
        LOGE("art_hook_init: failed to probe reflection fields");
        return false;
    }

    // 3. Probe ArtMethod layout.
    size_t method_size = 0, flags_offset = 0;
    uintptr_t probed_ep = 0;
    if (!probe_art_method_layout(env, &method_size, &flags_offset, &probed_ep)) {
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

    // 7. Prepare trampoline pool.
    if (!TrampolinePool::instance()->is_ready()) {
        LOGE("art_hook_init: TrampolinePool unavailable");
        return false;
    }

    // Commit global state.
    g_method_size.store(method_size);
    g_entry_point_offset.store(entry_point_offset);
    g_access_flags_offset.store(flags_offset);
    g_acc_precompiled.store(acc_precompiled);
    g_acc_fast_interp.store(acc_fast_interp);
    g_suspend_ctor = reinterpret_cast<void *>(suspend_ctor);
    g_suspend_dtor = reinterpret_cast<void *>(suspend_dtor);
    g_set_not_intrinsic = reinterpret_cast<void *>(set_not_intrinsic);
    if (set_trusted != 0) {
        g_set_dex_file_trusted = reinterpret_cast<void *>(set_trusted);
    } else {
        g_set_dex_file_trusted_method = set_trusted_method;
    }
    g_runtime_instance_addr = reinterpret_cast<void *>(runtime_instance);
    g_set_runtime_debug_state = reinterpret_cast<void *>(set_runtime_debug_state);
    g_set_java_debuggable = reinterpret_cast<void *>(set_java_debuggable);
    {
        std::lock_guard<std::mutex> lock(g_hook_records_mutex);
        g_hook_records.clear();
    }
    g_initialized.store(true);

    LOGI("art_hook_init: method_size=%zu entry_offset=%zu flags_offset=%zu api=%d",
         method_size, entry_point_offset, flags_offset, api);
    return true;
}

bool art_hook_initialized() { return g_initialized.load(); }

uintptr_t art_get_method(JNIEnv *env, jobject executable) {
    if (env == nullptr || executable == nullptr) return 0;
    if (g_art_method_field != nullptr) {
        jlong val = env->GetLongField(executable, g_art_method_field);
        if (!env->ExceptionCheck() && val != 0) return static_cast<uintptr_t>(val);
        env->ExceptionClear();
    }
    // Fallback: FromReflectedMethod.
    return reinterpret_cast<uintptr_t>(env->FromReflectedMethod(executable));
}

int art_hook_method(JNIEnv *env, uintptr_t target, uintptr_t backup, uintptr_t bridge) {
    (void)env;
    if (!g_initialized.load() || target == 0 || backup == 0 || bridge == 0) return -1;

    size_t method_size = g_method_size.load();
    size_t af_off = g_access_flags_offset.load();
    size_t ep_off = g_entry_point_offset.load();
    if (af_off + 4 > method_size || ep_off + sizeof(void *) > method_size) {
        LOGE("art_hook_method: invalid layout flags=%zu entry=%zu size=%zu", af_off,
             ep_off, method_size);
        return -8;
    }

    // Fail closed when any of the three ArtMethods does not look like a real
    // method: an unmapped address, a non-8-byte-aligned pointer, or an
    // implausible declaring-class root. Writing at a bogus offset would scribble
    // over neighbouring ArtMethods and corrupt the GC roots (the "GC tried to
    // mark invalid reference" crash class).
    auto plausible_art_method = [&](uintptr_t art, const char *name) -> bool {
        if (art == 0 || (art & (sizeof(void *) - 1)) != 0) {
            LOGE("art_hook_method: %s=%#lx is not a plausible ArtMethod", name, art);
            return false;
        }
        if (get_prot_for_addr(art) < 0 ||
            get_prot_for_addr(art + method_size - 1) < 0) {
            LOGE("art_hook_method: %s=%#lx is not fully mapped (%zu bytes)", name, art,
                 method_size);
            return false;
        }
        // The declaring-class root at offset 0 is either a 4-byte compressed
        // reference (flags sit at offset 4 — compact ArtMethod layout used by
        // some OEM builds) or an 8-byte pointer (flags at offset 8, AOSP
        // layout). Validate it according to the probed layout instead of
        // assuming 8 bytes.
        if (af_off == 4) {
            // Compressed references are heap offsets and can be large on
            // devices with big heaps; only a null root is clearly invalid.
            // The real gates are the mapped-address check above and the
            // entry-point / write-back verification below.
            uint32_t cls = 0;
            memcpy(&cls, reinterpret_cast<const void *>(art), sizeof(cls));
            if (cls == 0) {
                LOGE("art_hook_method: %s=%#lx has null declaring class root",
                     name, art);
                return false;
            }
        } else {
            uintptr_t cls = 0;
            memcpy(&cls, reinterpret_cast<const void *>(art), sizeof(cls));
            if (cls == 0 || (cls & (sizeof(void *) - 1)) != 0 || get_prot_for_addr(cls) < 0) {
                LOGE("art_hook_method: %s=%#lx has implausible declaring class %#lx",
                     name, art, cls);
                return false;
            }
        }
        return true;
    };
    if (!plausible_art_method(target, "target") || !plausible_art_method(backup, "backup") ||
        !plausible_art_method(bridge, "bridge")) {
        return -11;
    }

    // bridge and backup are the two ArtMethods of the generated DexMaker class
    // and must be adjacent in its methods_ array. If they are not, the memcpy
    // below would spill into a neighbouring slot; refuse instead of corrupting.
    uintptr_t lo = bridge < backup ? bridge : backup;
    uintptr_t hi = bridge < backup ? backup : bridge;
    if (hi - lo != method_size) {
        LOGE("art_hook_method: bridge/backup not adjacent (bridge=%#lx backup=%#lx "
             "delta=%zu, expected size=%zu)", bridge, backup, hi - lo, method_size);
        return -12;
    }

    // Allocate the trampoline first: it has no side effect on the ArtMethods,
    // so a pool exhaustion leaves everything untouched.
    const uint8_t *trampoline = TrampolinePool::instance()->allocate(bridge, ep_off);
    if (trampoline == nullptr) return -7;

    ScopedSuspend suspend("TCQT Hooking");
    if (!suspend.active()) return -2;

    // Reject targets whose entry point does not look like a code pointer.
    // With a wrong layout probe this would otherwise write the trampoline
    // into a neighbouring object and corrupt the heap.
    uintptr_t target_ep = 0, bridge_ep = 0;
    memcpy(&target_ep, reinterpret_cast<const void *>(target + ep_off), sizeof(target_ep));
    memcpy(&bridge_ep, reinterpret_cast<const void *>(bridge + ep_off), sizeof(bridge_ep));
    if (!is_executable_address(target_ep) || !is_executable_address(bridge_ep)) {
        LOGE("art_hook_method: non-executable entry point target=%#lx bridge=%#lx",
             target_ep, bridge_ep);
        return -9;
    }

    {
        std::lock_guard<std::mutex> lock(g_hook_records_mutex);
        if (g_hook_records.count(target) != 0) {
            LOGE("art_hook_method: target=%#lx already hooked", target);
            return -3;
        }
    }

    // Snapshot the pristine backup bytes so a failed hook can restore the
    // backup slot exactly (the memcpy below would otherwise leave it clobbered
    // on the rollback path).
    std::vector<uint8_t> backup_orig(method_size);
    memcpy(backup_orig.data(), reinterpret_cast<const void *>(backup), method_size);

    WritableArtMethod tw, bw, brw;
    if (!tw.acquire(target, method_size)) return -4;
    if (!bw.acquire(backup, method_size)) return -5;
    if (!brw.acquire(bridge, method_size)) return -6;

    auto *target_af = reinterpret_cast<uint32_t *>(target + af_off);
    auto *bridge_af = reinterpret_cast<uint32_t *>(bridge + af_off);
    auto *target_ep_ptr = reinterpret_cast<void **>(target + ep_off);
    auto *backup_ep_ptr = reinterpret_cast<void **>(backup + ep_off);
    auto *backup_af = reinterpret_cast<uint32_t *>(backup + af_off);

    // ── Snapshots for rollback ──────────────────────────────────────────────
    uint32_t original_target_flags = __atomic_load_n(target_af, __ATOMIC_RELAXED);
    uint32_t original_bridge_flags = __atomic_load_n(bridge_af, __ATOMIC_RELAXED);
    void *original_target_ep = __atomic_load_n(target_ep_ptr, __ATOMIC_RELAXED);
    void *original_bridge_ep = reinterpret_cast<void *>(
            strip_pac(reinterpret_cast<uintptr_t>(
                    __atomic_load_n(reinterpret_cast<void **>(bridge + ep_off), __ATOMIC_RELAXED))));

    bool hook_ok = true;
    auto fail = [&](const char *what) {
        LOGE("art_hook_method: verification failed (%s), rolling back", what);
        hook_ok = false;
    };
    auto rollback = [&]() {
        __atomic_store_n(target_af, original_target_flags, __ATOMIC_RELAXED);
        __atomic_store_n(target_ep_ptr, original_target_ep, __ATOMIC_RELAXED);
        __atomic_store_n(bridge_af, original_bridge_flags, __ATOMIC_RELAXED);
        memcpy(reinterpret_cast<void *>(backup), backup_orig.data(), method_size);
    };

    // ── Bridge: add ACC_COMPILE_DONT_BOTHER, clear precompiled ─────────────
    uint32_t precomp = g_acc_precompiled.load();
    uint32_t bridge_flags_new =
            (__atomic_load_n(bridge_af, __ATOMIC_RELAXED) | ACC_COMPILE_DONT_BOTHER) & ~precomp;
    __atomic_store_n(bridge_af, bridge_flags_new, __ATOMIC_RELAXED);
    if (__atomic_load_n(bridge_af, __ATOMIC_RELAXED) != bridge_flags_new) fail("bridge flags");

    // ── Target: clear intrinsic (may change flags), add ACC_COMPILE_DONT_BOTHER ──
    call_set_not_intrinsic(target);
    uint32_t after_set_not_intrinsic = __atomic_load_n(target_af, __ATOMIC_RELAXED);
    uint32_t fast_interp = g_acc_fast_interp.load();
    uint32_t target_flags_new = (after_set_not_intrinsic | ACC_COMPILE_DONT_BOTHER) & ~precomp;
    __atomic_store_n(target_af, target_flags_new, __ATOMIC_RELAXED);
    if (__atomic_load_n(target_af, __ATOMIC_RELAXED) != target_flags_new) fail("target flags");

    // ── Snapshot target into backup ─────────────────────────────────────────
    memcpy(reinterpret_cast<void *>(backup), reinterpret_cast<const void *>(target),
           method_size);

    // The copy must have landed in the backup slot: the backup's declaring
    // class root now equals the target's. A wrong method_size / offset would
    // leave it untouched (or overwrite a neighbour instead). Compare only the
    // class root — at hook time the flags word already matches (the memcpy
    // happens after the target's flags were tweaked), but keep the comparison
    // root-only to be layout-correct.
    bool backup_copy_ok = false;
    if (af_off == 4) {
        uint32_t t = 0, b = 0;
        memcpy(&t, reinterpret_cast<const void *>(target), sizeof(t));
        memcpy(&b, reinterpret_cast<const void *>(backup), sizeof(b));
        backup_copy_ok = (t != 0 && t == b);
    } else {
        uintptr_t t = 0, b = 0;
        memcpy(&t, reinterpret_cast<const void *>(target), sizeof(t));
        memcpy(&b, reinterpret_cast<const void *>(backup), sizeof(b));
        backup_copy_ok = (t != 0 && t == b);
    }
    if (!backup_copy_ok) fail("backup declaring-class copy");

    // Clear the target's fast-interpreter bit so it always goes through the
    // trampoline.
    uint32_t target_flags_final = __atomic_load_n(target_af, __ATOMIC_RELAXED) & ~fast_interp;
    __atomic_store_n(target_af, target_flags_final, __ATOMIC_RELAXED);

    // Clear fast-interpreter and precompiled flags on backup so Nterp/interpreter performs standard frame setup.
    // ACC_PROXY_METHOD is cleared too: the backup is a plain method of the
    // generated class, and if the target happened to be a proxy method, leaving
    // the bit set would make the GC treat the backup as a proxy and read its
    // data_ as an interface-method root.
    uint32_t backup_flags_new =
            __atomic_load_n(backup_af, __ATOMIC_RELAXED) & ~fast_interp & ~precomp &
            ~ACC_PROXY_METHOD;
    if ((backup_flags_new & ACC_STATIC) == 0) {
        backup_flags_new = (backup_flags_new | ACC_PRIVATE) & ~(ACC_PUBLIC | ACC_PROTECTED);
    }
    __atomic_store_n(backup_af, backup_flags_new, __ATOMIC_RELAXED);
    if (__atomic_load_n(backup_af, __ATOMIC_RELAXED) != backup_flags_new) fail("backup flags");

    // Backup always runs through the interpreter: point it at the bridge's
    // entry point (the bridge is never JIT-compiled, so its entry point is the
    // interpreter bridge). The memcpy above carried over the target's original
    // entry point, which may be JIT-compiled code compiled for the target's
    // ArtMethod identity — executing it as `backup` would run with mismatched
    // method context.
    __atomic_store_n(backup_ep_ptr, original_bridge_ep, __ATOMIC_RELAXED);
    if (__atomic_load_n(backup_ep_ptr, __ATOMIC_RELAXED) != original_bridge_ep)
        fail("backup entry point");

    // ── Redirect the target's entry point to the trampoline ─────────────────
    __atomic_store_n(target_ep_ptr, const_cast<uint8_t *>(trampoline), __ATOMIC_RELAXED);
    if (__atomic_load_n(target_ep_ptr, __ATOMIC_RELAXED) != trampoline) {
        LOGE("art_hook_method: entry point write-back mismatch (bad ep_off=%zu?)", ep_off);
        fail("target entry point");
    }

    if (!hook_ok) {
        rollback();
        return -10;
    }

    {
        std::lock_guard<std::mutex> lock(g_hook_records_mutex);
        g_hook_records[target] = HookRecord{backup, original_target_flags};
    }
    LOGD("art_hook_method: hooked target=%#lx backup=%#lx bridge=%#lx ep=%#lx "
         "size=%zu flags_off=%zu ep_off=%zu",
         target, backup, bridge, reinterpret_cast<uintptr_t>(trampoline),
         method_size, af_off, ep_off);
    return 0;
}

int art_unhook_method(JNIEnv *env, uintptr_t target, uintptr_t backup) {
    (void)env;
    if (!g_initialized.load() || target == 0 || backup == 0) return -1;

    uint32_t original_access_flags;
    {
        std::lock_guard<std::mutex> lock(g_hook_records_mutex);
        auto it = g_hook_records.find(target);
        if (it == g_hook_records.end() || it->second.backup_art != backup) {
            LOGE("art_unhook_method: no matching hook for target=%#lx", target);
            return -2;
        }
        original_access_flags = it->second.original_access_flags;
    }

    ScopedSuspend suspend("TCQT Unhooking");
    if (!suspend.active()) return -3;

    size_t method_size = g_method_size.load();
    size_t af_off = g_access_flags_offset.load();

    WritableArtMethod tw;
    if (!tw.acquire(target, method_size)) return -4;
    memcpy(reinterpret_cast<void *>(target), reinterpret_cast<const void *>(backup),
           method_size);
    __atomic_store_n(reinterpret_cast<uint32_t *>(target + af_off), original_access_flags,
                     __ATOMIC_RELAXED);

    // Verify the restore actually landed in the target slot. Compare only the
    // declaring-class root (4-byte compressed ref when flags sit at offset 4,
    // 8-byte pointer otherwise): the flags word at offset 4..7 legitimately
    // differs (target was restored to its original flags, the backup keeps the
    // hook-time modified ones).
    bool cls_ok = false;
    if (af_off == 4) {
        uint32_t t = 0, b = 0;
        memcpy(&t, reinterpret_cast<const void *>(target), sizeof(t));
        memcpy(&b, reinterpret_cast<const void *>(backup), sizeof(b));
        cls_ok = (t != 0 && t == b);
    } else {
        uintptr_t t = 0, b = 0;
        memcpy(&t, reinterpret_cast<const void *>(target), sizeof(t));
        memcpy(&b, reinterpret_cast<const void *>(backup), sizeof(b));
        cls_ok = (t != 0 && t == b);
    }
    if (!cls_ok) {
        LOGE("art_unhook_method: restore verification failed for target=%#lx", target);
        return -5;
    }

    {
        std::lock_guard<std::mutex> lock(g_hook_records_mutex);
        g_hook_records.erase(target);
    }
    LOGD("art_unhook_method: unhooked target=%#lx", target);
    return 0;
}

bool art_trust_dex_file(JNIEnv *env, jobject dex_file) {
    if (!g_initialized.load() || env == nullptr || dex_file == nullptr) return false;
    if (g_set_dex_file_trusted == nullptr && g_set_dex_file_trusted_method == nullptr)
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
    if (g_set_dex_file_trusted != nullptr) {
        auto f = reinterpret_cast<void (*)(JNIEnv *, jclass, jobject)>(g_set_dex_file_trusted);
        f(env, dex_cls, cookie);
    } else {
        env->CallStaticVoidMethod(dex_cls, g_set_dex_file_trusted_method, cookie);
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

jobject art_invoke_backup(JNIEnv *env, jobject backup_method, jobject this_object, jobjectArray args) {
    if (backup_method == nullptr) return nullptr;

    jmethodID mid = env->FromReflectedMethod(backup_method);
    if (mid == nullptr) return nullptr;

    uintptr_t backup_art = reinterpret_cast<uintptr_t>(mid);
    size_t af_off = g_access_flags_offset.load();
    uint32_t flags = __atomic_load_n(reinterpret_cast<const uint32_t *>(backup_art + af_off), __ATOMIC_RELAXED);
    bool is_static = (flags & ACC_STATIC) != 0;

    jclass method_cls = env->GetObjectClass(backup_method);
    jmethodID get_ret_mid = env->GetMethodID(method_cls, "getReturnType", "()Ljava/lang/Class;");
    auto ret_cls = static_cast<jclass>(env->CallObjectMethod(backup_method, get_ret_mid));
    env->DeleteLocalRef(method_cls);

    jclass class_cls = env->FindClass("java/lang/Class");
    jmethodID is_prim_mid = env->GetMethodID(class_cls, "isPrimitive", "()Z");
    bool is_prim = ret_cls != nullptr && env->CallBooleanMethod(ret_cls, is_prim_mid);

    char type_code = 'L';
    if (is_prim) {
        jmethodID get_name_mid = env->GetMethodID(class_cls, "getName", "()Ljava/lang/String;");
        auto name_str = static_cast<jstring>(env->CallObjectMethod(ret_cls, get_name_mid));
        const char *name = env->GetStringUTFChars(name_str, nullptr);
        if (strcmp(name, "void") == 0) type_code = 'V';
        else if (strcmp(name, "boolean") == 0) type_code = 'Z';
        else if (strcmp(name, "int") == 0) type_code = 'I';
        else if (strcmp(name, "long") == 0) type_code = 'J';
        else if (strcmp(name, "float") == 0) type_code = 'F';
        else if (strcmp(name, "double") == 0) type_code = 'D';
        else if (strcmp(name, "byte") == 0) type_code = 'B';
        else if (strcmp(name, "char") == 0) type_code = 'C';
        else if (strcmp(name, "short") == 0) type_code = 'S';
        env->ReleaseStringUTFChars(name_str, name);
        env->DeleteLocalRef(name_str);
    }
    if (ret_cls != nullptr) env->DeleteLocalRef(ret_cls);
    env->DeleteLocalRef(class_cls);

    jsize argc = (args != nullptr) ? env->GetArrayLength(args) : 0;
    std::vector<jvalue> jargs(argc);
    // 记录哪些槽位真正持有 local ref（jvalue 是 union，primitive 参数
    // 填 .z/.i/.j 后会污染 .l 字段，清理时必须按类型判断而非判空）。
    std::vector<bool> jarg_refs(argc, false);

    // primitive 参数必须从 boxed 对象 unbox 后填入 jvalue 对应字段；全部按
    // 对象引用（.l）传递会导致 JNI 签名不匹配，ART 检测到非法调用后会用
    // SIGSEGV（SI_TKILL）自杀，进程直接闪退（如 LoadedApk.<init> 的 boolean
    // 参数在 invokeBackup 时反复触发）。
    jclass bm_cls = env->GetObjectClass(backup_method);
    jmethodID get_params_mid = env->GetMethodID(
            bm_cls, "getParameterTypes", "()[Ljava/lang/Class;");
    env->DeleteLocalRef(bm_cls);
    jclass p_cls = env->FindClass("java/lang/Class");
    jmethodID p_is_prim_mid = env->GetMethodID(p_cls, "isPrimitive", "()Z");
    jmethodID p_get_name_mid = env->GetMethodID(p_cls, "getName", "()Ljava/lang/String;");
    env->DeleteLocalRef(p_cls);
    auto param_array = static_cast<jobjectArray>(
            env->CallObjectMethod(backup_method, get_params_mid));
    const jsize param_count =
            (param_array != nullptr) ? env->GetArrayLength(param_array) : 0;

    for (jsize i = 0; i < argc; ++i) {
        jobject arg = env->GetObjectArrayElement(args, i);
        if (i >= param_count) {
            jargs[i].l = arg;
            jarg_refs[i] = (arg != nullptr);
            continue;
        }
        auto pcls = static_cast<jclass>(env->GetObjectArrayElement(param_array, i));
        if (pcls == nullptr || !env->CallBooleanMethod(pcls, p_is_prim_mid)) {
            // 引用参数（或解析失败时兜底按引用传）
            jargs[i].l = arg;
            jarg_refs[i] = (arg != nullptr);
            if (pcls != nullptr) env->DeleteLocalRef(pcls);
            continue;
        }
        auto pname_str = static_cast<jstring>(env->CallObjectMethod(pcls, p_get_name_mid));
        const char *pname = env->GetStringUTFChars(pname_str, nullptr);
        env->DeleteLocalRef(pcls);

        // unbox：null 一律按 0/默认值处理
        jclass box_cls = (arg != nullptr) ? env->GetObjectClass(arg) : nullptr;
        if (strcmp(pname, "boolean") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "booleanValue", "()Z") : nullptr;
            jargs[i].z = (arg != nullptr && m != nullptr) ? env->CallBooleanMethod(arg, m) : JNI_FALSE;
        } else if (strcmp(pname, "byte") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "byteValue", "()B") : nullptr;
            jargs[i].b = (arg != nullptr && m != nullptr) ? env->CallByteMethod(arg, m) : 0;
        } else if (strcmp(pname, "char") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "charValue", "()C") : nullptr;
            jargs[i].c = (arg != nullptr && m != nullptr) ? env->CallCharMethod(arg, m) : 0;
        } else if (strcmp(pname, "short") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "shortValue", "()S") : nullptr;
            jargs[i].s = (arg != nullptr && m != nullptr) ? env->CallShortMethod(arg, m) : 0;
        } else if (strcmp(pname, "int") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "intValue", "()I") : nullptr;
            jargs[i].i = (arg != nullptr && m != nullptr) ? env->CallIntMethod(arg, m) : 0;
        } else if (strcmp(pname, "long") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "longValue", "()J") : nullptr;
            jargs[i].j = (arg != nullptr && m != nullptr) ? env->CallLongMethod(arg, m) : 0;
        } else if (strcmp(pname, "float") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "floatValue", "()F") : nullptr;
            jargs[i].f = (arg != nullptr && m != nullptr) ? env->CallFloatMethod(arg, m) : 0;
        } else if (strcmp(pname, "double") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "doubleValue", "()D") : nullptr;
            jargs[i].d = (arg != nullptr && m != nullptr) ? env->CallDoubleMethod(arg, m) : 0;
        } else {
            jargs[i].l = arg;
            jarg_refs[i] = (arg != nullptr);
        }
        if (box_cls != nullptr) env->DeleteLocalRef(box_cls);
        env->ReleaseStringUTFChars(pname_str, pname);
        env->DeleteLocalRef(pname_str);
    }
    if (param_array != nullptr) env->DeleteLocalRef(param_array);

    jclass target_cls = nullptr;
    if (is_static) {
        if (this_object != nullptr) {
            target_cls = env->GetObjectClass(this_object);
        } else {
            jclass m_cls = env->GetObjectClass(backup_method);
            jmethodID get_decl_mid = env->GetMethodID(m_cls, "getDeclaringClass", "()Ljava/lang/Class;");
            target_cls = static_cast<jclass>(env->CallObjectMethod(backup_method, get_decl_mid));
            env->DeleteLocalRef(m_cls);
        }
    }

    jobject result = nullptr;
    auto box_b = [&](jboolean v) {
        jclass c = env->FindClass("java/lang/Boolean");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(Z)Ljava/lang/Boolean;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_i = [&](jint v) {
        jclass c = env->FindClass("java/lang/Integer");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(I)Ljava/lang/Integer;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_j = [&](jlong v) {
        jclass c = env->FindClass("java/lang/Long");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(J)Ljava/lang/Long;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_f = [&](jfloat v) {
        jclass c = env->FindClass("java/lang/Float");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(F)Ljava/lang/Float;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_d = [&](jdouble v) {
        jclass c = env->FindClass("java/lang/Double");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(D)Ljava/lang/Double;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_byte = [&](jbyte v) {
        jclass c = env->FindClass("java/lang/Byte");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(B)Ljava/lang/Byte;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_char = [&](jchar v) {
        jclass c = env->FindClass("java/lang/Character");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(C)Ljava/lang/Character;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_short = [&](jshort v) {
        jclass c = env->FindClass("java/lang/Short");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(S)Ljava/lang/Short;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };

    if (is_static) {
        switch (type_code) {
            case 'V': env->CallStaticVoidMethodA(target_cls, mid, jargs.data()); break;
            case 'Z': result = box_b(env->CallStaticBooleanMethodA(target_cls, mid, jargs.data())); break;
            case 'I': result = box_i(env->CallStaticIntMethodA(target_cls, mid, jargs.data())); break;
            case 'J': result = box_j(env->CallStaticLongMethodA(target_cls, mid, jargs.data())); break;
            case 'F': result = box_f(env->CallStaticFloatMethodA(target_cls, mid, jargs.data())); break;
            case 'D': result = box_d(env->CallStaticDoubleMethodA(target_cls, mid, jargs.data())); break;
            case 'B': result = box_byte(env->CallStaticByteMethodA(target_cls, mid, jargs.data())); break;
            case 'C': result = box_char(env->CallStaticCharMethodA(target_cls, mid, jargs.data())); break;
            case 'S': result = box_short(env->CallStaticShortMethodA(target_cls, mid, jargs.data())); break;
            default: result = env->CallStaticObjectMethodA(target_cls, mid, jargs.data()); break;
        }
    } else {
        jclass backup_decl_cls = nullptr;
        jclass m_cls = env->GetObjectClass(backup_method);
        jmethodID get_decl_mid = env->GetMethodID(m_cls, "getDeclaringClass", "()Ljava/lang/Class;");
        backup_decl_cls = static_cast<jclass>(env->CallObjectMethod(backup_method, get_decl_mid));
        env->DeleteLocalRef(m_cls);

        switch (type_code) {
            case 'V': env->CallNonvirtualVoidMethodA(this_object, backup_decl_cls, mid, jargs.data()); break;
            case 'Z': result = box_b(env->CallNonvirtualBooleanMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'I': result = box_i(env->CallNonvirtualIntMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'J': result = box_j(env->CallNonvirtualLongMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'F': result = box_f(env->CallNonvirtualFloatMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'D': result = box_d(env->CallNonvirtualDoubleMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'B': result = box_byte(env->CallNonvirtualByteMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'C': result = box_char(env->CallNonvirtualCharMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'S': result = box_short(env->CallNonvirtualShortMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            default: result = env->CallNonvirtualObjectMethodA(this_object, backup_decl_cls, mid, jargs.data()); break;
        }
        if (backup_decl_cls != nullptr) {
            env->DeleteLocalRef(backup_decl_cls);
        }
    }

    for (jsize i = 0; i < argc; ++i) {
        if (jarg_refs[i] && jargs[i].l != nullptr) env->DeleteLocalRef(jargs[i].l);
    }
    if (target_cls != nullptr && target_cls != this_object) {
        env->DeleteLocalRef(target_cls);
    }

    return result;
}

}  // namespace tcqt
