#pragma once

#include <cstdint>
#include <link.h>
#include <vector>

namespace tcqt::zygisk::plt_detail {
// Collect first, mutate later: malformed metadata must not produce a partial
// installation. Only symbol-bearing AArch64 JUMP_SLOT/GLOB_DAT are selected.
bool find_got_slots(const dl_phdr_info &info, const char *symbol,
                    std::vector<uintptr_t> *slots);
}
