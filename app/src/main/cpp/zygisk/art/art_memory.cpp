#include "zygisk/art/art_memory.h"
#include <fcntl.h>
#include <cstdlib>
#include <string>
namespace tcqt::zygisk::art_detail {
uintptr_t strip_pac(uintptr_t addr) {
#ifdef __aarch64__
    // XPACLRI operates on LR and is a backwards-compatible hint on non-PAC
    // CPUs. HINT #32 is BTI, not XPACI, and never stripped pointer signatures.
    register uintptr_t lr __asm__("x30") = addr;
    __asm__ __volatile__("hint #7" : "+r"(lr));
    return lr;
#else
    return addr;
#endif
}

// Protection bits of the mapping containing `addr`, or -1 when unmapped.
int get_prot_for_addr(uintptr_t addr) {
    addr = strip_pac(addr);
    int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char buf[8192];
    ssize_t n;
    std::string data;
    while ((n = read(fd, buf, sizeof(buf))) > 0) data.append(buf, static_cast<size_t>(n));
    close(fd);
    if (n < 0) return -1;

    size_t pos = 0;
    while (pos < data.size()) {
        size_t eol = data.find('\n', pos);
        if (eol == std::string::npos) eol = data.size();
        const std::string line = data.substr(pos, eol - pos);
        pos = eol + 1;

        size_t dash = line.find('-');
        if (dash == std::string::npos) continue;
        uintptr_t start = strtoul(line.substr(0, dash).c_str(), nullptr, 16);
        size_t sp1 = line.find(' ', dash);
        if (sp1 == std::string::npos) continue;
        size_t sp2 = line.find(' ', sp1 + 1);
        if (sp2 == std::string::npos) continue;
        uintptr_t end = strtoul(line.substr(dash + 1, sp1 - dash - 1).c_str(), nullptr, 16);
        const std::string perms = line.substr(sp1 + 1, sp2 - sp1 - 1);
        if (addr >= start && addr < end) {
            int prot = 0;
            if (perms.find('r') != std::string::npos) prot |= PROT_READ;
            if (perms.find('w') != std::string::npos) prot |= PROT_WRITE;
            if (perms.find('x') != std::string::npos) prot |= PROT_EXEC;
            return prot;
        }
    }
    return -1;
}

// Whether `addr` points into executable memory (a plausible code pointer).
// NOTE: get_prot_for_addr() returns -1 for addresses that are NOT present in
// /proc/self/maps at all (unmapped, or a small non-pointer value like a dex
// method index). Such an address must NEVER be treated as executable:
// (-1 & PROT_EXEC) is nonzero, so this check must reject -1 explicitly.
bool is_executable_address(uintptr_t addr) {
    addr = strip_pac(addr);
    if (addr == 0) return false;
    int prot = get_prot_for_addr(addr);
    return prot >= 0 && (prot & PROT_EXEC) != 0;
}


} // namespace tcqt::zygisk::art_detail
