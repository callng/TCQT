#include "trampoline.h"

#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>

#include "log.h"

namespace tcqt {

namespace {
constexpr size_t POOL_SIZE = 1024 * 1024;   // 1 MB
constexpr size_t TRAMPOLINE_STRIDE = 32;    // 20-byte stub padded to 32
}  // namespace

TrampolinePool *TrampolinePool::instance() {
    static TrampolinePool pool;
    return &pool;
}

TrampolinePool::TrampolinePool() {
#ifdef __aarch64__
    long mfd = syscall(SYS_memfd_create, "jit-cache", MFD_CLOEXEC);
    if (mfd < 0) {
        LOGE("TrampolinePool: memfd_create failed (errno=%d)", errno);
        return;
    }
    if (ftruncate(mfd, POOL_SIZE) < 0) {
        close(mfd);
        return;
    }
    void *w = mmap(nullptr, POOL_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, mfd, 0);
    void *x = mmap(nullptr, POOL_SIZE, PROT_READ | PROT_EXEC, MAP_SHARED, mfd, 0);
    close(mfd);
    if (w == MAP_FAILED || x == MAP_FAILED) {
        if (w != MAP_FAILED) munmap(w, POOL_SIZE);
        if (x != MAP_FAILED) munmap(x, POOL_SIZE);
        LOGE("TrampolinePool: mmap failed");
        return;
    }
    writable_ = static_cast<uint8_t *>(w);
    executable_ = static_cast<const uint8_t *>(x);
    pool_size_ = POOL_SIZE;
#endif
}

const uint8_t *TrampolinePool::allocate(uintptr_t bridge_art, size_t ep_offset) {
#ifdef __aarch64__
    if (writable_ == nullptr || pool_size_ == 0) {
        LOGE("TrampolinePool: pool unavailable");
        return nullptr;
    }
    // Atomic slot allocation so concurrent hooks can never overlap and
    // overwrite each other's stub instructions / literal.
    size_t slot = next_slot_.fetch_add(TRAMPOLINE_STRIDE);
    if (slot + TRAMPOLINE_STRIDE > pool_size_) {
        LOGE("TrampolinePool: pool exhausted");
        return nullptr;
    }

    uint8_t *w = writable_ + slot;
    const uint8_t *x = executable_ + slot;

    // arm64 stub (20 bytes + 12-byte literal, padded to 32):
    //   ldr x0, #12          ; x0 = bridge_art_method (8-byte literal at +12)
    //   ldur x16, [x0, #ep]  ; x16 = [bridge_art + ep_offset] (quick entry point)
    //   br x16               ; jump into the bridge
    //   nop
    //   .8byte bridge_art_method
    uint32_t ep = static_cast<uint32_t>(ep_offset & 0x1ff);
    uint32_t ldur_x16 = 0xF8400010u | (ep << 12);  // LDUR X16, [X0, #imm9]
    uint32_t instr[3] = {
            0x58000060u,  // ldr x0, #12
            ldur_x16,
            0xD61F0200u,  // br x16
    };
    memcpy(w, instr, sizeof(instr));
    memcpy(w + 12, &bridge_art, sizeof(bridge_art));

    // Flush icache so execution sees the new instructions.
    __builtin___clear_cache(const_cast<char *>(reinterpret_cast<const char *>(x)),
                            const_cast<char *>(reinterpret_cast<const char *>(x + TRAMPOLINE_STRIDE)));
    return x;
#else
    (void)bridge_art;
    (void)ep_offset;
    LOGE("TrampolinePool: unsupported architecture");
    return nullptr;
#endif
}

}  // namespace tcqt
