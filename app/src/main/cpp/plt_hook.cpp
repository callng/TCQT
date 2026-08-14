#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <link.h>
#include <pthread.h>
#include <sys/mman.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <set>
#include <string>
#include <vector>

#include "log.h"
#include "plt_hook.h"

namespace tcqt {

namespace {

// R_AARCH64_GLOB_DAT / R_AARCH64_JUMP_SLOT relocation types (from
// bits/elf_common.h): their GOT slots point at imported symbols.

// ══════════════════════════════════════════════════════════════════════════
// 一、Hook 定义区
//
// 新增一个 PLT hook：
//   1. 写一个与目标符号同签名的替换函数，内部通过真实指针调用原实现
//   2. 用 PLT_HOOK_SPEC 在 kDefaultPltHooks 表中追加一行
// ══════════════════════════════════════════════════════════════════════════

// 真实 fopen：安装时由引擎 dlsym 解析写入
FILE *(*real_fopen)(const char *, const char *) = nullptr;

// fopen 检测规避：libfekit.so 读取 /proc/self/smaps 时改喂 /dev/null
FILE *hook_fopen(const char *pathname, const char *mode) {
    if (pathname != nullptr && strncmp(pathname, "/proc/self/smaps", 16) == 0) {
        LOGD("plt_hook: fopen(%s) intercepted -> /dev/null", pathname);
        return real_fopen("/dev/null", mode);
    }
    return real_fopen(pathname, mode);
}

// 声明式注册宏：{ id, 库名子串, 符号, 替换函数, 真实指针 }
#define PLT_HOOK_SPEC(id, lib, symbol, hook_fn, real)                        \
    {                                                                        \
        (id), (lib), (symbol), reinterpret_cast<void *>(hook_fn),           \
                reinterpret_cast<void **>(&(real))                           \
    }

// 内置默认 hook 表
const PltHookSpec kDefaultPltHooks[] = {
    PLT_HOOK_SPEC("key1", "libfekit.so", "fopen", &hook_fopen, real_fopen),
};
const std::size_t kDefaultPltHookCount = sizeof(kDefaultPltHooks) / sizeof(kDefaultPltHooks[0]);

// ══════════════════════════════════════════════════════════════════════════
// 二、通用 PLT 引擎
//
// 发现机制说明：bionic 的 dl_iterate_phdr 是一次性遍历、不会在后续 dlopen
// 时主动回调，因此只靠后台轮询线程周期性扫描（50ms）发现目标库。
// ══════════════════════════════════════════════════════════════════════════

// ── 内存辅助（与 art_hook.cpp 中 WritableArtMethod 同一模式）──────────────────
inline uintptr_t strip_pac(uintptr_t addr) {
#ifdef __aarch64__
    register uintptr_t x16 __asm__("x16") = addr;
    __asm__ __volatile__("hint #32" : "+r"(x16));
    return x16;
#else
    return addr;
#endif
}

// 包含 `addr` 的映射的权限位，未映射返回 -1。
int get_prot_for_addr(uintptr_t addr) {
    addr = strip_pac(addr);
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

// ── 扫描状态 ────────────────────────────────────────────────────────────────

// 每个 spec 的运行时状态：真实符号解析 + 目标库出现标记
struct InternalSpec {
    PltHookSpec spec{};
    bool ready = false;  // 真实符号解析成功，可参与安装
    bool seen = false;   // 目标库已出现过（日志去重 / 轮询退出）
};

// 安装时快照；安装完成后只读（扫描回调与轮询线程并发读）
std::vector<InternalSpec> g_specs;
std::atomic<bool> g_installed{false};
// 尚未发现目标库的 spec 数；归零即全部命中，轮询线程退出
std::atomic<std::size_t> g_pending{0};

std::mutex g_scan_mutex;
std::set<std::string> g_seen_libs;  // 已见过的库名（日志去重）

// 扫描参数：log_new —— 本次扫描是否记录新出现的库名（安装时的初始快照不打）
struct ScanContext {
    bool log_new = false;
};

int scan_cb(dl_phdr_info *info, size_t /*size*/, void *data) {
    const char *name = info->dlpi_name;
    if (name == nullptr || name[0] == '\0') return 0;
    auto *ctx = static_cast<ScanContext *>(data);

    std::lock_guard<std::mutex> lock(g_scan_mutex);

    // 匹配每个已注册 spec：目标库出现即改写其 GOT 槽位
    for (InternalSpec &is : g_specs) {
        if (!is.ready) continue;  // 真实符号未解析成功，跳过（避免空指针）
        if (strstr(name, is.spec.lib) == nullptr) continue;
        const bool first = !is.seen;
        is.seen = true;
        const int n = patch_got_symbol(info, is.spec.symbol, is.spec.hook_fn);
        if (first) {
            if (g_pending.load(std::memory_order_relaxed) > 0) {
                g_pending.fetch_sub(1, std::memory_order_relaxed);
            }
            if (n > 0) {
                LOGI("plt_hook: found %s (base=%#lx), patched %d %s GOT slot(s)", name,
                     static_cast<uintptr_t>(info->dlpi_addr), n, is.spec.symbol);
            } else {
                LOGW("plt_hook: found %s but no %s GOT slot", name, is.spec.symbol);
            }
        }
    }

    // 库名去重：新出现的库打 DEBUG 日志，便于发现目标库或其变体名
    if (g_seen_libs.insert(name).second && ctx->log_new) {
        LOGD("plt_hook: lib loaded: %s", name);
    }
    return 0;
}

// 轮询线程：dl_iterate_phdr 是一次性遍历、不会在后续 dlopen 时回调，周期
// 扫描作为唯一发现机制（50ms 粒度对 MSF 启动期的目标库足够及时）
void *poller_main(void *) {
    constexpr int kPollIntervalMs = 50;
    constexpr int kMaxPolls = 6000;  // 最长约 5 分钟
    ScanContext ctx{true};
    int polls = 0;
    while (g_pending.load(std::memory_order_relaxed) > 0 && polls < kMaxPolls) {
        dl_iterate_phdr(scan_cb, &ctx);
        usleep(static_cast<useconds_t>(kPollIntervalMs) * 1000);
        ++polls;
    }
    const std::size_t pending = g_pending.load(std::memory_order_relaxed);
    if (pending > 0) {
        LOGW("plt_hook: %zu target lib(s) never appeared within %d s, giving up",
             pending, kPollIntervalMs * kMaxPolls / 1000);
    }
    return nullptr;
}

}  // namespace

void install_plt_hooks(const PltHookSpec *specs, std::size_t count) {
    if (specs == nullptr || count == 0) return;
    if (g_installed.load(std::memory_order_acquire)) return;

    // 解析每个 spec 的真实符号；解析失败则跳过该 spec（缺 real 会导致替换
    // 函数调用空指针）。此时尚无并发（轮询线程在最后才创建）
    g_specs.clear();
    g_specs.reserve(count);
    std::size_t pending = 0;
    for (std::size_t i = 0; i < count; ++i) {
        InternalSpec is;
        is.spec = specs[i];
        if (is.spec.real != nullptr && is.spec.symbol != nullptr) {
            void *real = dlsym(RTLD_DEFAULT, is.spec.symbol);
            if (real != nullptr) {
                *is.spec.real = real;
                is.ready = true;
                ++pending;
            } else {
                LOGE("plt_hook: dlsym(%s) failed, spec skipped", is.spec.symbol);
            }
        }
        g_specs.push_back(std::move(is));
    }
    if (pending == 0) {
        LOGE("plt_hook: no spec ready, abort install");
        g_specs.clear();
        return;
    }
    g_pending.store(pending, std::memory_order_relaxed);
    g_installed.store(true, std::memory_order_release);

    // 初始快照：目标库若已加载则直接 patch；未加载的交给轮询线程发现
    ScanContext ctx{false};
    dl_iterate_phdr(scan_cb, &ctx);
    LOGI("plt_hook: %zu hook spec(s) armed, poller started", pending);

    pthread_t tid;
    if (pthread_create(&tid, nullptr, poller_main, nullptr) != 0) {
        LOGE("plt_hook: pthread_create failed");
        return;
    }
    pthread_detach(tid);
}

const PltHookSpec *default_plt_hooks() {
    return kDefaultPltHooks;
}

std::size_t default_plt_hook_count() {
    return kDefaultPltHookCount;
}

void install_default_plt_hooks(const std::vector<std::string> &disabled_ids) {
    // 按 id 过滤出本次要安装的 hook（默认全部启用）
    std::vector<PltHookSpec> enabled;
    enabled.reserve(kDefaultPltHookCount);
    for (const auto & spec : kDefaultPltHooks) {
        const bool disabled = std::find(disabled_ids.begin(), disabled_ids.end(), spec.id) !=
                              disabled_ids.end();
        if (!disabled) enabled.push_back(spec);
    }
    if (enabled.empty()) {
        LOGW("plt_hook: all default hooks disabled, nothing to install");
        return;
    }
    install_plt_hooks(enabled.data(), enabled.size());
}

}  // namespace tcqt
