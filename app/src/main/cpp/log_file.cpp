#include <fcntl.h>
#include <csignal>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <sys/time.h>
#include <sys/uio.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>
#include <vector>

#include "elf_util.h"
#include "log.h"
#include "log_file.h"

namespace tcqt {

namespace {

// ── 环形缓冲（崩溃时落盘 128 行）────────────────────────────────────────────

constexpr size_t RING_LINES = 128;
constexpr size_t LINE_CAP = 384;

struct RingLine {
    char text[LINE_CAP];
};

RingLine g_ring[RING_LINES];
size_t g_ring_next = 0;
size_t g_ring_count = 0;

constexpr size_t HOOK_CALL_RING = 64;
uint64_t g_hook_calls[HOOK_CALL_RING] = {0};
size_t g_hook_calls_next = 0;

// ── 日志文件 ─────────────────────────────────────────────────────────────────

int g_log_fd = -1;
std::string g_log_path;
std::mutex g_log_mutex;

constexpr off_t LOG_MAX_BYTES = 1 << 20;
size_t g_written_bytes = 0;

void write_all(int fd, const char *data, size_t len) {
    while (len > 0) {
        ssize_t n = write(fd, data, len);
        if (n <= 0) break;
        data += n;
        len -= static_cast<size_t>(n);
    }
}

void write_device_header(int fd) {
    char manufacturer[128] = "";
    char model[128] = "";
    char device[128] = "";
    char sdk[32] = "";
    char release[32] = "";
    __system_property_get("ro.product.manufacturer", manufacturer);
    __system_property_get("ro.product.model", model);
    __system_property_get("ro.product.device", device);
    __system_property_get("ro.build.version.sdk", sdk);
    __system_property_get("ro.build.version.release", release);

    struct utsname uts {};
    uname(&uts);
    char buf[1024];
    int n = snprintf(buf, sizeof(buf),
                     "# ==== device info ====\n"
                     "# model=%s %s (%s)\n"
                     "# android=%s (SDK %s)\n"
                     "# kernel=%s %s\n"
                     "# pagesize=%ld\n"
                     "# ==== end ====\n",
                     manufacturer, model, device, release, sdk, uts.release, uts.version,
                     sysconf(_SC_PAGESIZE));
    if (n > 0) write_all(fd, buf, static_cast<size_t>(n));
}

// 轮转：log.txt → log.1.txt（覆盖旧备份，只保留上一代），重新打开新文件
// 调用方必须持有 g_log_mutex
void rotate_log_locked() {
    if (g_log_fd < 0 || g_log_path.empty()) return;
    const std::string backup = g_log_path + ".1";
    close(g_log_fd);
    unlink(backup.c_str());
    rename(g_log_path.c_str(), backup.c_str());
    g_log_fd = open(g_log_path.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
    g_written_bytes = 0;
}

void format_timestamp(char *out, size_t cap) {
    struct timeval tv {};
    gettimeofday(&tv, nullptr);
    struct tm tm {};
    localtime_r(&tv.tv_sec, &tm);
    snprintf(out, cap, "%02d-%02d %02d:%02d:%02d.%03d", tm.tm_mon + 1, tm.tm_mday,
             tm.tm_hour, tm.tm_min, tm.tm_sec, static_cast<int>(tv.tv_usec / 1000));
}

// 向环形缓冲压一行（不带锁，调用方持有 g_log_mutex）
void ring_push(const char *line) {
    strncpy(g_ring[g_ring_next].text, line, LINE_CAP - 1);
    g_ring[g_ring_next].text[LINE_CAP - 1] = '\0';
    g_ring_next = (g_ring_next + 1) % RING_LINES;
    if (g_ring_count < RING_LINES) g_ring_count++;
}

// ── 崩溃钩子（仅 async-signal-safe 操作）─────────────────────────────────────

std::atomic<bool> g_in_crash_handler{false};
struct sigaction g_old_actions[NSIG];

// ── 信号接管策略 ──────────────────────────────────────────────────────────
bool art_uffd_gc_enabled() {
    ArtLibrary art;
    if (find_art_library(&art)) {
        uintptr_t off = resolve_elf_symbol(art.path, "_ZN3art15gUseUserfaultfdE");
        if (off != 0) {
            uint8_t value = 0;
            struct iovec local = {&value, sizeof(value)};
            struct iovec remote = {reinterpret_cast<void *>(art.base + off), sizeof(value)};
            if (syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0) == 1) {
                return value != 0;
            }
        }
    }
    // 后备：属性明确为 true（libart 变体符号缺失 / 厂商 ART / LTO 等）
    char buf[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.dalvik.vm.enable_uffd_gc", buf);
    return strcmp(buf, "true") == 0;
}

// 把信号交给上一个处理器，且必须携带原始 siginfo/ucontext。ART 的 FaultManager
void chain_to_previous_handler(int sig, siginfo_t *si, void *uctx) {
    const struct sigaction &old = g_old_actions[sig];
    if ((old.sa_flags & SA_SIGINFO) && old.sa_sigaction != nullptr) {
        old.sa_sigaction(sig, si, uctx);
        return;
    }
    if (old.sa_handler == SIG_IGN) {
        return;
    }
    if (old.sa_handler != SIG_DFL && old.sa_handler != nullptr) {
        old.sa_handler(sig);
        return;
    }
    // 没有可链式调用的旧处理器：恢复默认动作后把信号发回当前线程，让内核
    // 以原始故障语义终止进程并产生 tombstone。用 tgkill 而非 raise()，明确
    // 定向到当前故障线程（raise 可能被派发到同进程的其他线程）
    struct sigaction dfl {};
    dfl.sa_handler = SIG_DFL;
    sigemptyset(&dfl.sa_mask);
    sigaction(sig, &dfl, nullptr);
    syscall(SYS_tgkill, getpid(), static_cast<pid_t>(syscall(SYS_gettid)), sig);
}

void append_hex(char *out, size_t *pos, size_t cap, uintptr_t value) {
    static const char digits[] = "0123456789abcdef";
    char buf[20];
    size_t n = 0;
    do {
        buf[n++] = digits[value & 0xf];
        value >>= 4;
    } while (value != 0);
    while (n > 0 && *pos + 1 < cap) out[(*pos)++] = buf[--n];
}

// 常见 si_code 的文本名（tombstone 里 SI_TKILL 是 TCQT 处理器 re-raise
// 造成的，原始 si_code 只在这里可见，用于区分真实内存故障与故意自杀）
const char *si_code_name(int sig, int code) {
    switch (code) {
        case SI_TKILL: return "SI_TKILL";
        case SI_USER: return "SI_USER";
        case SI_KERNEL: return "SI_KERNEL";
    }
    switch (sig) {
        case SIGBUS:
            switch (code) {
                case BUS_ADRALN: return "BUS_ADRALN";
                case BUS_ADRERR: return "BUS_ADRERR";
                case BUS_MCEERR_AR: return "BUS_MCEERR_AR";
                case BUS_MCEERR_AO: return "BUS_MCEERR_AO";
            }
            break;
        case SIGSEGV:
            switch (code) {
                case SEGV_MAPERR: return "SEGV_MAPERR";
                case SEGV_ACCERR: return "SEGV_ACCERR";
                case SEGV_BNDERR: return "SEGV_BNDERR";
                case SEGV_PKUERR: return "SEGV_PKUERR";
                case SEGV_MTEAERR: return "SEGV_MTEAERR";
                case SEGV_MTESERR: return "SEGV_MTESERR";
            }
            break;
    }
    return "?";
}

// 崩溃 PC 附近的指令字节（pc-8..pc+8），供离线反汇编定位触发指令。
// 用 process_vm_readv 读自身，避免信号处理器里触碰未映射内存。
void dump_code_around(int fd, uintptr_t pc) {
    if (pc == 0) return;
    unsigned char bytes[16] = {0};
    uintptr_t from = pc - 8;
    struct iovec local = {bytes, sizeof(bytes)};
    struct iovec remote = {reinterpret_cast<void *>(from), sizeof(bytes)};
    ssize_t n = syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0);
    if (n <= 0) {
        // 整段越界时退化为只读 pc 处 4 字节
        remote.iov_base = reinterpret_cast<void *>(pc);
        remote.iov_len = 4;
        local.iov_len = 4;
        n = syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0);
        if (n <= 0) return;
        from = pc;
    }
    char buf[96];
    size_t pos = 0;
    const char hdr[] = "code@0x";
    memcpy(buf, hdr, sizeof(hdr) - 1);
    pos = sizeof(hdr) - 1;
    append_hex(buf, &pos, sizeof(buf), from);
    buf[pos++] = ':';
    for (ssize_t i = 0; i < n; ++i) {
        buf[pos++] = ' ';
        char hi = static_cast<char>("0123456789abcdef"[bytes[i] >> 4]);
        char lo = static_cast<char>("0123456789abcdef"[bytes[i] & 0xf]);
        if (pos + 2 >= sizeof(buf)) break;
        buf[pos++] = hi;
        buf[pos++] = lo;
    }
    buf[pos++] = '\n';
    write_all(fd, buf, pos);
}

void crash_handler(int sig, siginfo_t *si, void *uctx) {
    // 防重入：崩溃发生在日志代码自身时，恢复默认动作并把信号定向发回当前
    // 线程（tgkill），保证进程按默认方式终止
    if (g_in_crash_handler.exchange(true)) {
        struct sigaction dfl {};
        dfl.sa_handler = SIG_DFL;
        sigemptyset(&dfl.sa_mask);
        sigaction(sig, &dfl, nullptr);
        syscall(SYS_tgkill, getpid(), static_cast<pid_t>(syscall(SYS_gettid)), sig);
        return;
    }

    // Best-effort suppression of ART implicit-null-check SIGSEGV. This is
    // intentionally heuristic：ART 编译/解释代码对 null 引用的访问会触发
    // SEGV_MAPERR 且 si_addr 为 0 或很小的字段偏移，ART FaultManager 捕获后
    // 修复故障点并正常抛出 NullPointerException——这是正常 Java 语义而不是进程崩溃
    // 静默透传给 ART（不写日志）：否则每个空指针检查都会写一个 CRASH 块
    // 刷屏（日志 1MB 轮转 + 信号上下文里文件 I/O）。是否真的可恢复仍由 ART
    // 用原始 ucontext 自行判断——只负责不刷屏、不 re-raise
    if (sig == SIGSEGV && si != nullptr && si->si_code == SEGV_MAPERR &&
        reinterpret_cast<uintptr_t>(si->si_addr) < 4096) {
        chain_to_previous_handler(sig, si, uctx);
        g_in_crash_handler.store(false);
        return;
    }

    uintptr_t pc = 0;
    uintptr_t regs[31] = {0};
    uintptr_t sp = 0;
    uintptr_t lr = 0;
#if defined(__aarch64__)
    if (uctx != nullptr) {
        const auto *mc = &reinterpret_cast<ucontext_t *>(uctx)->uc_mcontext;
        pc = static_cast<uintptr_t>(mc->pc);
        for (int i = 0; i < 31; ++i) {
            regs[i] = static_cast<uintptr_t>(mc->regs[i]);
        }
        sp = static_cast<uintptr_t>(mc->sp);
        lr = static_cast<uintptr_t>(mc->regs[30]);
    }
#endif

    int fd = g_log_fd;
    if (fd >= 0) {
        char buf[512];
        size_t pos = 0;
        const char header[] = "\n========== CRASH ==========\n";
        write_all(fd, header, sizeof(header) - 1);
        int si_code = (si != nullptr) ? si->si_code : 0;
        uintptr_t fault_addr =
                (si != nullptr) ? reinterpret_cast<uintptr_t>(si->si_addr) : 0;
        int n = snprintf(buf, sizeof(buf),
                         "signal %d (si_code=%d %s, si_addr=0x", sig, si_code,
                         si_code_name(sig, si_code));
        write_all(fd, buf, static_cast<size_t>(n));
        pos = 0;
        append_hex(buf, &pos, sizeof(buf), fault_addr);
        buf[pos++] = ')';
        n = snprintf(buf + pos, sizeof(buf) - pos, " tid=%d pid=%d pc=0x",
                     static_cast<int>(syscall(SYS_gettid)), getpid());
        pos += static_cast<size_t>(n);
        append_hex(buf, &pos, sizeof(buf), pc);
        buf[pos++] = '\n';
        write_all(fd, buf, pos);

        // 崩溃指令字节（离线反汇编）
        dump_code_around(fd, pc);

        // 崩溃时刻寄存器（用于定位垃圾指针来源）
        const char reg_hdr[] = "regs:\n";
        write_all(fd, reg_hdr, sizeof(reg_hdr) - 1);
        for (int i = 0; i < 31; ++i) {
            snprintf(buf, sizeof(buf), "x%02d=", i);
            pos = strlen(buf);
            append_hex(buf, &pos, sizeof(buf), regs[i]);
            buf[pos++] = (i % 4 == 3 || i == 30) ? '\n' : ' ';
            write_all(fd, buf, pos);
        }
        snprintf(buf, sizeof(buf), "sp=0x");
        pos = strlen(buf);
        append_hex(buf, &pos, sizeof(buf), sp);
        buf[pos++] = ' ';
        write_all(fd, buf, pos);
        snprintf(buf, sizeof(buf), "lr=0x");
        pos = strlen(buf);
        append_hex(buf, &pos, sizeof(buf), lr);
        buf[pos++] = '\n';
        write_all(fd, buf, pos);

        // 环形缓冲：从最旧开始整段 dump（崩溃线程可能正持有写锁，
        // 这里不取锁，读到半行是可接受的
        const char body[] = "----- last log lines -----\n";
        write_all(fd, body, sizeof(body) - 1);
        size_t start = g_ring_count < RING_LINES ? 0 : g_ring_next;
        size_t count = g_ring_count < RING_LINES ? g_ring_count : RING_LINES;
        for (size_t i = 0; i < count; ++i) {
            const char *line = g_ring[(start + i) % RING_LINES].text;
            write_all(fd, line, strlen(line));
            write_all(fd, "\n", 1);
        }

        const char hook_hdr[] = "----- last hook calls (id, newest first) -----\n";
        write_all(fd, hook_hdr, sizeof(hook_hdr) - 1);
        char line[24];
        for (size_t i = 0; i < HOOK_CALL_RING; ++i) {
            // 从最新倒序输出
            size_t idx = (g_hook_calls_next + HOOK_CALL_RING - 1 - i) % HOOK_CALL_RING;
            uint64_t id = g_hook_calls[idx];
            if (id == 0) continue;
            int n = snprintf(line, sizeof(line), "%llu\n",
                             static_cast<unsigned long long>(id));
            if (n > 0) write_all(fd, line, static_cast<size_t>(n));
        }
        const char footer[] = "========== END ==========\n";
        write_all(fd, footer, sizeof(footer) - 1);
    }

    // 不得在这里 restore + raise 把原始故障上下文链式交给上一个
    // 处理器（ART 的 FaultManager / libsigchain dispatcher），使其能对可恢复的
    // ART 内部事件（隐式空指针、uffd GC 毒化页）修复 ucontext 后让进程继续
    // 对真正致命的故障则由其走默认动作终止进程并产生 tombstone
    chain_to_previous_handler(sig, si, uctx);
    g_in_crash_handler.store(false);
}

}  // namespace

void log_file_init(const std::string &path) {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    if (g_log_fd >= 0 || path.empty()) return;

    // 创建父目录（最多两级）
    size_t pos = path.find('/', path.find('/') + 1);
    while ((pos = path.find('/', pos + 1)) != std::string::npos) {
        std::string dir = path.substr(0, pos);
        mkdir(dir.c_str(), 0700);
    }

    int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
    if (fd < 0) {
        g_log_path.clear();
        return;
    }
    struct stat st {};
    if (fstat(fd, &st) == 0 && st.st_size > LOG_MAX_BYTES) {
        // 上次的日志已超上限：轮转为 log.1.txt（保留上一代），重新开始
        close(fd);
        const std::string backup = path + ".1";
        unlink(backup.c_str());
        rename(path.c_str(), backup.c_str());
        fd = open(path.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
        if (fd < 0) {
            g_log_path.clear();
            return;
        }
    }
    g_log_fd = fd;
    g_log_path = path;
    g_written_bytes = 0;
    // 新文件（或轮转后的空文件）开头写一次设备信息
    if (fstat(fd, &st) == 0 && st.st_size == 0) {
        write_device_header(fd);
    }
}

void tcqt_log_write(int level, const char *fmt, ...) {
    char line[LINE_CAP + 32];
    char ts[32];
    format_timestamp(ts, sizeof(ts));

    // android 日志级别：V=2 D=3 I=4 W=5 E=6 F=7
    const size_t idx = (level >= 2 && level <= 7) ? static_cast<size_t>(level - 2) : 4;
    static const char level_chars[] = "VDIWEF";

    va_list args;
    va_start(args, fmt);
    size_t len = static_cast<size_t>(
            snprintf(line, sizeof(line), "%s %c %d ", ts, level_chars[idx],
                     static_cast<int>(syscall(SYS_gettid))));
    len += static_cast<size_t>(
            vsnprintf(line + len, sizeof(line) - len, fmt, args));
    va_end(args);
    if (len >= sizeof(line)) len = sizeof(line) - 1;
    line[len++] = '\n';

    {
        std::lock_guard<std::mutex> lock(g_log_mutex);
        ring_push(line);
        if (g_log_fd >= 0) {
            write_all(g_log_fd, line, len);
            g_written_bytes += len;
            // 会话内也轮转：超 1MB 时把当前文件改为 log.1.txt，继续写新文件
            if (g_written_bytes > LOG_MAX_BYTES) rotate_log_locked();
        }
    }
}

void log_file_install_crash_handlers() {
    struct sigaction sa {};
    sa.sa_sigaction = crash_handler;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);

    // 检测只做一次：运行时读 libart gUseUserfaultfd（含属性兜底），避免
    // 重复解析 ELF / 读属性导致前后结果不一致
    const bool uffd_gc_enabled = art_uffd_gc_enabled();
    char sdk_buf[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.build.version.sdk", sdk_buf);
    const int sdk = atoi(sdk_buf);

    // Android 15+：ART 对 SIGSEGV 的内部 fault handling（隐式空指针检查）
    const bool art_owns_sigsegv = sdk >= 35;
    // UFFD GC：ART 可能用 SIGBUS 作为 userfaultfd fault 的恢复机制，
    // 一旦确认启用，则完全不碰 SIGBUS
    const bool art_owns_sigbus = uffd_gc_enabled;

    // SIGABRT 始终由模块接管（Java 致命异常等必然终止，用于记录日志）
    std::vector<int> sigs = {SIGABRT};
    if (!art_owns_sigsegv) sigs.push_back(SIGSEGV);
    if (!art_owns_sigbus) sigs.push_back(SIGBUS);
    for (int sig : sigs) {
        if (sigaction(sig, &sa, &g_old_actions[sig]) != 0) {
            g_old_actions[sig].sa_handler = SIG_DFL;
            g_old_actions[sig].sa_flags = 0;
            g_old_actions[sig].sa_sigaction = nullptr;
        }
    }
    LOGI("log_file: crash handlers installed "
         "(SIGABRT=%d SIGSEGV=%d SIGBUS=%d uffd_gc=%d sdk=%d)",
         1,
         art_owns_sigsegv ? 0 : 1,
         art_owns_sigbus ? 0 : 1,
         uffd_gc_enabled ? 1 : 0,
         sdk);
}

void log_file_note_hook_call(uint64_t hook_id) {
    if (hook_id == 0) return;
    g_hook_calls[g_hook_calls_next] = hook_id;
    g_hook_calls_next = (g_hook_calls_next + 1) % HOOK_CALL_RING;
}

}  // namespace tcqt
