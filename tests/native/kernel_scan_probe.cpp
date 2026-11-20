// Runs the production resolver on unmodified, mapped ARM64 ELF files. No QQ hooks.
#include "../../app/src/main/cpp/recall/kernel_scan.h"
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <string>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc != 2 && argc != 3) return 2;
    const int fd = open(argv[1], O_RDONLY);
    struct stat st{};
    if (fd < 0 || fstat(fd, &st) != 0) return 3;
    const auto file = static_cast<const unsigned char *>(mmap(nullptr, st.st_size, PROT_READ, MAP_PRIVATE, fd, 0));
    close(fd);
    if (file == MAP_FAILED) return 3;
    const auto eh = reinterpret_cast<const Elf64_Ehdr *>(file);
    if (std::memcmp(eh->e_ident, ELFMAG, SELFMAG) || eh->e_machine != EM_AARCH64) return 4;
    const auto ph = reinterpret_cast<const Elf64_Phdr *>(file + eh->e_phoff);
    size_t size = 0;
    for (size_t i = 0; i < eh->e_phnum; ++i)
        if (ph[i].p_type == PT_LOAD) size = std::max(size, size_t(ph[i].p_vaddr + ph[i].p_memsz));
    const auto image = static_cast<unsigned char *>(mmap(nullptr, size, PROT_READ | PROT_WRITE,
                                                        MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    if (image == MAP_FAILED) return 3;
    for (size_t i = 0; i < eh->e_phnum; ++i)
        if (ph[i].p_type == PT_LOAD) std::memcpy(image + ph[i].p_vaddr, file + ph[i].p_offset, ph[i].p_filesz);
    dl_phdr_info info{};
    info.dlpi_addr = reinterpret_cast<uintptr_t>(image);
    info.dlpi_phdr = ph;
    info.dlpi_phnum = eh->e_phnum;
    const auto start = std::chrono::steady_clock::now();
    tcqt::recall::KernelProfile profile{};
    const char *error{};
    const bool ok = tcqt::recall::scan_kernel(info, profile, error) &&
                    tcqt::recall::validate_kernel_targets(info, profile);
    const auto us = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now() - start).count();
    if (ok) std::printf("%zx %zx %zx %x %x %x %zx %x %lld\n", profile.offsets[0],
        profile.offsets[1], profile.offsets[2], profile.instructions[0], profile.instructions[1],
        profile.instructions[2], profile.session_query, profile.query_instruction, static_cast<long long>(us));
    else std::printf("FAIL %s\n", error ? error : "target validation");
    int failures = ok ? 0 : 1;
    if (ok && argc == 3 && std::strcmp(argv[2], "--checks") == 0) {
        using namespace tcqt::recall;
        auto test = [&](const char *name, bool condition) {
            std::printf("%s %s\n", condition ? "PASS" : "FAIL", name);
            if (!condition) ++failures;
        };
        auto resolve = [&] {
            KernelProfile output{};
            return scan_kernel(info, output, error) && validate_kernel_targets(info, output);
        };
        char id[41]{};
        test("loaded ELF Build ID readable", read_kernel_build_id(info, id));
        auto word = reinterpret_cast<uint32_t *>(image + profile.offsets[1] + 8);
        const auto saved = *word;
        *word = 0x2a0403f5; // MOV W21,W4, incompatible by_notify parameter.
        test("changed W5 convention rejected", !resolve());
        *word = saved;
        constexpr uint32_t construction[] = {0xa902abe9, 0x91020118, 0x91008109, 0x9102a108};
        const auto words = reinterpret_cast<uint32_t *>(image);
        const auto end = words + size / 4;
        auto layout = std::search(words, end, std::begin(construction), std::end(construction));
        test("notify constructor fixture found", layout != end);
        if (layout != end) {
            layout[1] = 0x91024118; // operator offset changes from +80 to +90.
            test("changed operator layout rejected", !resolve());
            layout[1] = construction[1];
        }
        const char anchor[] = "ReplaceToRecallMsg";
        auto text = std::search(image, image + size, std::begin(anchor), std::end(anchor));
        test("recall name fixture found", text != image + size);
        if (text != image + size) {
            *text = '_';
            test("missing recall name rejected", !resolve());
            *text = 'R';
            // Add a reference in the unrelated session-query function. Candidates
            // must be unique functions, not merely the first string xref found.
            auto code = reinterpret_cast<uint32_t *>(image + profile.session_query);
            const uint32_t original[] = {code[0], code[1]};
            const auto delta = (int64_t(reinterpret_cast<uintptr_t>(text) & ~uintptr_t(4095)) -
                                int64_t(reinterpret_cast<uintptr_t>(code) & ~uintptr_t(4095))) / 4096;
            const auto imm = uint32_t(delta) & 0x1fffff;
            code[0] = 0x90000000 | ((imm & 3) << 29) | ((imm >> 2) << 5);
            code[1] = 0x91000000 | ((reinterpret_cast<uintptr_t>(text) & 4095) << 10);
            test("ambiguous function references rejected", !resolve() && error &&
                 std::strstr(error, "ambiguous") != nullptr);
            std::copy(std::begin(original), std::end(original), code);
        }
        for (size_t i = 0; i < info.dlpi_phnum; ++i) if (ph[i].p_type == PT_GNU_EH_FRAME) {
            auto count = reinterpret_cast<uint32_t *>(image + ph[i].p_vaddr + 8);
            const auto original = *count;
            *count = 0xffffffff;
            test("out-of-bounds unwind table rejected", !resolve());
            *count = original;
        }
        auto invalid = profile;
        invalid.offsets[0] = UINTPTR_MAX - 3;
        test("out-of-bounds cached RVA rejected", !validate_kernel_targets(info, invalid));
        test("restored kernel resolves", resolve());
        const char *directory = std::getenv("TMPDIR");
        std::string temporary = std::string(directory ? directory : "/tmp") + "/tcqt-kernel-cache-XXXXXX";
        const int cache_fd = mkstemp(temporary.data());
        test("cache fixture created", cache_fd >= 0);
        if (cache_fd >= 0) {
            close(cache_fd);
            KernelProfile cached{};
            test("native cache round trip", write_kernel_cache(temporary.c_str(), id, profile) &&
                read_kernel_cache(temporary.c_str(), id, cached) && validate_kernel_targets(info, cached) &&
                std::equal(std::begin(profile.offsets), std::end(profile.offsets), cached.offsets) &&
                profile.session_query == cached.session_query);
            char other_id[41];
            std::memset(other_id, '0', 40);
            other_id[40] = 0;
            test("other Build ID cache rejected", !read_kernel_cache(temporary.c_str(), other_id, cached));
            auto file = std::fopen(temporary.c_str(), "r+b");
            const uint32_t old_revision = kKernelScanRevision + 1;
            std::fwrite(&old_revision, sizeof(old_revision), 1, file);
            std::fclose(file);
            test("other scanner revision rejected", !read_kernel_cache(temporary.c_str(), id, cached));
            write_kernel_cache(temporary.c_str(), id, profile);
            file = std::fopen(temporary.c_str(), "r+b");
            std::fseek(file, 48, SEEK_SET);
            std::fputc(0xff, file);
            std::fclose(file);
            test("corrupt cached targets rejected", !read_kernel_cache(temporary.c_str(), id, cached));
            file = std::fopen(temporary.c_str(), "wb");
            std::fputc(0, file);
            std::fclose(file);
            test("truncated cache rejected", !read_kernel_cache(temporary.c_str(), id, cached));
            std::remove(temporary.c_str());
            test("unwritable cache path leaves resolver usable",
                !write_kernel_cache((temporary + "/missing").c_str(), id, profile) && resolve());
        }
    }
    munmap(image, size);
    munmap(const_cast<unsigned char *>(file), st.st_size);
    return failures == 0 ? 0 : 1;
}
