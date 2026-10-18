#pragma once

#include <string>

namespace tcqt {

// Per-process events (1 MiB x 4) and optional debug details (512 KiB x 2).
// Replays bounded pre-specialization events after the app log directory is ready.
void log_file_init(const std::string &directory, const std::string &process, bool debug);
bool log_debug_enabled();

// 写一条日志：写入日志文件。LOG* 宏调用
void tcqt_log_write(int level, const char *fmt, ...)
        __attribute__((format(printf, 2, 3)));

}  // namespace tcqt
