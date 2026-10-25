#pragma once

#include <sys/types.h>

#include <cstdint>
#include <span>
#include <string>
#include <vector>

namespace tcqt {

constexpr uint64_t APK_MAX_BYTES = 256ULL * 1024 * 1024;

enum class InjectionTarget : uint32_t { QQ = 1, TIM = 2 };

struct InjectionState {
    bool module_disabled = false;
    bool scope_disabled = false;
    bool compat_mode = false;
    bool debug_logs = false;
    std::vector<uint8_t> disabled_hooks;
    std::string payload_hash;
    int apk_fd = -1;  // Owned by the caller after a successful request.
    uint32_t error_stage = 0;
};

struct CompanionPaths {
    std::string module_dir = "/data/adb/modules/zygisk_tcqt";
    std::string data_dir = "/data/adb/tcqt";
};

// The socket is borrowed. Neither function closes it. Requests contain only a
// whitelisted host and Android UID; filesystem paths never come from the client.
bool request_injection_state(int socket, InjectionTarget target, uid_t uid,
                             size_t hook_count, InjectionState &out);
bool handle_companion_request(int socket, std::span<const char *const> hook_ids,
                              const CompanionPaths &paths = {});
const char *companion_stage_name(uint32_t stage);

}  // namespace tcqt
