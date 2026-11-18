#include "kernel_scan.h"
#include <algorithm>
#include <array>
#include <cstdio>
#include <cstring>
#include <span>
#include <string>
#include <unistd.h>
#include <vector>

namespace tcqt::recall {
namespace {
template<typename T> T read(const void *address) {
    T value;
    std::memcpy(&value, address, sizeof(value));
    return value;
}

bool mapped(const dl_phdr_info &info, uintptr_t address, size_t size, uint32_t flags) {
    for (size_t i = 0; i < info.dlpi_phnum; ++i) {
        const auto &ph = info.dlpi_phdr[i];
        if (ph.p_type != PT_LOAD || (ph.p_flags & flags) != flags) continue;
        const uintptr_t start = info.dlpi_addr + ph.p_vaddr;
        if (address >= start && address - start <= ph.p_memsz &&
            size <= ph.p_memsz - (address - start)) return true;
    }
    return false;
}

int64_t signed_bits(uint32_t value, unsigned bits) {
    return int64_t(value ^ (uint32_t(1) << (bits - 1))) - (int64_t(1) << (bits - 1));
}
bool is_call(uint32_t word) { return (word & 0xfc000000) == 0x94000000; }
uintptr_t call_target(uintptr_t pc, uint32_t word) {
    return pc + signed_bits(word & 0x03ffffff, 26) * 4;
}
bool is_end(uint32_t word) {
    return (word & 0xfffffc1f) == 0xd65f0000 || // RET
           (word & 0xfffffc1f) == 0xd61f0000 || // BR
           (word & 0xfc000000) == 0x14000000;   // B (outlined tail)
}

struct Function {
    uintptr_t start{};
    std::span<const uint32_t> code;
    explicit operator bool() const { return !code.empty(); }
};

struct Image {
    const dl_phdr_info &info;
    std::vector<uintptr_t> starts;

    bool initialize() {
        for (size_t i = 0; i < info.dlpi_phnum; ++i) {
            const auto &ph = info.dlpi_phdr[i];
            if (ph.p_type != PT_GNU_EH_FRAME) continue;
            const uintptr_t header = info.dlpi_addr + ph.p_vaddr;
            if (ph.p_memsz < 12 || !mapped(info, header, ph.p_memsz, PF_R)) return false;
            const auto p = reinterpret_cast<const uint8_t *>(header);
            // version=1, eh_frame=pcrel/sdata4, count=udata4, table=datarel/sdata4.
            if (read<uint32_t>(p) != 0x3b031b01) return false;
            const size_t count = read<uint32_t>(p + 8);
            if (count < 2 || count > (ph.p_memsz - 12) / 8) return false;
            starts.reserve(count);
            for (size_t j = 0; j < count; ++j) {
                const uintptr_t start = header + read<int32_t>(p + 12 + j * 8);
                if ((start & 3) != 0 || !mapped(info, start, 4, PF_R | PF_X) ||
                    (!starts.empty() && start <= starts.back())) return false;
                starts.push_back(start);
            }
            return true;
        }
        return false;
    }

    Function function(uintptr_t address) const {
        auto it = std::upper_bound(starts.begin(), starts.end(), address);
        if (it == starts.begin() || it == starts.end()) return {};
        const uintptr_t start = *(it - 1), end = *it;
        if (end - start > 65536 || !mapped(info, start, end - start, PF_R | PF_X)) return {};
        return {start, {reinterpret_cast<const uint32_t *>(start), (end - start) / 4}};
    }

    std::span<const uint32_t> instructions(uintptr_t address, size_t limit) const {
        const auto f = function(address);
        if (!f || (address & 3)) return {};
        const auto tail = f.code.subspan((address - f.start) / 4);
        return tail.first(std::min(limit, tail.size()));
    }
};

constexpr std::array<const char *, 6> kAnchors = {
    "HandleRecallNotifyInAndrSilence", "ReplaceToRecallMsg",
    "ReplaceToTracelessRecallMsgByNotify", "HandleRecallMsgNotify",
    "OnRecallMsgNotifyWithLocalRecord",
    "[Msg] on grp recall nfy ! frm:{} to:{}, pr:{} seq:{} rd:{} t:{} op_uid:{} op_t:{} is_off:{} trl_flag:{} is_andr_silence:{}"
};

bool locate_functions(const Image &image, std::array<Function, kAnchors.size()> &functions) {
    std::array<std::vector<uintptr_t>, kAnchors.size()> strings;
    for (size_t i = 0; i < image.info.dlpi_phnum; ++i) {
        const auto &ph = image.info.dlpi_phdr[i];
        if (ph.p_type != PT_LOAD || !(ph.p_flags & PF_R)) continue;
        const auto begin = reinterpret_cast<const char *>(image.info.dlpi_addr + ph.p_vaddr);
        for (size_t a = 0; a < kAnchors.size(); ++a) {
            const size_t size = std::strlen(kAnchors[a]) + 1;
            if (size > ph.p_filesz) continue;
            const char *p = begin, *end = begin + ph.p_filesz - size + 1;
            while (p < end) {
                p = static_cast<const char *>(std::memchr(p, kAnchors[a][0], end - p));
                if (p == nullptr) break;
                if ((p == begin || p[-1] == 0) && std::memcmp(p, kAnchors[a], size) == 0)
                    strings[a].push_back(reinterpret_cast<uintptr_t>(p));
                ++p;
            }
        }
    }
    for (const auto &s : strings) if (s.empty()) return false;
    for (size_t i = 0; i < image.info.dlpi_phnum; ++i) {
        const auto &ph = image.info.dlpi_phdr[i];
        if (ph.p_type != PT_LOAD || (ph.p_flags & (PF_R | PF_X)) != (PF_R | PF_X)) continue;
        if (ph.p_vaddr & 3) return false;
        const auto words = reinterpret_cast<const uint32_t *>(image.info.dlpi_addr + ph.p_vaddr);
        const size_t count = ph.p_filesz / 4;
        for (size_t j = 0; j < count; ++j) {
            const uint32_t w = words[j];
            if ((w & 0x9f000000) != 0x90000000) continue; // ADRP
            const uintptr_t pc = reinterpret_cast<uintptr_t>(words + j);
            const uint32_t imm = ((w >> 5) & 0x7ffff) * 4 + ((w >> 29) & 3);
            const uintptr_t page = (pc & ~uintptr_t(4095)) + signed_bits(imm, 21) * 4096;
            for (size_t a = 0; a < strings.size(); ++a) for (const auto string : strings[a]) {
                if (page != (string & ~uintptr_t(4095))) continue;
                for (size_t k = j + 1; k < std::min(j + 17, count); ++k) {
                    const uint32_t add = words[k];
                    if ((add & 0xffc00000) != 0x91000000 || ((add >> 5) & 31) != (w & 31) ||
                        ((add >> 10) & 4095) != (string & 4095)) continue;
                    const auto f = image.function(pc);
                    if (!f || (functions[a] && functions[a].start != f.start)) return false;
                    functions[a] = f; // Multiple log references in one function are expected.
                }
            }
        }
    }
    for (const auto &f : functions) if (!f) return false;
    return true;
}

bool has(std::span<const uint32_t> code, uint32_t word) {
    return std::find(code.begin(), code.end(), word) != code.end();
}

bool verify_worker(const Image &image, const Function &worker, uintptr_t &query, const char *&error) {
    error = "missing or ambiguous account parameter 5003";
    size_t qi = worker.code.size();
    for (size_t i = 0; i < worker.code.size(); ++i) if (worker.code[i] == 0x52827168) {
        if (qi != worker.code.size()) return false;
        qi = i; // MOV W8,#5003
    }
    if (qi < 26 || qi == worker.code.size()) return false;
    error = "unsupported normalized notify construction";
    // Normalized RecallNotify construction: peer at base, sender +48,
    // recipient +60, operator +80, seq +20, extra +a8; session is manager+8.
    // Ignore the ADD X23 literal address, which follows ADRP in every corpus build.
    constexpr uint32_t construction[] = {
        0x9106a3e8, 0x9101210a, 0x91018109, 0x9109c3f9, 0x52800036,
        0xa902abe9, 0x91020118, 0x91008109, 0x9102a108, 0xf9002fe9,
        0xf90007e8, 0xf90023f5, 0xf90013f8, 0xf9402fe8, 0x6f00e400,
        0x910022a0
    };
    std::vector<uint32_t> setup;
    for (size_t i = qi - 24; i < qi; ++i) {
        const auto w = worker.code[i];
        if (is_call(w) || (w & 0x9f000000) == 0x90000000 ||
            (w & 0xffc003ff) == 0x910002f7) continue; // ADD X23,X23,#literal
        setup.push_back(w);
    }
    if (setup.size() < std::size(construction) ||
        !std::equal(std::begin(construction), std::end(construction), setup.begin())) return false;
    // These stores tie the chat/operator fields to that same stack object.
    if (!has(worker.code, 0x390703f6) || !has(worker.code, 0xb90223ff)) return false;
    error = "unsupported native session query arguments";
    size_t call = qi + 1;
    while (call < std::min(qi + 13, worker.code.size()) && !is_call(worker.code[call])) ++call;
    if (call == std::min(qi + 13, worker.code.size())) return false;
    const auto args = worker.code.subspan(qi + 1, call - qi - 1);
    if (!has(args, 0x52800202) || !has(args, 0x52800023)) return false; // W2=0x10,W3=1
    query = call_target(worker.start + call * 4, worker.code[call]);
    const auto qf = image.function(query);
    if (!qf || qf.start != query) return false;
    std::vector<uint32_t> query_code(qf.code.begin(), qf.code.end());
    for (size_t i = 0; i < qf.code.size(); ++i) if (is_call(qf.code[i])) {
        const auto helper = image.instructions(call_target(query + i * 4, qf.code[i]), 32);
        for (const auto w : helper) {
            query_code.push_back(w);
            if (is_end(w)) break;
        }
    }
    for (const auto w : {0xaa0503f3u, 0xaa0403f4u, 0xaa0303f5u, 0xaa0203f6u})
        if (!has(query_code, w)) return false;
    error = "unsupported libc++ string view ABI";
    size_t view_call = qi;
    while (view_call != 0 && !is_call(worker.code[view_call - 1])) --view_call;
    if (view_call == 0) return false;
    const auto view = image.instructions(call_target(worker.start + (view_call - 1) * 4,
                                                     worker.code[view_call - 1]), 7);
    constexpr uint32_t string_view[] = {
        0x39400008, 0xa940a40a, 0x7200011f, 0xd341fd08, 0x9a801520, 0x9a8a0101, 0xd65f03c0
    };
    if (view.size() != std::size(string_view) ||
        !std::equal(std::begin(string_view), std::end(string_view), view.begin())) return false;
    error = "unsupported normalized notify scalar fields";
    // PB scalar getters followed by stores prove seq/client/random/server UID/time.
    const uint32_t setters[] = {0x52800281, 0x52800061, 0x528000c1, 0x52800081, 0x528000a1};
    const unsigned expected[] = {0x1c8, 0x1d0, 0x1d8, 0x1e0, 0x1e8};
    for (size_t field = 0; field < std::size(setters); ++field) {
        bool found = false;
        for (size_t i = qi + 1; i < worker.code.size(); ++i) if (worker.code[i] == setters[field]) {
            for (size_t j = i + 1; j < std::min(i + 9, worker.code.size()); ++j) {
                const auto w = worker.code[j];
                if ((w & 0xffc003e0) != 0xf90003e0 || ((w & 31) != 0 && (w & 31) != 8)) continue;
                found = ((w >> 10) & 4095) * 8 == expected[field];
                break;
            }
            if (found) break;
        }
        if (!found) return false;
    }
    return true;
}

bool verify_getters(const Image &image, const Function &replace, const Function &traceless) {
    unsigned slots = 0;
    auto inspect = [&](std::span<const uint32_t> code) {
        for (const auto w : code) {
            if ((w & 0xffc003e0) != 0xf9400100) continue; // LDR Xt,[X8,#vtable offset]
            const auto offset = ((w >> 10) & 4095) * 8;
            const unsigned wanted[] = {0, 8, 0x18, 0x40, 0xa0};
            for (size_t j = 0; j < std::size(wanted); ++j) if (offset == wanted[j]) slots |= 1 << j;
        }
    };
    for (const auto &f : {replace, traceless}) {
        inspect(f.code);
        for (size_t i = 0; i < f.code.size(); ++i) if (is_call(f.code[i])) {
            const uintptr_t target = call_target(f.start + i * 4, f.code[i]);
            const auto helper = image.instructions(target, 32);
            size_t size = 0;
            while (size < helper.size()) if (is_end(helper[size++])) break;
            inspect(helper.first(size));
        }
    }
    return slots == 31;
}

struct Cache {
    uint32_t revision{};
    char id[40]{};
    uintptr_t offsets[kSites]{};
    uint32_t instructions[kSites]{};
    uintptr_t query{};
    uint32_t query_instruction{}, checksum{};
};
uint32_t checksum(const Cache &cache) {
    const auto p = reinterpret_cast<const unsigned char *>(&cache);
    uint32_t hash = 2166136261u;
    for (size_t i = 0; i < offsetof(Cache, checksum); ++i) hash = (hash ^ p[i]) * 16777619u;
    return hash;
}
} // namespace

bool read_kernel_build_id(const dl_phdr_info &info, char (&id)[41]) {
    id[0] = 0;
    for (size_t i = 0; i < info.dlpi_phnum; ++i) {
        const auto &ph = info.dlpi_phdr[i];
        if (ph.p_type != PT_NOTE) continue;
        auto p = reinterpret_cast<const uint8_t *>(info.dlpi_addr + ph.p_vaddr);
        auto end = p + ph.p_memsz;
        // Android's linker has validated the loaded program headers; note lengths
        // and cached RVAs remain external data, so validate those at this boundary.
        while (size_t(end - p) >= sizeof(Elf64_Nhdr)) {
            const auto note = read<Elf64_Nhdr>(p);
            p += sizeof(note);
            const size_t name = (size_t(note.n_namesz) + 3) & ~size_t(3);
            const size_t desc = (size_t(note.n_descsz) + 3) & ~size_t(3);
            if (name > size_t(end - p) || desc > size_t(end - p) - name) break;
            if (note.n_type == NT_GNU_BUILD_ID && note.n_namesz == 4 && note.n_descsz == 20 &&
                std::memcmp(p, "GNU", 4) == 0) {
                constexpr char hex[] = "0123456789abcdef";
                for (size_t j = 0; j < 20; ++j) {
                    id[j * 2] = hex[p[name + j] >> 4];
                    id[j * 2 + 1] = hex[p[name + j] & 15];
                }
                id[40] = 0;
                return true;
            }
            p += name + desc;
        }
    }
    return false;
}

bool scan_kernel(const dl_phdr_info &info, KernelProfile &result, const char *&error) {
    Image image{info, {}};
    error = "unsupported unwind table";
    if (!image.initialize()) return false;
    std::array<Function, kAnchors.size()> functions{};
    error = "missing or ambiguous recall log references";
    if (!locate_functions(image, functions)) return false;
    KernelProfile profile{};
    profile.version = "auto";
    constexpr uint32_t loads[kSites] = {0xf8420ea8, 0xf9400028, 0xf9400028};
    error = "unsupported recall branch/register convention";
    for (size_t site = 0; site < kSites; ++site) {
        const auto &f = functions[site];
        for (size_t i = 1; i < std::min(size_t(25), f.code.size()); ++i) {
            const auto w = f.code[i];
            if (f.code[i - 1] != loads[site] || (w & 0xff00001f) != 0xb4000008) continue;
            const uintptr_t pc = f.start + i * 4;
            const uintptr_t target = pc + signed_bits((w >> 5) & 0x7ffff, 19) * 4;
            if (profile.offsets[site] || target <= pc || target >= f.start + f.code.size() * 4) return false;
            profile.offsets[site] = pc - info.dlpi_addr;
            profile.instructions[site] = w;
        }
        if (!profile.offsets[site]) return false;
    }
    error = "unsupported notify/string/session layout";
    uintptr_t query{};
    if (!verify_worker(image, functions[3], query, error)) return false;
    error = "unsupported record getter layout";
    if (!verify_getters(image, functions[1], functions[2])) return false;
    profile.session_query = query - info.dlpi_addr;
    profile.query_instruction = read<uint32_t>(reinterpret_cast<const void *>(query));
    result = profile;
    error = nullptr;
    return true;
}

bool validate_kernel_targets(const dl_phdr_info &info, const KernelProfile &profile) {
    constexpr uint32_t loads[kSites] = {0xf8420ea8, 0xf9400028, 0xf9400028};
    for (size_t i = 0; i < kSites; ++i) {
        const uintptr_t offset = profile.offsets[i];
        if (offset < 4 || offset > UINTPTR_MAX - info.dlpi_addr || (offset & 3)) return false;
        const uintptr_t address = info.dlpi_addr + offset;
        if (!mapped(info, address - 4, 16, PF_R | PF_X)) return false;
        const auto p = reinterpret_cast<const uint32_t *>(address);
        if (p[0] != profile.instructions[i] || p[-1] != loads[i]) return false;
        if (i == 1 && p[2] != 0x2a0503f5) return false;
        if (i == 2 && p[1] != 0x39430449) return false;
    }
    if (profile.session_query > UINTPTR_MAX - info.dlpi_addr || (profile.session_query & 3)) return false;
    const uintptr_t query = info.dlpi_addr + profile.session_query;
    return mapped(info, query, 4, PF_R | PF_X) && profile.query_instruction == 0xd10243ff &&
           read<uint32_t>(reinterpret_cast<const void *>(query)) == profile.query_instruction;
}

bool read_kernel_cache(const char *path, const char *id, KernelProfile &profile) {
    auto file = std::fopen(path, "rb");
    if (file == nullptr) return false;
    Cache cache{};
    const bool complete = std::fread(&cache, sizeof(cache), 1, file) == 1 && std::fgetc(file) == EOF;
    std::fclose(file);
    if (!complete || cache.revision != kKernelScanRevision || std::memcmp(cache.id, id, 40) != 0 ||
        cache.checksum != checksum(cache)) return false;
    profile = {"cached", id, {}, {}, cache.query, cache.query_instruction};
    std::copy(std::begin(cache.offsets), std::end(cache.offsets), profile.offsets);
    std::copy(std::begin(cache.instructions), std::end(cache.instructions), profile.instructions);
    return true;
}

bool write_kernel_cache(const char *path, const char *id, const KernelProfile &profile) {
    Cache cache{};
    cache.revision = kKernelScanRevision;
    std::memcpy(cache.id, id, 40);
    std::copy(std::begin(profile.offsets), std::end(profile.offsets), cache.offsets);
    std::copy(std::begin(profile.instructions), std::end(profile.instructions), cache.instructions);
    cache.query = profile.session_query;
    cache.query_instruction = profile.query_instruction;
    cache.checksum = checksum(cache);
    const std::string temporary = std::string(path) + "." + std::to_string(getpid()) + ".tmp";
    auto file = std::fopen(temporary.c_str(), "wb");
    if (file == nullptr) return false;
    bool ok = std::fwrite(&cache, sizeof(cache), 1, file) == 1;
    if (std::fclose(file) != 0) ok = false;
    if (ok) ok = std::rename(temporary.c_str(), path) == 0;
    if (!ok) std::remove(temporary.c_str());
    return ok;
}
} // namespace tcqt::recall
