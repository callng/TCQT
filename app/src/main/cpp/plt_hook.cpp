#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <pthread.h>
#include <sys/mman.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <set>
#include <string>

#include "log.h"
#include "plt_hook.h"

namespace tcqt {

namespace {

// R_AARCH64_GLOB_DAT / R_AARCH64_JUMP_SLOT relocation types (from
// bits/elf_common.h): their GOT slots point at imported symbols.

// ── Hook 目标函数 ─────────────────────────────────────────────────────────────

FILE *(*real_fopen)(const char *, const char *) = nullptr;

FILE *hook_fopen(const char *pathname, const char *mode) {
    if (pathname != nullptr && strncmp(pathname, "/proc/self/smaps", 16) == 0) {
        LOGD("plt_hook: fopen(%s) intercepted -> /dev/null", pathname);
        return real_fopen("/dev/null", mode);
    }
    return real_fopen(pathname, mode);
}

int scan_cb(dl_phdr_info *info, size_t size, void *data);

// 扫描参数：log_new —— 本次扫描是否记录新出现的库名（安装时的初始快照不打）；
// dlopen_patches —— 本次扫描新装 dlopen 触发的库数（仅安装扫描时汇总成一行）。
struct ScanContext {
    bool log_new = false;
    int dlopen_patches = 0;
};

// dlopen 触发：接住已加载库对 dlopen 的调用，返回后立即重扫一遍，第一时间
// 发现 libfekit.so（bionic 的 dl_iterate_phdr 不会在库加载时主动回调）。
void *(*real_dlopen)(const char *, int) = nullptr;
void *(*real_android_dlopen_ext)(const char *, int, const void *) = nullptr;

void rescan_after_dlopen() {
    ScanContext ctx{true, 0};
    dl_iterate_phdr(scan_cb, &ctx);
}

void *hook_dlopen(const char *filename, int flags) {
    void *handle = real_dlopen(filename, flags);
    rescan_after_dlopen();
    return handle;
}

void *hook_android_dlopen_ext(const char *filename, int flags, const void *extinfo) {
    void *handle = real_android_dlopen_ext(filename, flags, extinfo);
    rescan_after_dlopen();
    return handle;
}

// ── 内存辅助（与 art_hook.cpp 中 WritableArtMethod 同一模式）──────────────────

// 包含 `addr` 的映射的权限位，未映射返回 -1。
int get_prot_for_addr(uintptr_t addr) {
    int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char buf[4096];
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

// 把包含 `addr` 的页改为可写，析构时恢复。
class ScopedWritablePage {
public:
    bool acquire(uintptr_t addr) {
        long page_size_long = sysconf(_SC_PAGESIZE);
        if (page_size_long <= 0) return false;
        page_size_ = static_cast<size_t>(page_size_long);
        page_ = addr - addr % page_size_;
        prot_ = get_prot_for_addr(page_);
        if (prot_ < 0) {
            LOGE("plt_hook: cannot read protection for %#lx", page_);
            return false;
        }
        if ((prot_ & PROT_WRITE) == 0) {
            if (mprotect(reinterpret_cast<void *>(page_), page_size_, prot_ | PROT_WRITE) != 0) {
                LOGE("plt_hook: mprotect failed at %#lx (errno=%d)", page_, errno);
                return false;
            }
            changed_ = true;
        }
        return true;
    }

    ~ScopedWritablePage() {
        if (changed_) {
            mprotect(reinterpret_cast<void *>(page_), page_size_, prot_);
        }
    }

private:
    uintptr_t page_ = 0;
    size_t page_size_ = 0;
    int prot_ = 0;
    bool changed_ = false;
};

// 改写一个 GOT 槽位；已改写的槽位视为成功（幂等，可重复调用）。
bool patch_got_slot(uintptr_t slot, const char *sym, void *replacement) {
    if (slot == 0) return false;
    void *current = *reinterpret_cast<void **>(slot);
    if (current == replacement) return true;

    ScopedWritablePage writable;
    if (!writable.acquire(slot)) return false;
    __atomic_store_n(reinterpret_cast<void **>(slot), replacement, __ATOMIC_RELEASE);
    if (*reinterpret_cast<void **>(slot) != replacement) {
        LOGE("plt_hook: %s GOT write-back mismatch at %#lx", sym, slot);
        return false;
    }
    return true;
}

// 扫描 `info` 对应库的动态重定位表，把所有指向符号 `name` 的 GOT 槽位改写为
// `replacement`。返回改写成功的槽位数。
int patch_got_symbol(dl_phdr_info *info, const char *name, void *replacement) {
    const uintptr_t base = info->dlpi_addr;

    const ElfW(Dyn) *dyn = nullptr;
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        if (info->dlpi_phdr[i].p_type == PT_DYNAMIC) {
            dyn = reinterpret_cast<const ElfW(Dyn) *>(base + info->dlpi_phdr[i].p_vaddr);
            break;
        }
    }
    if (dyn == nullptr) return false;

    const ElfW(Sym) *symtab = nullptr;
    const char *strtab = nullptr;
    size_t syment = sizeof(ElfW(Sym));
    const ElfW(Rela) *rela = nullptr;
    size_t relasz = 0;
    const ElfW(Rela) *jmp_rel = nullptr;
    size_t jmp_relsz = 0;

    for (const ElfW(Dyn) *d = dyn; d->d_tag != DT_NULL; ++d) {
        switch (d->d_tag) {
            case DT_SYMTAB:
                symtab = reinterpret_cast<const ElfW(Sym) *>(base + d->d_un.d_ptr);
                break;
            case DT_STRTAB:
                strtab = reinterpret_cast<const char *>(base + d->d_un.d_ptr);
                break;
            case DT_SYMENT:
                syment = d->d_un.d_val;
                break;
            case DT_RELA:
                rela = reinterpret_cast<const ElfW(Rela) *>(base + d->d_un.d_ptr);
                break;
            case DT_RELASZ:
                relasz = d->d_un.d_val;
                break;
            case DT_JMPREL:
                jmp_rel = reinterpret_cast<const ElfW(Rela) *>(base + d->d_un.d_ptr);
                break;
            case DT_PLTRELSZ:
                jmp_relsz = d->d_un.d_val;
                break;
            default:
                break;
        }
    }
    if (symtab == nullptr || strtab == nullptr) return false;

    auto patch_rela = [&](const ElfW(Rela) *rel, size_t sz) {
        int patched = 0;
        const size_t count = sz / sizeof(ElfW(Rela));
        for (size_t i = 0; i < count; ++i) {
            const uint32_t type = ELF64_R_TYPE(rel[i].r_info);
            if (type != R_AARCH64_JUMP_SLOT && type != R_AARCH64_GLOB_DAT) continue;
            const uint32_t sym_idx = ELF64_R_SYM(rel[i].r_info);
            // st_name is the first field of ElfW(Sym); index by the real
            // symbol entry size from DT_SYMENT.
            const uint32_t st_name = *reinterpret_cast<const uint32_t *>(
                    reinterpret_cast<const char *>(symtab) +
                    static_cast<size_t>(sym_idx) * syment);
            if (strcmp(strtab + st_name, name) != 0) continue;
            if (patch_got_slot(base + rel[i].r_offset, name, replacement)) patched++;
        }
        return patched;
    };

    int patched = 0;
    if (rela != nullptr) patched += patch_rela(rela, relasz);
    if (jmp_rel != nullptr) patched += patch_rela(jmp_rel, jmp_relsz);
    return patched;
}

// ── 扫描状态（scan_cb 都在 dl_iterate_phdr 回调内执行，用互斥锁串行化）──────

std::atomic<bool> g_fekit_seen{false};  // libfekit.so 出现过（轮询线程据此退出）
std::mutex g_scan_mutex;
std::set<uintptr_t> g_dlopen_patched;  // 已装 dlopen 触发的库（按加载基址去重）
std::set<std::string> g_seen_libs;     // 已见过的库名（日志去重）

int scan_cb(dl_phdr_info *info, size_t /*size*/, void *data) {
    const char *name = info->dlpi_name;
    if (name == nullptr || name[0] == '\0') return 0;
    auto *ctx = static_cast<ScanContext *>(data);

    std::lock_guard<std::mutex> lock(g_scan_mutex);

    const bool is_fekit = strstr(name, "libfekit.so") != nullptr;
    if (is_fekit) {
        const int n = patch_got_symbol(
                info, "fopen",
                reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(&hook_fopen)));
        if (!g_fekit_seen.exchange(true)) {
            if (n > 0) {
                LOGI("plt_hook: found %s (base=%#lx), patched %d fopen GOT slot(s)", name,
                     static_cast<uintptr_t>(info->dlpi_addr), n);
            } else {
                LOGW("plt_hook: found %s but no fopen GOT slot", name);
            }
        }
    }

    // dlopen 触发：按基址去重，避免每次扫描都重解析整张重定位表。
    if (g_dlopen_patched.insert(static_cast<uintptr_t>(info->dlpi_addr)).second) {
        patch_got_symbol(info, "android_dlopen_ext",
                         reinterpret_cast<void *>(
                                 reinterpret_cast<uintptr_t>(&hook_android_dlopen_ext)));
        patch_got_symbol(info, "dlopen",
                         reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(&hook_dlopen)));
        ctx->dlopen_patches++;
    }

    // 库名去重：初始快照只入集合不打日志，之后新出现的库才打（便于发现
    // libfekit.so 或其变体名）。
    if (!is_fekit && g_seen_libs.insert(name).second && ctx->log_new) {
        if (strstr(name, "fekit") != nullptr) {
            LOGI("plt_hook: fekit-like lib loaded (not matched): %s", name);
        } else {
            LOGD("plt_hook: lib loaded: %s", name);
        }
    }
    return 0;
}

// 轮询线程：dl_iterate_phdr 是一次性遍历、不会在后续 dlopen 时回调，周期
// 扫描作为兜底（dlopen 触发负责缩短正常路径上的发现延迟）。
void *poller_main(void *) {
    constexpr int kPollIntervalMs = 50;
    constexpr int kMaxPolls = 6000;  // 最长约 5 分钟
    ScanContext ctx{true, 0};
    for (int i = 0; i < kMaxPolls && !g_fekit_seen; ++i) {
        dl_iterate_phdr(scan_cb, &ctx);
        usleep(static_cast<useconds_t>(kPollIntervalMs) * 1000);
    }
    if (!g_fekit_seen) {
        LOGW("plt_hook: libfekit.so not seen within %d s, giving up",
             kPollIntervalMs * kMaxPolls / 1000);
    }
    return nullptr;
}

}  // namespace

void install_fekit_fopen_hook() {
    real_fopen = reinterpret_cast<FILE *(*)(const char *, const char *)>(
            dlsym(RTLD_DEFAULT, "fopen"));
    real_dlopen =
            reinterpret_cast<void *(*)(const char *, int)>(dlsym(RTLD_DEFAULT, "dlopen"));
    real_android_dlopen_ext = reinterpret_cast<void *(*)(const char *, int, const void *)>(
            dlsym(RTLD_DEFAULT, "android_dlopen_ext"));
    if (real_fopen == nullptr) {
        LOGE("plt_hook: dlsym(fopen) failed");
        return;
    }

    // 初始快照：给已加载库装 dlopen 触发；libfekit.so 若已加载则直接 patch。
    ScanContext ctx{false, 0};
    dl_iterate_phdr(scan_cb, &ctx);
    LOGI("plt_hook: dlopen trigger installed on %d libs", ctx.dlopen_patches);

    pthread_t tid;
    if (pthread_create(&tid, nullptr, poller_main, nullptr) != 0) {
        LOGE("plt_hook: pthread_create failed");
        return;
    }
    pthread_detach(tid);
    LOGI("plt_hook: fopen hook installed (dlopen trigger + poller)");
}

}  // namespace tcqt
