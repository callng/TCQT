#include "log_file.h"

#include <android/log.h>
#include <fcntl.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <sys/time.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <cerrno>
#include <cstdarg>
#include <cstdio>
#include <ctime>
#include <deque>
#include <mutex>
#include <string>
#include <vector>

namespace tcqt {
namespace {
std::mutex g_mutex;
std::string g_directory;
std::string g_process;
std::atomic<bool> g_debug {false};
struct Record { int level; std::string text; };
std::deque<Record> g_early;
size_t g_early_bytes = 0;
size_t g_early_dropped = 0;
constexpr size_t EARLY_MAX = 64 * 1024;
constexpr size_t MESSAGE_MAX = 32 * 1024;

bool write_all(int fd, const char *data, size_t len) {
    while (len > 0) {
        ssize_t n = write(fd, data, len);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return false;
        data += n;
        len -= static_cast<size_t>(n);
    }
    return true;
}

std::string header() {
    char model[PROP_VALUE_MAX] = "", sdk[PROP_VALUE_MAX] = "";
    __system_property_get("ro.product.model", model);
    __system_property_get("ro.build.version.sdk", sdk);
    struct utsname uts {};
    uname(&uts);
    return "# TCQT Zygisk | process=" + g_process + " | model=" + model +
           " | SDK=" + sdk + " | kernel=" + uts.release +
           " | pageSize=" + std::to_string(sysconf(_SC_PAGESIZE)) + "\n";
}

// A stable lock inode protects size checks + rotation across overlapping instances.
// Reopen each append so no writer retains a rotated inode.
void append_locked(const Record &record) {
    const bool detail = record.level < ANDROID_LOG_INFO;
    if (detail && !g_debug) return;
    std::string name = g_process;
    for (char &c : name) {
        if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
              (c >= '0' && c <= '9') || c == '_' || c == '-')) c = '_';
    }
    const std::string path = g_directory + "/" + name + (detail ? ".debug.log" : ".events.log");
    const off_t limit = detail ? 512 * 1024 : 1024 * 1024;
    const int backups = detail ? 1 : 3;
    int lock_fd = open((path + ".lock").c_str(), O_WRONLY | O_CREAT | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (lock_fd < 0) return;
    int result;
    do { result = flock(lock_fd, LOCK_EX); } while (result < 0 && errno == EINTR);
    if (result < 0) { close(lock_fd); return; }
    struct stat st {};
    if (stat(path.c_str(), &st) == 0 && st.st_size + static_cast<off_t>(record.text.size()) > limit) {
        for (int i = backups; i > 0; --i) {
            const std::string src = i == 1 ? path : path + "." + std::to_string(i - 1);
            const std::string dst = path + "." + std::to_string(i);
            rename(src.c_str(), dst.c_str());
        }
    }
    int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC | O_NOFOLLOW, 0600);
    bool ok = fd >= 0;
    if (ok) {
        if (fstat(fd, &st) == 0 && st.st_size == 0) {
            const auto info = header();
            ok = write_all(fd, info.data(), info.size());
        }
        ok = write_all(fd, record.text.data(), record.text.size()) && ok;
        if (record.level >= ANDROID_LOG_ERROR) fdatasync(fd);
        close(fd);
    }
    flock(lock_fd, LOCK_UN);
    close(lock_fd);
    if (!ok) __android_log_print(ANDROID_LOG_ERROR, "TCQT-Zygisk", "log append failed: %s errno=%d", path.c_str(), errno);
}
}  // namespace

void log_file_init(const std::string &directory, const std::string &process, bool debug) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_directory.empty() || directory.empty()) return;
    if (mkdir(directory.c_str(), 0700) != 0 && errno != EEXIST) {
        __android_log_print(ANDROID_LOG_ERROR, "TCQT-Zygisk", "cannot create log directory: %s errno=%d", directory.c_str(), errno);
        return;
    }
    g_directory = directory;
    g_process = process;
    g_debug = debug;
    for (const auto &record : g_early) append_locked(record);
    g_early.clear();
    g_early_bytes = 0;
    if (g_early_dropped) {
        append_locked({ANDROID_LOG_WARN, "# early log buffer overflow: dropped=" + std::to_string(g_early_dropped) + "\n"});
    }
}

bool log_debug_enabled() {
    return g_debug.load(std::memory_order_relaxed);
}

void tcqt_log_write(int level, const char *fmt, ...) {
    if (level < ANDROID_LOG_INFO && !g_debug.load(std::memory_order_relaxed)) return;
    va_list args, copy;
    va_start(args, fmt);
    va_copy(copy, args);
    const int needed = vsnprintf(nullptr, 0, fmt, copy);
    va_end(copy);
    if (needed < 0) { va_end(args); return; }
    const size_t size = std::min(static_cast<size_t>(needed), MESSAGE_MAX);
    std::vector<char> message(size + 1);
    vsnprintf(message.data(), message.size(), fmt, args);
    va_end(args);
    struct timeval tv {};
    gettimeofday(&tv, nullptr);
    struct tm tm {};
    localtime_r(&tv.tv_sec, &tm);
    char prefix[128];
    const char severity = level >= 2 && level <= 7 ? "VDIWEF"[level - 2] : '?';
    snprintf(prefix, sizeof(prefix), "%04d-%02d-%02d %02d:%02d:%02d.%03d %c pid=%d tid=%ld ",
             tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour, tm.tm_min, tm.tm_sec,
             static_cast<int>(tv.tv_usec / 1000), severity, getpid(), syscall(SYS_gettid));
    Record record {level, std::string(prefix) + message.data()};
    if (static_cast<size_t>(needed) > MESSAGE_MAX) record.text += " [truncated at 32 KiB]";
    record.text += '\n';
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_directory.empty()) {
        append_locked(record);
    } else if (level >= ANDROID_LOG_INFO) {
        while (!g_early.empty() && g_early_bytes + record.text.size() > EARLY_MAX) {
            g_early_bytes -= g_early.front().text.size();
            g_early.pop_front();
            ++g_early_dropped;
        }
        g_early_bytes += record.text.size();
        g_early.push_back(std::move(record));
    }
}
}  // namespace tcqt
