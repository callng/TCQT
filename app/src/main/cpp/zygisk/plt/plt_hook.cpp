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
#include <vector>

#include "zygisk/logging/log.h"
#include "zygisk/plt/plt_hook.h"
#include "zygisk/plt/plt_elf.h"

namespace tcqt::zygisk {

namespace {

// R_AARCH64_GLOB_DAT / R_AARCH64_JUMP_SLOT relocation types (from
// bits/elf_common.h): their GOT slots point at imported symbols.

// Discover loaded libraries with the existing 50 ms poller.

// ── 内存辅助（与 art_hook.cpp 中 WritableArtMethod 同一模式）──────────────────
// 包含 `addr` 的映射的权限位，未映射返回 -1。
// These are ELF load-bias + relocation offsets, not PAC-signed code pointers.
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
        if (prot_ < 0 || !(prot_ & PROT_READ)) {
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

    bool restore() {
        if (changed_) {
            if (mprotect(reinterpret_cast<void *>(page_), page_size_, prot_) != 0) {
                LOGE("plt_hook: restoring protection failed at %#lx (errno=%d)", page_, errno);
                return false;
            }
            changed_ = false;
        }
        return true;
    }
    ~ScopedWritablePage() { restore(); }

private:
    uintptr_t page_ = 0;
    size_t page_size_ = 0;
    int prot_ = 0;
    bool changed_ = false;
};

// 改写一个 GOT 槽位；已改写的槽位视为成功（幂等，可重复调用）。
bool patch_got_slot(uintptr_t slot, const char *sym, void *replacement) {
    if (slot == 0 || slot % alignof(void *) != 0 || replacement == nullptr) return false;
    ScopedWritablePage writable;
    if (!writable.acquire(slot)) return false;
    auto **address = reinterpret_cast<void **>(slot);
    if (__atomic_load_n(address, __ATOMIC_ACQUIRE) != replacement)
        __atomic_store_n(address, replacement, __ATOMIC_RELEASE);
    if (__atomic_load_n(address, __ATOMIC_ACQUIRE) != replacement) {
        LOGE("plt_hook: %s GOT write-back mismatch at %#lx", sym, slot);
        return false;
    }
    return writable.restore();
}

// 扫描 `info` 对应库的动态重定位表，把所有指向符号 `name` 的 GOT 槽位改写为
// `replacement`。返回改写成功的槽位数。
int patch_got_symbol(dl_phdr_info *info, const char *name, void *replacement) {
    std::vector<uintptr_t> slots;
    if (!plt_detail::find_got_slots(*info, name, &slots)) return -1;
    int patched = 0;
    for (uintptr_t slot : slots) {
        if (!patch_got_slot(slot, name, replacement)) return -1;
        ++patched;
    }
    return patched;
}

// ── 扫描状态 ────────────────────────────────────────────────────────────────

// 每个 spec 的运行时状态：真实符号解析 + 目标库出现标记
struct InternalSpec {
    PltHookSpec spec{};
    std::string library, symbol;  // the caller's temporary table may go away
    bool ready = false;  // 真实符号解析成功，可参与安装
    bool seen = false;   // 目标库已出现过（日志去重）
    bool patched = false;
};

// 安装时快照；发布后只由持有 g_scan_mutex 的扫描回调更新状态。
std::vector<InternalSpec> g_specs;
std::atomic<bool> g_installed{false};
// 尚未成功打补丁的 spec 数；仅看到库名不能计为成功。
std::atomic<std::size_t> g_pending{0};

std::mutex g_install_mutex;
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
        if (strstr(name, is.library.c_str()) == nullptr) continue;
        const bool first = !is.seen;
        is.seen = true;
        const int n = patch_got_symbol(info, is.symbol.c_str(), is.spec.hook_fn);
        if (n > 0 && !is.patched) {
            is.patched = true;
            g_pending.fetch_sub(1, std::memory_order_relaxed);
            LOGI("plt_hook: found %s (base=%#lx), patched %d %s GOT slot(s)", name,
                 static_cast<uintptr_t>(info->dlpi_addr), n, is.symbol.c_str());
        } else if (first && n <= 0) {
            LOGW("plt_hook: found %s but %s %s, will retry", name, is.symbol.c_str(),
                 n == 0 ? "has no GOT slot" : "metadata/write failed");
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
        LOGW("plt_hook: %zu hook spec(s) still unpatched after %d s, giving up",
             pending, kPollIntervalMs * kMaxPolls / 1000);
    }
    return nullptr;
}

}  // namespace

void install_plt_hooks(const PltHookSpec *specs, std::size_t count) {
    if (specs == nullptr || count == 0) return;
    std::unique_lock<std::mutex> install_lock(g_install_mutex);
    if (g_installed.load(std::memory_order_acquire)) return;

    // 初始化互斥；真实符号和名称快照全部就绪后才发布给扫描回调。
    g_specs.clear();
    g_specs.reserve(count);
    std::size_t pending = 0;
    for (std::size_t i = 0; i < count; ++i) {
        if (specs[i].lib == nullptr || specs[i].lib[0] == '\0' ||
            specs[i].symbol == nullptr || specs[i].symbol[0] == '\0' ||
            specs[i].hook_fn == nullptr || specs[i].real == nullptr) {
            LOGE("plt_hook: invalid spec at index %zu, skipped", i);
            continue;
        }
        InternalSpec is;
        is.spec = specs[i];
        is.library = specs[i].lib;
        is.symbol = specs[i].symbol;
        void *real = dlsym(RTLD_DEFAULT, is.spec.symbol);
        if (real != nullptr && real != is.spec.hook_fn) {
            *is.spec.real = real;
            is.ready = true;
            ++pending;
        } else {
            LOGE("plt_hook: dlsym(%s) missing or resolves to replacement, spec skipped", is.spec.symbol);
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
    install_lock.unlock();

    // 初始快照：目标库若已加载则直接 patch；未加载的交给轮询线程发现
    ScanContext ctx{false};
    dl_iterate_phdr(scan_cb, &ctx);
    if (g_pending.load(std::memory_order_relaxed) == 0) return;

    pthread_t tid;
    if (pthread_create(&tid, nullptr, poller_main, nullptr) != 0) {
        LOGE("plt_hook: pthread_create failed");
        return;
    }
    pthread_detach(tid);
    LOGI("plt_hook: %zu hook spec(s) armed, poller started", pending);
}

}  // namespace tcqt::zygisk
