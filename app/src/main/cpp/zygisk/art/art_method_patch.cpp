#include "zygisk/art/art_memory.h"
#include "zygisk/art/trampoline.h"
#include <mutex>
#include <unordered_map>
#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_runtime.h"
#include <cstring>
#include <vector>
#include "zygisk/logging/log.h"
namespace tcqt::zygisk {
using namespace art_detail;

namespace {
struct HookRecord {
    uintptr_t backup_art;
    uint32_t original_access_flags;
};
std::mutex g_hook_records_mutex;
std::unordered_map<uintptr_t, HookRecord> g_hook_records;

void call_set_not_intrinsic(uintptr_t art_method) {
    if (state().symbols.set_not_intrinsic != nullptr) {
        auto f = reinterpret_cast<void (*)(void *)>(state().symbols.set_not_intrinsic);
        f(reinterpret_cast<void *>(art_method));
    } else {
        // Fallback: manually clear ACC_INTRINSIC.
        uint32_t *af = reinterpret_cast<uint32_t *>(art_method + state().layout.access_flags_offset);
        __atomic_store_n(af, __atomic_load_n(af, __ATOMIC_RELAXED) & ~ACC_INTRINSIC,
                         __ATOMIC_RELAXED);
    }
}

} // namespace
int art_hook_method(JNIEnv *env, uintptr_t target, uintptr_t backup, uintptr_t bridge) {
    (void)env;
    if (!art_hook_initialized() || target == 0 || backup == 0 || bridge == 0) return -1;

    size_t method_size = state().layout.method_size;
    size_t af_off = state().layout.access_flags_offset;
    size_t ep_off = state().layout.entry_point_offset;
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
    uint32_t precomp = state().layout.acc_precompiled;
    uint32_t bridge_flags_new =
            (__atomic_load_n(bridge_af, __ATOMIC_RELAXED) | ACC_COMPILE_DONT_BOTHER) & ~precomp;
    __atomic_store_n(bridge_af, bridge_flags_new, __ATOMIC_RELAXED);
    if (__atomic_load_n(bridge_af, __ATOMIC_RELAXED) != bridge_flags_new) fail("bridge flags");

    // ── Target: clear intrinsic (may change flags), add ACC_COMPILE_DONT_BOTHER ──
    call_set_not_intrinsic(target);
    uint32_t after_set_not_intrinsic = __atomic_load_n(target_af, __ATOMIC_RELAXED);
    uint32_t fast_interp = state().layout.acc_fast_interp;
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
    if (!art_hook_initialized() || target == 0 || backup == 0) return -1;

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

    size_t method_size = state().layout.method_size;
    size_t af_off = state().layout.access_flags_offset;

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


} // namespace tcqt::zygisk
