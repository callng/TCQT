#include "zygisk/plt/plt_elf.h"

#include <algorithm>
#include <cstring>

namespace tcqt::zygisk::plt_detail {
namespace {
class Image {
public:
    explicit Image(const dl_phdr_info &info) : info_(info) {}
    bool address(uintptr_t offset, uintptr_t *result) const {
        return !__builtin_add_overflow(static_cast<uintptr_t>(info_.dlpi_addr), offset, result);
    }
    bool contains(uintptr_t address, size_t bytes) const {
        if (bytes == 0 || bytes > UINTPTR_MAX - address) return false;
        for (size_t i = 0; i < info_.dlpi_phnum; ++i) {
            const auto &p = info_.dlpi_phdr[i];
            uintptr_t start;
            if (p.p_type != PT_LOAD || !(p.p_flags & PF_R) || !this->address(p.p_vaddr, &start)) continue;
            if (address >= start && address - start <= p.p_memsz &&
                bytes <= p.p_memsz - (address - start)) return true;
        }
        return false;
    }
    template<typename T> bool read(uintptr_t address, T *out) const {
        if (!contains(address, sizeof(T))) return false;
        memcpy(out, reinterpret_cast<const void *>(address), sizeof(T));
        return true;
    }
    size_t max_relocations() const {
        size_t bytes = 0;
        for (size_t i = 0; i < info_.dlpi_phnum; ++i) {
            const auto &p = info_.dlpi_phdr[i];
            if (p.p_type == PT_LOAD && __builtin_add_overflow(bytes, p.p_memsz, &bytes)) return 0;
        }
        return bytes / sizeof(uintptr_t);
    }
private:
    const dl_phdr_info &info_;
};

// APS2 is a signed LEB128 stream, including negative offset deltas between
// relocation groups. Unlike the linker's fatal decoder, this rejects bad input.
class Sleb128 {
public:
    Sleb128(const uint8_t *data, size_t size) : current_(data), end_(data + size) {}
    bool next(uint64_t *value) {
        *value = 0;
        for (unsigned shift = 0; shift <= 63; shift += 7) {
            if (current_ == end_) return false;
            const uint8_t byte = *current_++;
            const uint8_t payload = byte & 0x7f;
            if (shift == 63 && payload != 0 && payload != 0x7f) return false;
            *value |= static_cast<uint64_t>(payload) << shift;
            if (!(byte & 0x80)) {
                if (shift < 63 && (byte & 0x40)) *value |= UINT64_MAX << (shift + 7);
                return true;
            }
        }
        return false;
    }
private:
    const uint8_t *current_;
    const uint8_t *end_;
};

bool advance(uint64_t *offset, uint64_t delta) {
    if (delta <= INT64_MAX) return !__builtin_add_overflow(*offset, delta, offset);
    const uint64_t magnitude = uint64_t{0} - delta;
    if (*offset < magnitude) return false;
    *offset -= magnitude;
    return true;
}

template<typename Visitor>
bool visit_packed(const uint8_t *data, size_t size, size_t maximum, Visitor visit) {
    if (size < 4 || memcmp(data, "APS2", 4) != 0) return false;
    Sleb128 input(data + 4, size - 4);
    uint64_t count, offset;
    if (!input.next(&count) || count > maximum || !input.next(&offset)) return false;
    for (uint64_t done = 0; done < count;) {
        uint64_t group, flags, delta = 0, info = 0, ignored;
        if (!input.next(&group) || group == 0 || group > count - done ||
            !input.next(&flags) || (flags & ~uint64_t{15})) return false;
        if ((flags & 2) && !input.next(&delta)) return false;
        if ((flags & 1) && !input.next(&info)) return false;
        // We inspect resolved slots, not their original relocation addends.
        if ((flags & 12) == 12 && !input.next(&ignored)) return false;
        for (uint64_t i = 0; i < group; ++i) {
            if (!(flags & 2) && !input.next(&delta)) return false;
            if (!advance(&offset, delta)) return false;
            if (!(flags & 1) && !input.next(&info)) return false;
            if ((flags & 12) == 8 && !input.next(&ignored)) return false;
            if (!visit(offset, info)) return false;
        }
        done += group;
    }
    return true;
}
}

bool find_got_slots(const dl_phdr_info &info, const char *name, std::vector<uintptr_t> *slots) {
    if (slots == nullptr) return false;
    slots->clear();
    if (info.dlpi_phdr == nullptr || name == nullptr || *name == '\0') return false;
    const Image image(info);
    uintptr_t dynamic = 0;
    size_t dynamic_size = 0;
    for (size_t i = 0; i < info.dlpi_phnum; ++i) {
        const auto &p = info.dlpi_phdr[i];
        if (p.p_type == PT_DYNAMIC) {
            if (!image.address(p.p_vaddr, &dynamic)) return false;
            dynamic_size = p.p_memsz;
            break;
        }
    }
    if (!image.contains(dynamic, dynamic_size)) return false;
    uintptr_t symtab = 0, strtab = 0, rela = 0, jmprel = 0, packed = 0;
    size_t strsz = 0, relasz = 0, pltsz = 0, packedsz = 0;
    bool ended = false;
    for (size_t i = 0; i < dynamic_size / sizeof(ElfW(Dyn)); ++i) {
        ElfW(Dyn) entry;
        if (!image.read(dynamic + i * sizeof(entry), &entry)) return false;
        if (entry.d_tag == DT_NULL) { ended = true; break; }
        const auto value = entry.d_un.d_val;
        switch (entry.d_tag) {
            case DT_SYMTAB: if (!image.address(value, &symtab)) return false; break;
            case DT_STRTAB: if (!image.address(value, &strtab)) return false; break;
            case DT_STRSZ: strsz = value; break;
            case DT_SYMENT: if (value != sizeof(ElfW(Sym))) return false; break;
            case DT_RELA: if (!image.address(value, &rela)) return false; break;
            case DT_RELASZ: relasz = value; break;
            case DT_RELAENT: if (value != sizeof(ElfW(Rela))) return false; break;
            case DT_JMPREL: if (!image.address(value, &jmprel)) return false; break;
            case DT_PLTRELSZ: pltsz = value; break;
            case DT_PLTREL: if (value != DT_RELA) return false; break;
            case DT_ANDROID_RELA: if (!image.address(value, &packed)) return false; break;
            case DT_ANDROID_RELASZ: packedsz = value; break;
            // RELR/ANDROID_RELR encode only relative relocations, with no
            // imported symbol to hook. Leave their already relocated data alone.
            default: break;
        }
    }
    if (!ended || !image.contains(symtab, sizeof(ElfW(Sym))) || !image.contains(strtab, strsz)) return false;
    std::vector<uintptr_t> found;
    const size_t name_length = strlen(name);
    auto visit = [&](uint64_t offset, uint64_t rinfo) {
        const uint32_t type = ELF64_R_TYPE(rinfo);
        if (type != R_AARCH64_JUMP_SLOT && type != R_AARCH64_GLOB_DAT) return true;
        uintptr_t symbol_address;
        const uint64_t index = ELF64_R_SYM(rinfo);
        if (__builtin_mul_overflow(index, sizeof(ElfW(Sym)), &symbol_address) ||
            __builtin_add_overflow(symtab, symbol_address, &symbol_address)) return false;
        ElfW(Sym) symbol;
        if (!image.read(symbol_address, &symbol) || symbol.st_name >= strsz) return false;
        const auto *actual = reinterpret_cast<const char *>(strtab + symbol.st_name);
        const size_t available = strsz - symbol.st_name;
        if (memchr(actual, '\0', available) == nullptr) return false;
        if (name_length >= available || memcmp(actual, name, name_length) != 0 || actual[name_length] != '\0') return true;
        uintptr_t slot;
        if (!image.address(offset, &slot) || slot % alignof(uintptr_t) != 0 ||
            !image.contains(slot, sizeof(uintptr_t))) return false;
        found.push_back(slot);
        return true;
    };
    auto visit_rela = [&](uintptr_t table, size_t bytes) {
        if (bytes == 0) return true;
        if (bytes % sizeof(ElfW(Rela)) != 0 || !image.contains(table, bytes)) return false;
        for (size_t i = 0; i < bytes / sizeof(ElfW(Rela)); ++i) {
            ElfW(Rela) entry;
            if (!image.read(table + i * sizeof(entry), &entry) || !visit(entry.r_offset, entry.r_info)) return false;
        }
        return true;
    };
    if (!visit_rela(rela, relasz) || !visit_rela(jmprel, pltsz)) return false;
    if (packedsz != 0 && (!image.contains(packed, packedsz) ||
        !visit_packed(reinterpret_cast<const uint8_t *>(packed), packedsz, image.max_relocations(), visit))) return false;
    std::sort(found.begin(), found.end());
    found.erase(std::unique(found.begin(), found.end()), found.end());
    *slots = std::move(found);
    return true;
}
}
