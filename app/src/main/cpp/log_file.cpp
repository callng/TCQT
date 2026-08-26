#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <sys/time.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <mutex>
#include <string>

#include "log.h"
#include "log_file.h"

namespace tcqt {

namespace {

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
    char line[1024];
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
        if (g_log_fd >= 0) {
            write_all(g_log_fd, line, len);
            g_written_bytes += len;
            // 会话内也轮转：超 1MB 时把当前文件改为 log.1.txt，继续写新文件
            if (g_written_bytes > LOG_MAX_BYTES) rotate_log_locked();
        }
    }
}

}  // namespace tcqt
