#include "zygisk/plt/plt_hook.h"

#include <algorithm>
#include <cstdio>
#include <cstring>

#include "zygisk/logging/log.h"

namespace tcqt::zygisk {
namespace {

// Add same-signature replacements here and register them in kDefaultPltHooks.

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
#define PLT_HOOK_SPEC(id, lib, symbol, hook_fn, real)                               \
    {                                                                               \
        (id), (lib), (symbol), reinterpret_cast<void *>(hook_fn),                   \
                reinterpret_cast<void **>(&(real))                                  \
    }

// 内置默认 hook 表
const PltHookSpec kDefaultPltHooks[] = {
    PLT_HOOK_SPEC("key1", "libfekit.so", "fopen", &hook_fopen, real_fopen),
};
const std::size_t kDefaultPltHookCount = sizeof(kDefaultPltHooks) / sizeof(kDefaultPltHooks[0]);

#undef PLT_HOOK_SPEC

}  // namespace

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
    for (const auto &spec : kDefaultPltHooks) {
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

}  // namespace tcqt::zygisk
