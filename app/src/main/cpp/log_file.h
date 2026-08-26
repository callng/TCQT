#pragma once

#include <string>

namespace tcqt {

// 初始化本地文件日志：日志追加写入 `path`（自动创建父目录）。单文件上限
// 1MB，超出后轮转为 `path.1`（仅保留上一代），新日志继续写 `path`
// 可在任意进程早期调用一次
void log_file_init(const std::string &path);

// 写一条日志：写入日志文件。LOG* 宏调用
void tcqt_log_write(int level, const char *fmt, ...)
        __attribute__((format(printf, 2, 3)));

}  // namespace tcqt
