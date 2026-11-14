#include "zygisk/art/art_memory.h"
#include "zygisk/art/trampoline.h"
#include <mutex>
#include <unordered_map>
#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_runtime.h"
#include "zygisk/art/art_quick_redirect.h"
#include <array>
#include <cstring>
#include <vector>
#include "zygisk/logging/log.h"
namespace tcqt::zygisk {
using namespace art_detail;

namespace {
struct HookRecord {
    uintptr_t backup_art;
    uint32_t original_access_flags;
    void *restore_entry_point;
    const uint8_t *trampoline;
};
std::mutex g_hook_records_mutex;
std::unordered_map<uintptr_t, HookRecord> g_hook_records;

bool readable_range(uintptr_t address, size_t length) {
    if (address == 0 || length == 0 || length - 1 > UINTPTR_MAX - address) return false;
    const int first = get_prot_for_addr(address);
    const int last = get_prot_for_addr(address + length - 1);
    return first >= 0 && last >= 0 && (first & PROT_READ) && (last & PROT_READ);
}

bool is_quick_redirect(uintptr_t entry, size_t entry_offset, size_t method_size) {
    entry = strip_pac(entry);
    // Read instructions first, then the exact literal span. A 20-byte LSPlant
    // stub can sit at the end of a mapping; do not read a whole 32-byte slot.
    std::array<uint8_t, 32> code{};
    if (!readable_range(entry, 16)) return false;
    memcpy(code.data(), reinterpret_cast<const void *>(entry), 16);
    if (code[0] == 0x5f || code[0] == 0x9f || code[0] == 0xdf) {
        if (!readable_range(entry, 20)) return false;
        memcpy(code.data(), reinterpret_cast<const void *>(entry), 20);
    }
    const size_t literal = quick_redirect_literal(code, entry_offset);
    if (literal == 0 || !readable_range(entry + literal, sizeof(uintptr_t))) return false;
    uintptr_t redirected_method = 0;
    memcpy(&redirected_method, reinterpret_cast<const void *>(entry + literal), sizeof(uintptr_t));
    if ((redirected_method & (sizeof(void *) - 1)) != 0 ||
        !readable_range(redirected_method, method_size)) return false;
    uintptr_t redirected_entry = 0;
    memcpy(&redirected_entry, reinterpret_cast<const void *>(redirected_method + entry_offset),
           sizeof(redirected_entry));
    return is_executable_address(redirected_entry);
}

bool call_set_not_intrinsic(uintptr_t art_method) {
    auto *af = reinterpret_cast<uint32_t *>(art_method + state().layout.access_flags_offset);
    if ((__atomic_load_n(af, __ATOMIC_RELAXED) & ACC_INTRINSIC) == 0) return true;
    if (state().symbols.set_not_intrinsic != nullptr) {
        auto f = reinterpret_cast<void (*)(void *)>(state().symbols.set_not_intrinsic);
        f(reinterpret_cast<void *>(art_method));
        return (__atomic_load_n(af, __ATOMIC_RELAXED) & ACC_INTRINSIC) == 0;
    }
    // Clearing just the sign bit reinterprets intrinsic IDs as runtime flags.
    // ART also restores hidden-API flags; do not guess its version-specific mask.
    LOGE("art_hook_method: intrinsic target requires ArtMethod::SetNotIntrinsic");
    return false;
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
        // reference (flags sit at offset 4 in AOSP Android 13..17) or an
        // OEM 8-byte pointer (flags at offset 8). Validate the probed layout instead of
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
    void *interpreter = state().symbols.quick_to_interpreter;
    if (!is_executable_address(reinterpret_cast<uintptr_t>(interpreter))) return -13;

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
    auto *bridge_ep_ptr = reinterpret_cast<void **>(bridge + ep_off);
    void *original_bridge_ep = __atomic_load_n(bridge_ep_ptr, __ATOMIC_RELAXED);

    bool hook_ok = true;
    auto fail = [&](const char *what) {
        LOGE("art_hook_method: verification failed (%s), rolling back", what);
        hook_ok = false;
    };
    auto rollback = [&]() {
        __atomic_store_n(target_af, original_target_flags, __ATOMIC_RELAXED);
        __atomic_store_n(target_ep_ptr, original_target_ep, __ATOMIC_RELAXED);
        __atomic_store_n(bridge_af, original_bridge_flags, __ATOMIC_RELAXED);
        __atomic_store_n(bridge_ep_ptr, original_bridge_ep, __ATOMIC_RELAXED);
        memcpy(reinterpret_cast<void *>(backup), backup_orig.data(), method_size);
    };

    // Resolve intrinsic semantics before modifying any of the three methods.
    if (!call_set_not_intrinsic(target)) {
        rollback();
        return -14;
    }

    // Keep the generated bridge interpreted even if the runtime had already
    // selected Nterp/JIT code while its class was initialized.
    uint32_t bridge_flags_new = hooked_method_flags(original_bridge_flags);
    __atomic_store_n(bridge_af, bridge_flags_new, __ATOMIC_RELAXED);
    __atomic_store_n(bridge_ep_ptr, interpreter, __ATOMIC_RELAXED);
    if (__atomic_load_n(bridge_af, __ATOMIC_RELAXED) != bridge_flags_new) fail("bridge flags");

    // ── Target: clear intrinsic (may change flags), add ACC_COMPILE_DONT_BOTHER ──
    uint32_t after_set_not_intrinsic = __atomic_load_n(target_af, __ATOMIC_RELAXED);
    uint32_t target_flags_new = hooked_method_flags(after_set_not_intrinsic);
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

    // The backup retains the target's declaring class and DEX semantics.
    // 0x00400000 is kAccDefault, not a proxy flag: preserve default methods.
    // For JNI methods, preserve FastNative/CriticalNative calling conventions.
    uint32_t backup_flags_new = __atomic_load_n(backup_af, __ATOMIC_RELAXED);
    if ((backup_flags_new & ACC_STATIC) == 0) {
        backup_flags_new = (backup_flags_new | ACC_PRIVATE) & ~(ACC_PUBLIC | ACC_PROTECTED);
    }
    __atomic_store_n(backup_af, backup_flags_new, __ATOMIC_RELAXED);
    if (__atomic_load_n(backup_af, __ATOMIC_RELAXED) != backup_flags_new) fail("backup flags");

    // Managed method bodies use the interpreter because this engine does not
    // track JIT code movement. Preserve an already non-compilable method's
    // entry as well as recognized quick redirects: newer hook engines install
    // compiled dispatch code which cannot be identified by a short stub pattern.
    // The preexisting flag is checked before TCQT adds its own. Interpreting
    // the copied DEX body would bypass that engine's callbacks.
    // Native methods also need their JNI entry and data_ from the copied method.
    const bool existing_non_compilable =
            (original_target_flags & (ACC_INTRINSIC | ACC_COMPILE_DONT_BOTHER | ACC_PRECOMPILED)) ==
            ACC_COMPILE_DONT_BOTHER;
    const bool preserve_entry = (original_target_flags & ACC_NATIVE) != 0 || existing_non_compilable ||
            is_quick_redirect(reinterpret_cast<uintptr_t>(original_target_ep), ep_off, method_size);
    void *backup_entry = preserve_entry ? original_target_ep : interpreter;
    __atomic_store_n(backup_ep_ptr, backup_entry, __ATOMIC_RELAXED);
    if (__atomic_load_n(backup_ep_ptr, __ATOMIC_RELAXED) != backup_entry)
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
        // A managed target's old JIT entry can be reclaimed after deoptimization.
        // Use its safe interpreter backup when unhooking; retained framework
        // and native entries must instead be restored exactly.
        g_hook_records[target] = HookRecord{backup, original_target_flags, backup_entry, trampoline};
    }
    if (preserve_entry)
        LOGI("art_hook_method: preserved existing entry target=%#lx backup=%#lx entry=%p flags=%#x",
             target, backup, original_target_ep, original_target_flags);
    LOGD("art_hook_method: hooked target=%#lx backup=%#lx bridge=%#lx ep=%#lx "
         "size=%zu flags_off=%zu ep_off=%zu",
         target, backup, bridge, reinterpret_cast<uintptr_t>(trampoline),
         method_size, af_off, ep_off);
    return 0;
}

int art_unhook_method(JNIEnv *env, uintptr_t target, uintptr_t backup) {
    (void)env;
    if (!art_hook_initialized() || target == 0 || backup == 0) return -1;

    HookRecord record{};
    {
        std::lock_guard<std::mutex> lock(g_hook_records_mutex);
        auto it = g_hook_records.find(target);
        if (it == g_hook_records.end() || it->second.backup_art != backup) {
            LOGE("art_unhook_method: no matching hook for target=%#lx", target);
            return -2;
        }
        record = it->second;
    }

    ScopedSuspend suspend("TCQT Unhooking");
    if (!suspend.active()) return -3;

    size_t method_size = state().layout.method_size;
    size_t af_off = state().layout.access_flags_offset;
    auto *target_entry = reinterpret_cast<void **>(target + state().layout.entry_point_offset);
    if (strip_pac(reinterpret_cast<uintptr_t>(__atomic_load_n(target_entry, __ATOMIC_RELAXED))) !=
        reinterpret_cast<uintptr_t>(record.trampoline)) {
        // Another framework now owns the outer entry and may have copied our
        // trampoline into its backup. Keep the empty Java dispatch slot alive
        // as a passthrough; restoring here would erase that framework's hook.
        LOGI("art_unhook_method: retaining chained passthrough for target=%#lx", target);
        return 1;
    }

    WritableArtMethod tw;
    if (!tw.acquire(target, method_size)) return -4;
    // Only these two fields were changed on the target. In particular, never
    // restore the backup's interpreter entry over the original hook trampoline.
    __atomic_store_n(target_entry, record.restore_entry_point, __ATOMIC_RELAXED);
    __atomic_store_n(reinterpret_cast<uint32_t *>(target + af_off), record.original_access_flags,
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
