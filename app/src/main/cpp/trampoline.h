#pragma once

#include <cstddef>
#include <cstdint>

namespace tcqt {

// Executable trampoline pool backed by a dual-mapped memfd
// (PROT_READ|PROT_WRITE alias for writing, PROT_READ|PROT_EXEC alias for
// execution).  Avoids mprotect(PROT_EXEC), which SELinux blocks on Android.
class TrampolinePool {
public:
    static TrampolinePool *instance();

    // Allocate one trampoline slot that redirects into [bridge_art + ep_offset]
    // and return its executable address. Returns nullptr when exhausted.
    const uint8_t *allocate(uintptr_t bridge_art, size_t ep_offset);

    bool is_ready() const { return writable_ != nullptr && pool_size_ != 0; }

    ~TrampolinePool() = default;

private:
    TrampolinePool();
    uint8_t *writable_ = nullptr;
    const uint8_t *executable_ = nullptr;
    size_t next_slot_ = 0;
    size_t pool_size_ = 0;
};

}  // namespace tcqt
