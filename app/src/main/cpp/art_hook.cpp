#include "art_hook.h"

#include <cerrno>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include <atomic>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <unordered_map>

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
// Protection bits of the mapping containing `addr`, or -1 when unmapped.
int get_prot_for_addr(uintptr_t addr) {
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
bool is_executable_address(uintptr_t addr) {
    return addr != 0 && (get_prot_for_addr(addr) & PROT_EXEC) != 0;
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

// Measure ArtMethod size via two adjacent Constructor ArtMethod pointers, then
// scan the first ArtMethod for the access_flags value.
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
    if (len < 2) {
        env->DeleteLocalRef(ctors);
        return false;
    }
    jobject c0 = env->GetObjectArrayElement(ctors, 0);
    jobject c1 = env->GetObjectArrayElement(ctors, 1);
    uintptr_t first = 0, second = 0;
    uint32_t flags = 0;
    if (c0 != nullptr && g_art_method_field != nullptr) {
        first = static_cast<uintptr_t>(env->GetLongField(c0, g_art_method_field));
        if (g_access_flags_field != nullptr)
            flags = static_cast<uint32_t>(env->GetIntField(c0, g_access_flags_field));
    }
    if (c1 != nullptr && g_art_method_field != nullptr) {
        second = static_cast<uintptr_t>(env->GetLongField(c1, g_art_method_field));
    }
    if (c0 != nullptr) env->DeleteLocalRef(c0);
    if (c1 != nullptr) env->DeleteLocalRef(c1);
    env->DeleteLocalRef(ctors);
    env->ExceptionClear();

    if (first == 0 || second == 0) return false;
    size_t size = first > second ? first - second : second - first;
    if (size < sizeof(void *) * 3 || size > 256 || size % sizeof(void *) != 0) {
        LOGE("probe_art_method_layout: invalid size %zu", size);
        return false;
    }

    // Scan for the access_flags value (prefer offset 4, right after the
    // declaring_class GC root).
    size_t found = 0;
    bool candidate = false;
    for (size_t off = 0; off + 4 <= size; off += 4) {
        uint32_t value;
        memcpy(&value, reinterpret_cast<const void *>(first + off), sizeof(value));
        if (value == flags) {
            if (off == 4) {
                *method_size = size;
                *flags_offset = off;
                memcpy(entry_point_out, reinterpret_cast<const void *>(first + size - sizeof(void *)),
                       sizeof(*entry_point_out));
                return true;
            }
            if (!candidate) {
                found = off;
                candidate = true;
            }
        }
    }
    if (candidate) {
        *method_size = size;
        *flags_offset = found;
        memcpy(entry_point_out, reinterpret_cast<const void *>(first + size - sizeof(void *)),
               sizeof(*entry_point_out));
        return true;
    }
    LOGE("probe_art_method_layout: access_flags not found");
    return false;
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

    WritableArtMethod tw, bw, brw;
    if (!tw.acquire(target, method_size)) return -4;
    if (!bw.acquire(backup, method_size)) return -5;
    if (!brw.acquire(bridge, method_size)) return -6;

    auto *target_af = reinterpret_cast<uint32_t *>(target + af_off);
    auto *bridge_af = reinterpret_cast<uint32_t *>(bridge + af_off);
    auto *target_ep_ptr = reinterpret_cast<void **>(target + ep_off);
    auto *backup_ep_ptr = reinterpret_cast<void **>(backup + ep_off);

    // ── Snapshots for rollback ──────────────────────────────────────────────
    uint32_t original_target_flags = __atomic_load_n(target_af, __ATOMIC_RELAXED);
    uint32_t original_bridge_flags = __atomic_load_n(bridge_af, __ATOMIC_RELAXED);
    void *original_target_ep = __atomic_load_n(target_ep_ptr, __ATOMIC_RELAXED);
    void *original_bridge_ep = __atomic_load_n(
            reinterpret_cast<void **>(bridge + ep_off), __ATOMIC_RELAXED);

    auto rollback = [&]() {
        __atomic_store_n(target_af, original_target_flags, __ATOMIC_RELAXED);
        __atomic_store_n(target_ep_ptr, original_target_ep, __ATOMIC_RELAXED);
        __atomic_store_n(bridge_af, original_bridge_flags, __ATOMIC_RELAXED);
    };

    // ── Bridge: add ACC_COMPILE_DONT_BOTHER, clear precompiled ─────────────
    uint32_t precomp = g_acc_precompiled.load();
    __atomic_store_n(bridge_af,
                     (__atomic_load_n(bridge_af, __ATOMIC_RELAXED) | ACC_COMPILE_DONT_BOTHER) &
                             ~precomp,
                     __ATOMIC_RELAXED);

    // ── Target: clear intrinsic (may change flags), add ACC_COMPILE_DONT_BOTHER ──
    call_set_not_intrinsic(target);
    uint32_t fast_interp = g_acc_fast_interp.load();
    __atomic_store_n(target_af,
                     (__atomic_load_n(target_af, __ATOMIC_RELAXED) | ACC_COMPILE_DONT_BOTHER) &
                             ~precomp,
                     __ATOMIC_RELAXED);

    // ── Snapshot target into backup ─────────────────────────────────────────
    memcpy(reinterpret_cast<void *>(backup), reinterpret_cast<const void *>(target),
           method_size);

    // Clear the target's fast-interpreter bit so it always goes through the
    // trampoline.
    __atomic_store_n(target_af,
                     __atomic_load_n(target_af, __ATOMIC_RELAXED) & ~fast_interp,
                     __ATOMIC_RELAXED);

    // Non-static backup methods become private (matching ART expectations).
    auto *backup_af = reinterpret_cast<uint32_t *>(backup + af_off);
    if ((__atomic_load_n(backup_af, __ATOMIC_RELAXED) & ACC_STATIC) == 0) {
        __atomic_store_n(backup_af,
                         (__atomic_load_n(backup_af, __ATOMIC_RELAXED) | ACC_PRIVATE) &
                                 ~(ACC_PUBLIC | ACC_PROTECTED),
                         __ATOMIC_RELAXED);
    }

    // Backup always runs through the interpreter: point it at the bridge's
    // entry point (the bridge is never JIT-compiled, so its entry point is the
    // interpreter bridge). The memcpy above carried over the target's original
    // entry point, which may be JIT-compiled code compiled for the target's
    // ArtMethod identity — executing it as `backup` would run with mismatched
    // method context.
    __atomic_store_n(backup_ep_ptr, original_bridge_ep, __ATOMIC_RELAXED);

    // ── Redirect the target's entry point to the trampoline ─────────────────
    __atomic_store_n(target_ep_ptr, const_cast<uint8_t *>(trampoline), __ATOMIC_RELAXED);

    // ── Read-back verification; roll back on mismatch ───────────────────────
    if (__atomic_load_n(target_ep_ptr, __ATOMIC_RELAXED) != trampoline) {
        LOGE("art_hook_method: entry point write-back mismatch (bad ep_off=%zu?)",
             ep_off);
        rollback();
        return -10;
    }

    {
        std::lock_guard<std::mutex> lock(g_hook_records_mutex);
        g_hook_records[target] = HookRecord{backup, original_target_flags};
    }
    LOGD("art_hook_method: hooked target=%#lx backup=%#lx bridge=%#lx ep=%#lx",
         target, backup, bridge, reinterpret_cast<uintptr_t>(trampoline));
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

}  // namespace tcqt
