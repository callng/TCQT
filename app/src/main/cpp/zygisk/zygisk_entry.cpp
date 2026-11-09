#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <array>
#include <cerrno>
#include <cctype>
#include <cstdio>
#include <cstring>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "zygisk/api/zygisk.hpp"
#include "zygisk/bootstrap.h"
#include "zygisk/injector_identity.h"
#include "zygisk/jni_bridge.h"
#include "zygisk/logging/log.h"
#include "zygisk/payload.h"
#include "zygisk/plt/plt_hook.h"
#include "zygisk/zygisk_companion.h"

namespace tcqt::zygisk {
namespace {

enum class TargetApp {
    NONE,
    QQ,
    TIM
};

struct TargetInfo {
    std::string_view package;
    TargetApp app;
};

constexpr std::array TARGETS{
    TargetInfo{"com.tencent.mobileqq", TargetApp::QQ},
    TargetInfo{"com.tencent.tim", TargetApp::TIM},
};

TargetApp match_target(std::string_view process_name) {
    for (const auto &[package, app] : TARGETS) {
        if (process_name == package) {
            return app;
        }

        if (process_name.size() > package.size() &&
            process_name.starts_with(package) &&
            process_name[package.size()] == ':') {
            return app;
        }
    }

    return TargetApp::NONE;
}

std::string get_jstring(JNIEnv *env, jstring str) {
    if (str == nullptr) return {};
    const char *chars = env->GetStringUTFChars(str, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(str, chars);
    return out;
}

void log_impl_ident(::zygisk::Api *api) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (fp != nullptr) {
        char line[512];
        int logged = 0;
        while (logged < 3 && fgets(line, sizeof(line), fp) != nullptr) {
            bool hit = false;
            for (const char *p = line; *p != '\0' && !hit; ++p) {
                const char needle[] = "zygisk";
                size_t i = 0;
                while (needle[i] != '\0' &&
                       std::tolower(static_cast<unsigned char>(p[i])) == needle[i]) {
                    ++i;
                }
                if (needle[i] == '\0') hit = true;
            }
            if (hit) {
                line[strcspn(line, "\n")] = '\0';
                LOGI("impl: %s", line);
                ++logged;
            }
        }
        fclose(fp);
    }
    uint32_t flags = api->getFlags();
    LOGI("impl: getFlags()=0x%x", flags);
}

void companion_handler(int socket) {
    const PltHookSpec *specs = default_plt_hooks();
    std::vector<const char *> hook_ids;
    for (size_t i = 0; i < default_plt_hook_count(); ++i) hook_ids.push_back(specs[i].id);
    if (!handle_companion_request(socket, hook_ids)) {
        LOGE("companion: request transport failed (errno=%d)", errno);
    }
    // The Zygisk framework owns the handler's socket.
}

class TcqtZygisk : public ::zygisk::ModuleBase {
public:
    void onLoad(::zygisk::Api *api_ptr, JNIEnv *env_ptr) override {
        this->api = api_ptr;
        this->env = env_ptr;
    }

    void preAppSpecialize(::zygisk::AppSpecializeArgs *args) override {
        std::string nice_name = get_jstring(env, args->nice_name);
        TargetApp target = match_target(nice_name);
        if (target == TargetApp::NONE) {
            api->setOption(::zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        log_impl_ident(api);
        LOGI("injector: %s", injector_identity);
        int socket = api->connectCompanion();
        if (socket < 0) {
            LOGE("preAppSpecialize: root companion unavailable for %s (uid=%d)",
                 nice_name.c_str(), args->uid);
            api->setOption(::zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        InjectionState state;
        const size_t hook_count = default_plt_hook_count();
        const auto host = target == TargetApp::QQ ? InjectionTarget::QQ : InjectionTarget::TIM;
        bool received = request_injection_state(socket, host, args->uid, hook_count, state);
        int error = errno;
        close(socket);
        if (!received) {
            LOGE("preAppSpecialize: companion %s failed for %s (uid=%d errno=%d: %s)",
                 companion_stage_name(state.error_stage), nice_name.c_str(),
                 args->uid, error, strerror(error));
            api->setOption(::zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        if (state.module_disabled || state.scope_disabled) {
            LOGI("preAppSpecialize: %s disabled by %s, skip injection (uid=%d)",
                 nice_name.c_str(), state.module_disabled ? "module marker" : "WebUI scope", args->uid);
            api->setOption(::zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        debug_logs_ = state.debug_logs;
        const PltHookSpec *hook_specs = default_plt_hooks();
        disabled_hook_ids_.clear();
        for (size_t i = 0; i < hook_count; ++i) {
            if (state.disabled_hooks[i]) {
                disabled_hook_ids_.push_back(hook_specs[i].id);
                LOGI("preAppSpecialize: PLT hook %s disabled via WebUI (%s uid=%d)",
                     hook_specs[i].id, nice_name.c_str(), args->uid);
            }
        }
        int apk_fd = state.apk_fd;
        if (fstat(apk_fd, &payload_stat_) != 0) {
            LOGE("preAppSpecialize: companion payload stat failed (errno=%d)", errno);
            close(apk_fd);
            api->setOption(::zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        payload_hash_ = std::move(state.payload_hash);
        LOGI("preAppSpecialize: companion ready for %s (uid=%d debug=%d payload=%s)",
             nice_name.c_str(), args->uid, debug_logs_, payload_hash_.c_str());
        if (payload_hash_.empty()) {
            LOGW("preAppSpecialize: payload hash missing, cache will be refreshed");
        }

        // 通知 Zygisk 实现保留该 fd：fork 路径下 ReZygisk 会在 pre 之后
        // 关闭所有未豁免的 fd（rz_sanitize_fds），不豁免则 post 阶段复制
        // main.apk 会因 fd 已关闭而失败。
        if (api->exemptFd(apk_fd)) {
            LOGI("preAppSpecialize: payload fd %d exempted", apk_fd);
        } else {
            LOGW("preAppSpecialize: exemptFd unavailable or rejected (fd %d)", apk_fd);
        }

        apk_fd_ = apk_fd;
        process_name_ = std::move(nice_name);
        data_dir_ = get_jstring(env, args->app_data_dir);
        enabled_ = true;
        LOGI("preAppSpecialize: target %s (uid=%d)", process_name_.c_str(), args->uid);
    }

    void postAppSpecialize(const ::zygisk::AppSpecializeArgs *) override {
        if (!enabled_) return;
        enabled_ = false;

        if (apk_fd_ < 0 || data_dir_.empty()) {
            LOGE("postAppSpecialize: incomplete state");
            if (apk_fd_ >= 0) close(apk_fd_);
            return;
        }
        int apk_fd = apk_fd_;
        apk_fd_ = -1;

        std::string target_dir = data_dir_ + "/files/.tcqt";
        if (!ensure_dir(target_dir)) {
            close(apk_fd);
            return;
        }

        // 本地文件日志：目录就绪后初始化
        log_file_init(target_dir + "/logs", process_name_, debug_logs_);
        LOGI("session start: process=%s pid=%d debug=%d payload=%s",
             process_name_.c_str(), getpid(), debug_logs_, payload_hash_.c_str());

        // install hooks
        if (process_name_.size() >= 4 &&
            process_name_.compare(process_name_.size() - 4, 4, ":MSF") == 0) {
            if (disabled_hook_ids_.size() >= default_plt_hook_count()) {
                LOGI("postAppSpecialize: all PLT hooks disabled by user, skip");
            } else {
                install_default_plt_hooks(disabled_hook_ids_);
            }
        }

        // 先校验 fd 仍指向 payload：个别实现（如未豁免 fd 的 fork 路径）可能
        // 在 pre 之后把它关掉，此时跳过复制、不 close 可能已被复用的 fd 号，
        // 后面改为从已有路径读 dex（main.apk 已存在时同样能注入）。
        std::string apk_dst = target_dir + "/main.apk";
        std::string hash_dst = target_dir + "/main.apk.sha256";
        struct stat st {};
        bool fd_ok = fstat(apk_fd, &st) == 0 && S_ISREG(st.st_mode) &&
                     st.st_dev == payload_stat_.st_dev && st.st_ino == payload_stat_.st_ino &&
                     st.st_size == payload_stat_.st_size;
        if (fd_ok) {
            std::string cached_hash;
            bool cached_hash_ok = read_text_file(hash_dst, cached_hash);

            bool payload_changed =
                    payload_hash_.empty() || !cached_hash_ok ||
                    payload_hash_ != cached_hash;

            if (payload_changed) {
                LOGI("postAppSpecialize: payload changed, updating cached main.apk");
                if (!copy_fd_to_path(apk_fd, apk_dst, APK_MAX_BYTES)) {
                    LOGE("postAppSpecialize: failed to copy main.apk");
                    close(apk_fd);
                    return;
                }
                if (!payload_hash_.empty()) {
                    if (!write_text_file_atomic(hash_dst, payload_hash_)) {
                        LOGE("postAppSpecialize: failed to update payload hash");
                        close(apk_fd);
                        return;
                    }
                    LOGI("postAppSpecialize: payload updated: %s", payload_hash_.c_str());
                } else {
                    unlink(hash_dst.c_str());
                    LOGW("postAppSpecialize: global payload hash missing, "
                         "cache refreshed without version file");
                }
            } else {
                LOGI("postAppSpecialize: payload unchanged: %s", payload_hash_.c_str());
            }
            close(apk_fd);
        } else {
            LOGW("postAppSpecialize: payload fd %d no longer valid, skip copy", apk_fd);
        }

        bootstrap_payload(env, apk_dst, process_name_, data_dir_);
    }

    void preServerSpecialize(::zygisk::ServerSpecializeArgs *) override {
        api->setOption(::zygisk::DLCLOSE_MODULE_LIBRARY);
    }

private:
    ::zygisk::Api *api = nullptr;
    JNIEnv *env = nullptr;
    int apk_fd_ = -1;
    struct stat payload_stat_ {};
    bool enabled_ = false;
    bool debug_logs_ = false;
    std::vector<std::string> disabled_hook_ids_;
    std::string process_name_;
    std::string data_dir_;
    std::string payload_hash_;
};

}  // namespace
}  // namespace tcqt::zygisk

REGISTER_ZYGISK_MODULE(tcqt::zygisk::TcqtZygisk)
REGISTER_ZYGISK_COMPANION(tcqt::zygisk::companion_handler)
