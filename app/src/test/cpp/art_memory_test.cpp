#include "zygisk/art/art_memory.h"
#include <cassert>
#include <cstdio>
#include <limits>

namespace tcqt::zygisk::art_detail {
RuntimeState &state() { static RuntimeState runtime; return runtime; }
}
using namespace tcqt::zygisk::art_detail;

int main() {
    const auto size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    auto *region = static_cast<uint8_t *>(mmap(nullptr, size * 3, PROT_READ | PROT_WRITE,
                                             MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    assert(region != MAP_FAILED);
    const auto address = reinterpret_cast<uintptr_t>(region);
    assert(mprotect(region, size, PROT_READ) == 0);
    {
        WritableArtMethod write;
        assert(write.acquire(address + size - 8, 16));
        assert((get_prot_for_addr(address) & PROT_WRITE) != 0);
        region[size - 1] = 1;
        region[size] = 2;
    }
    assert(get_prot_for_addr(address) == PROT_READ);
    assert(get_prot_for_addr(address + size) == (PROT_READ | PROT_WRITE));
    puts("PASS cross-page writes and original protection restoration");

    assert(munmap(region + size, size) == 0);
    {
        WritableArtMethod write;
        assert(!write.acquire(address + size - 8, 16));
        assert(get_prot_for_addr(address) == PROT_READ);
    }
    puts("PASS partial acquire rollback on unmapped page");
    {
        WritableArtMethod write;
        assert(!write.acquire(0, 1));
        assert(!write.acquire(address, 0));
        assert(!write.acquire(std::numeric_limits<uintptr_t>::max() - 2, 8));
    }
    puts("PASS invalid and overflowing spans");
    assert(get_prot_for_addr(address + size) == -1);
    assert(!is_executable_address(address + size));
    assert(!is_executable_address(0));
    assert(mprotect(region + size * 2, size, PROT_READ | PROT_EXEC) == 0);
    assert(is_executable_address(address + size * 2));
    puts("PASS executable mapping checks reject unmapped addresses");
#ifdef __aarch64__
    {
        register uintptr_t signed_address __asm__("x30") = address;
        __asm__ __volatile__("hint #25" : "+r"(signed_address)); // PACIASP, NOP without PAC
        assert(strip_pac(signed_address) == address);
    }
    puts("PASS PAC stripping (backwards-compatible LR hints)");
#endif
    {
        ScopedSuspend suspend("fixture without ART symbols");
        assert(!suspend.active());
    }
    puts("PASS missing suspension symbols fail closed");
    munmap(region, size);
    munmap(region + size * 2, size);
}
