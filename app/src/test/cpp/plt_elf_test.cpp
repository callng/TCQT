#include "zygisk/plt/plt_elf.h"
#include <sys/mman.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>

using tcqt::zygisk::plt_detail::find_got_slots;
namespace {
void require(bool ok, const char *message) {
    if (!ok) { fprintf(stderr, "FAIL: %s\n", message); std::exit(1); }
}
struct Fixture {
    static constexpr size_t kSym = 0x200, kStr = 0x300, kRel = 0x400, kPacked = 0x500;
    size_t size = static_cast<size_t>(sysconf(_SC_PAGESIZE)) * 2;
    uint8_t *data = static_cast<uint8_t *>(mmap(nullptr, size, PROT_READ | PROT_WRITE,
                                               MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    ElfW(Phdr) phdr[2]{};
    dl_phdr_info info{};
    size_t slot = size - 32;
    Fixture() {
        require(data != MAP_FAILED, "allocate ELF fixture");
        phdr[0].p_type = PT_LOAD; phdr[0].p_flags = PF_R | PF_W; phdr[0].p_memsz = size;
        phdr[1].p_type = PT_DYNAMIC; phdr[1].p_memsz = 12 * sizeof(ElfW(Dyn));
        info.dlpi_addr = reinterpret_cast<uintptr_t>(data);
        info.dlpi_phdr = phdr; info.dlpi_phnum = 2;
        auto *dyn = reinterpret_cast<ElfW(Dyn) *>(data);
        const ElfW(Dyn) entries[] = {
            {DT_SYMTAB, {.d_ptr = kSym}}, {DT_STRTAB, {.d_ptr = kStr}},
            {DT_STRSZ, {.d_val = 8}}, {DT_SYMENT, {.d_val = sizeof(ElfW(Sym))}},
            {DT_RELA, {.d_ptr = kRel}}, {DT_RELASZ, {.d_val = sizeof(ElfW(Rela))}},
            {DT_RELAENT, {.d_val = sizeof(ElfW(Rela))}}, {DT_PLTREL, {.d_val = DT_RELA}},
            {DT_ANDROID_RELA, {.d_ptr = kPacked}}, {DT_ANDROID_RELASZ, {.d_val = 0}},
            {DT_NULL, {.d_val = 0}}, {DT_NULL, {.d_val = 0}},
        };
        memcpy(dyn, entries, sizeof(entries));
        reinterpret_cast<ElfW(Sym) *>(data + kSym)[1].st_name = 1;
        memcpy(data + kStr, "\0getpid\0", 8);
        *rel() = {slot, ELF64_R_INFO(1, R_AARCH64_GLOB_DAT), 0};
    }
    ~Fixture() { munmap(data, size); }
    ElfW(Rela) *rel() { return reinterpret_cast<ElfW(Rela) *>(data + kRel); }
    ElfW(Dyn) &tag(int64_t tag) {
        auto *dyn = reinterpret_cast<ElfW(Dyn) *>(data);
        for (size_t i = 0; i < 12; ++i) if (dyn[i].d_tag == tag) return dyn[i];
        std::abort();
    }
    void packed(const std::vector<uint8_t> &bytes) {
        memcpy(data + kPacked, bytes.data(), bytes.size());
        tag(DT_ANDROID_RELASZ).d_un.d_val = bytes.size();
    }
    bool parse(std::vector<uintptr_t> *slots) { return find_got_slots(info, "getpid", slots); }
    void rejects(const char *message) {
        std::vector<uintptr_t> slots{123};
        require(!parse(&slots) && slots.empty(), message);
    }
};
void sleb(std::vector<uint8_t> &out, int64_t value) {
    for (;;) {
        uint8_t byte = value & 0x7f;
        value >>= 7;
        bool last = (value == 0 && !(byte & 0x40)) || (value == -1 && (byte & 0x40));
        out.push_back(byte | (last ? 0 : 0x80));
        if (last) return;
    }
}
std::vector<uint8_t> stream(std::initializer_list<int64_t> values) {
    std::vector<uint8_t> bytes{'A', 'P', 'S', '2'};
    for (auto value : values) sleb(bytes, value);
    return bytes;
}
}

int main() {
    printf("PLT_TEST_PID=%d uid=%d page_size=%ld mode=parser\n", getpid(), getuid(), sysconf(_SC_PAGESIZE));
    constexpr int64_t glob = ELF64_R_INFO(1, R_AARCH64_GLOB_DAT);
    constexpr int64_t jump = ELF64_R_INFO(1, R_AARCH64_JUMP_SLOT);
    {
        Fixture f;
        std::vector<uintptr_t> slots;
        require(f.parse(&slots) && slots == std::vector<uintptr_t>{f.info.dlpi_addr + f.slot}, "plain RELA");
        f.packed(stream({3, static_cast<int64_t>(f.slot) - 8, 2, 15, 8, glob, 0, 1, 8, -8, jump, 4}));
        require(f.parse(&slots) && slots == std::vector<uintptr_t>{f.info.dlpi_addr + f.slot, f.info.dlpi_addr + f.slot + 8},
                "APS2 grouped fields, addends, negative delta and duplicate slots");
        f.packed(stream({2, static_cast<int64_t>(f.slot) - 8, 2, 0, 8, glob, 8, jump}));
        require(f.parse(&slots) && slots.size() == 2, "APS2 per-entry offset/info without addends");
        puts("PASS: RELA and grouped/ungrouped APS2, negative deltas and deduplication");
    }
    {
        Fixture f;
        const auto valid = stream({1, 0, 1, 0, static_cast<int64_t>(f.slot), glob});
        for (size_t length = 1; length < valid.size(); ++length) {
            f.packed(std::vector<uint8_t>(valid.begin(), valid.begin() + length));
            f.rejects("truncated stream publishes no partial slots");
        }
        auto overflow = stream({1, 0, 1, 0});
        overflow.insert(overflow.end(), 11, 0x80);
        f.packed(overflow); f.rejects("overlong SLEB128");
        puts("PASS: every truncated prefix and overflowing SLEB128 rejected atomically");
    }
    {
        Fixture f;
        for (const auto &bytes : {stream({-1, 0}), stream({1, 0, 0, 0}), stream({1, 0, 2, 0}),
                stream({1, 0, 1, 16}), stream({1, 0, 1, 0, -8, glob})}) {
            f.packed(bytes); f.rejects("invalid count, group, flags or negative offset");
        }
        puts("PASS: invalid APS2 counts/groups/flags and offset underflow rejected");
    }
    {
        Fixture f; f.tag(DT_SYMENT).d_un.d_val = 1; f.rejects("bad symbol stride");
        Fixture g; g.tag(DT_PLTREL).d_un.d_val = DT_REL; g.rejects("REL in arm64 PLT");
        Fixture h; h.tag(DT_RELASZ).d_un.d_val = 1; h.rejects("partial RELA");
        Fixture i; i.phdr[1].p_memsz = 10 * sizeof(ElfW(Dyn)); i.rejects("unterminated dynamic table");
        puts("PASS: invalid ELF entry sizes/types and missing DT_NULL rejected");
    }
    {
        Fixture f; f.tag(DT_STRTAB).d_un.d_ptr = f.size - 1; f.rejects("strtab outside load segment");
        Fixture g; memset(g.data + Fixture::kStr, 'x', 8); g.rejects("unterminated symbol name");
        Fixture h; h.rel()->r_info = ELF64_R_INFO(UINT32_MAX, R_AARCH64_GLOB_DAT); h.rejects("out-of-range symbol");
        Fixture i; i.rel()->r_offset = i.size; i.rejects("out-of-range GOT slot");
        Fixture j; ++j.rel()->r_offset; j.rejects("unaligned atomic GOT slot");
        puts("PASS: symbol/string/GOT bounds and pointer alignment checked");
    }
    puts("PLT_ELF_PASS");
}
