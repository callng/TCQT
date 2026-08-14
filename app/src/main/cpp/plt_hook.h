#pragma once

#include <cstddef>
#include <string>
#include <vector>

namespace tcqt {

// 一个 PLT/GOT hook 的声明式描述
//
// 新增一个 hook 只需两步：
//   1. 写一个与目标符号同签名的替换函数，内部通过 *real 调用真实实现
//   2. 在 plt_hook.cpp 的 kDefaultPltHooks 表中用 PLT_HOOK_SPEC 追加一行
// 库发现（后台轮询）、GOT 槽位改写、真实符号解析全部由安装引擎统一处理，与具体 hook 无关
struct PltHookSpec {
    const char *id;      // 唯一 id，对应 WebUI 标记 <宿主>.<id>.disable
    const char *lib;     // 目标库名子串（strstr 匹配），如 "libfekit.so"
    const char *symbol;  // 被替换的导入符号，如 "fopen"
    void *hook_fn;       // 替换函数地址（须与 symbol 同签名）
    void **real;         // 输出：真实函数地址（安装时由 dlsym 解析写入）
};

// 内置默认 hook 表（定义于 plt_hook.cpp，只读）zygisk_entry 依据它
// 逐个检查 <宿主>.<id>.disable 标记，构建本次进程要跳过的 hook 集合
const PltHookSpec *default_plt_hooks();
std::size_t default_plt_hook_count();

// 安装内置默认 hook 表，跳过 disabled_ids 中列出的 hook（按 id 匹配），
// 幂等。生产入口：zygisk_entry 在 MSF 进程、且 WebUI 开关开启时调用
void install_default_plt_hooks(const std::vector<std::string> &disabled_ids);

// 用自定义表安装（扩展入口）。安装后立即对已加载目标库打补丁，并启动
// 后台轮询发现，直到所有目标库都出现或超时。重复调用直接返回
void install_plt_hooks(const PltHookSpec *specs, std::size_t count);

}  // namespace tcqt
