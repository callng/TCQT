#include "elf_util.h"

#include <dlfcn.h>
#include <link.h>
#include <fcntl.h>
#include <unistd.h>

#include <cstring>

#include "log.h"

namespace tcqt {
namespace {

int dl_iterate_cb(dl_phdr_info *info, size_t /*size*/, void *data) {
    auto *lib = static_cast<ArtLibrary *>(data);
    const char *name = info->dlpi_name;
    if (name == nullptr || name[0] == '\0') return 0;
    // Match the real libart.so (avoid e.g. libart-compiler.so, libartbase.so).
    if (strstr(name, "/libart.so") != nullptr || strcmp(name, "libart.so") == 0) {
        lib->base = info->dlpi_addr;
        lib->path = name;
        return 1;
    }
    return 0;
}

// Minimal ELF64 structures (little-endian arm64).
struct Ehdr64 {
    uint8_t e_ident[16];
    uint16_t e_type;
    uint16_t e_machine;
    uint32_t e_version;
    uint64_t e_entry;
    uint64_t e_phoff;
    uint64_t e_shoff;
    uint32_t e_flags;
    uint16_t e_ehsize;
    uint16_t e_phentsize;
    uint16_t e_phnum;
    uint16_t e_shentsize;
    uint16_t e_shnum;
    uint16_t e_shstrndx;
};

struct Shdr64 {
    uint32_t sh_name;
    uint32_t sh_type;
    uint64_t sh_flags;
    uint64_t sh_addr;
    uint64_t sh_offset;
    uint64_t sh_size;
    uint32_t sh_link;
    uint32_t sh_info;
    uint64_t sh_addralign;
    uint64_t sh_entsize;
};

struct Sym64 {
    uint32_t st_name;
    uint8_t st_info;
    uint8_t st_other;
    uint16_t st_shndx;
    uint64_t st_value;
    uint64_t st_size;
};

constexpr uint32_t SH_TYPE_SYMTAB = 2;
constexpr uint32_t SH_TYPE_DYNSYM = 11;

template <typename T>
bool read_full(int fd, uint64_t offset, T *out, size_t size = sizeof(T)) {
    if (lseek(fd, static_cast<off_t>(offset), SEEK_SET) < 0) return false;
    size_t got = 0;
    auto *dst = reinterpret_cast<uint8_t *>(out);
    while (got < size) {
        ssize_t n = read(fd, dst + got, size - got);
        if (n <= 0) return false;
        got += static_cast<size_t>(n);
    }
    return true;
}

}  // namespace

bool find_art_library(ArtLibrary *out) {
    if (out == nullptr) return false;
    dl_iterate_phdr(dl_iterate_cb, out);
    return out->base != 0 && !out->path.empty();
}

uintptr_t resolve_elf_symbol(const std::string &path, const char *symbol) {
    if (path.empty() || symbol == nullptr || symbol[0] == '\0') return 0;

    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        LOGE("resolve_elf_symbol: cannot open %s", path.c_str());
        return 0;
    }

    Ehdr64 ehdr{};
    if (!read_full(fd, 0, &ehdr)) {
        close(fd);
        return 0;
    }
    if (memcmp(ehdr.e_ident, "\x7f" "ELF", 4) != 0 || ehdr.e_shentsize < sizeof(Shdr64)) {
        close(fd);
        return 0;
    }

    // Load section headers and string table.
    auto *shdrs = new Shdr64[ehdr.e_shnum];
    if (!read_full(fd, ehdr.e_shoff, shdrs, sizeof(Shdr64) * ehdr.e_shnum)) {
        delete[] shdrs;
        close(fd);
        return 0;
    }
    if (ehdr.e_shstrndx >= ehdr.e_shnum) {
        delete[] shdrs;
        close(fd);
        return 0;
    }
    Shdr64 shstr = shdrs[ehdr.e_shstrndx];
    auto *shstr_data = new uint8_t[shstr.sh_size];
    if (!read_full(fd, shstr.sh_offset, shstr_data, shstr.sh_size)) {
        delete[] shstr_data;
        delete[] shdrs;
        close(fd);
        return 0;
    }

    uintptr_t result = 0;
    const size_t name_len = strlen(symbol);

    for (uint16_t i = 0; i < ehdr.e_shnum; ++i) {
        const Shdr64 &sh = shdrs[i];
        if (sh.sh_type != SH_TYPE_SYMTAB && sh.sh_type != SH_TYPE_DYNSYM) continue;
        if (sh.sh_link >= ehdr.e_shnum) continue;
        if (sh.sh_entsize < sizeof(Sym64)) continue;

        const Shdr64 &str_sh = shdrs[sh.sh_link];
        auto *str_data = new uint8_t[str_sh.sh_size];
        if (!read_full(fd, str_sh.sh_offset, str_data, str_sh.sh_size)) {
            delete[] str_data;
            continue;
        }

        const uint64_t sym_count = sh.sh_size / sh.sh_entsize;
        auto *syms = new Sym64[sym_count];
        if (!read_full(fd, sh.sh_offset, syms, sizeof(Sym64) * sym_count)) {
            delete[] syms;
            delete[] str_data;
            continue;
        }

        for (uint64_t k = 0; k < sym_count; ++k) {
            const Sym64 &sym = syms[k];
            if (sym.st_name == 0 || sym.st_value == 0 || sym.st_shndx == 0) continue;
            if (sym.st_name + name_len >= str_sh.sh_size) continue;
            if (memcmp(str_data + sym.st_name, symbol, name_len) == 0 &&
                (sym.st_name + name_len >= str_sh.sh_size ||
                 str_data[sym.st_name + name_len] == '\0')) {
                result = sym.st_value;
                delete[] syms;
                delete[] str_data;
                goto done;
            }
        }
        delete[] syms;
        delete[] str_data;
    }

done:
    delete[] shstr_data;
    delete[] shdrs;
    close(fd);
    return result;
}

}  // namespace tcqt
