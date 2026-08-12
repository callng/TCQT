#pragma once

#include <string>

namespace tcqt {

// 初始化本地文件日志：日志追加写入 `path`（自动创建父目录）。单文件上限
// 1MB，超出后轮转为 `path.1`（仅保留上一代），新日志继续写 `path`
// 可在任意进程早期调用一次；未初始化时 tcqt_log_write 只保留内存环形缓冲
void log_file_init(const std::string &path);

// 写一条日志：同时进入内存环形缓冲（崩溃时落盘）与日志文件。LOG* 宏调用
void tcqt_log_write(int level, const char *fmt, ...)
        __attribute__((format(printf, 2, 3)));

// 接管 SIGABRT/SIGSEGV/SIGBUS：崩溃瞬间把环形缓冲 + 信号信息写入日志文件
// 然后恢复原处理器并重新 raise，保证 tombstone / 宿主崩溃链不受影响
void log_file_install_crash_handlers();

// 记录一次 hook 调用
// 崩溃前最后执行的 hook）。hook_id 与 Java 侧 install 日志中的 id 对应
void log_file_note_hook_call(uint64_t hook_id);

}  // namespace tcqt
