#pragma once

#include <cstdint>
#include <string>

namespace tcqt {

// Location of the loaded libart.so in the process address space.
struct ArtLibrary {
    uintptr_t base = 0;   // load bias (dlpi_addr)
    std::string path;     // on-disk path, e.g. /apex/com.android.art/lib64/libart.so
};

// Locate libart.so via dl_iterate_phdr.
bool find_art_library(ArtLibrary *out);

// Resolve an ELF symbol from the on-disk shared object (scans .dynsym/.symtab).
// Returns base + st_value, or 0 when the symbol is not found.
uintptr_t resolve_elf_symbol(const std::string &path, const char *symbol);

}  // namespace tcqt
