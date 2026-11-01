#pragma once
#include <cerrno>
#include <cstdint>
#include <sys/mman.h>
#include <unistd.h>
#include "zygisk/art/art_runtime.h"
#include "zygisk/logging/log.h"
namespace tcqt::zygisk::art_detail {
uintptr_t strip_pac(uintptr_t address);
int get_prot_for_addr(uintptr_t address);
bool is_executable_address(uintptr_t address);
class ScopedSuspend {
public:
    explicit ScopedSuspend(const char *reason) {
        if (state().symbols.suspend_ctor != nullptr) {
            auto ctor = reinterpret_cast<void (*)(void *, const char *, bool)>(state().symbols.suspend_ctor);
            ctor(storage_, reason, false);
        }
    }
    ~ScopedSuspend() {
        if (state().symbols.suspend_dtor != nullptr) {
            auto dtor = reinterpret_cast<void (*)(void *)>(state().symbols.suspend_dtor);
            dtor(storage_);
        }
    }
    bool active() const { return state().symbols.suspend_ctor != nullptr && state().symbols.suspend_dtor != nullptr; }

private:
    alignas(16) uint8_t storage_[256];
};

inline constexpr size_t MAX_PAGES = 257;

struct PageState {
    uintptr_t address = 0;
    size_t length = 0;
    int original_protection = 0;
    bool changed = false;
};

class WritableArtMethod {
public:
    WritableArtMethod() = default;
    WritableArtMethod(const WritableArtMethod &) = delete;
    WritableArtMethod &operator=(const WritableArtMethod &) = delete;
    bool acquire(uintptr_t address, size_t length) {
        if (address == 0 || length == 0 || length - 1 > UINTPTR_MAX - address) return false;
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


} // namespace tcqt::zygisk::art_detail
