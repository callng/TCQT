#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <string>
#include <vector>

#include "jni_bridge.h"
#include "log.h"
#include "payload.h"
#include "plt_hook.h"
#include "zygisk.hpp"

namespace tcqt {

constexpr const char *SHARED_PAYLOAD_APK = "/data/adb/tcqt/main.apk";
constexpr const char *SCOPE_DIR = "/data/adb/tcqt";
constexpr const char *ENTRY_CLASS = "com.owo233.tcqt.loader.zygisk.ZygiskEntry";
constexpr uint64_t APK_MAX_BYTES = 256ULL * 1024 * 1024;

enum class TargetApp { NONE, QQ, TIM };

TargetApp match_target(const std::string &process_name) {
    const char *prefixes[] = {"com.tencent.mobileqq", "com.tencent.tim"};

    for (size_t i = 0; i < 2; ++i) {
        const char *prefix = prefixes[i];
        if (process_name == prefix ||
            (process_name.rfind(prefix, 0) == 0 &&
             process_name[std::strlen(prefix)] == ':')) {
            return i == 0 ? TargetApp::QQ : TargetApp::TIM;
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

// Whether `path` exists as a regular file with the expected size.
bool path_has_size(const std::string &path, uint64_t expected) {
    if (expected == 0) return false;
    struct stat st {};
    if (stat(path.c_str(), &st) != 0) return false;
    return S_ISREG(st.st_mode) && static_cast<uint64_t>(st.st_size) == expected;
}

class TcqtZygisk : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api_ptr, JNIEnv *env_ptr) override {
        this->api = api_ptr;
        this->env = env_ptr;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        std::string nice_name = get_jstring(env, args->nice_name);
        TargetApp target = match_target(nice_name);
        if (target == TargetApp::NONE) {
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        int dir_fd = api->getModuleDir();
        if (dir_fd < 0) {
            LOGE("preAppSpecialize: getModuleDir failed");
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        // 主动禁用模块时 disable 文件 → 本次跳过注入
        // 卸载标记为 remove → 本次跳过注入
        // 宿主（QQ/TIM）下次启动即不再注入
        if (faccessat(dir_fd, "disable", F_OK, 0) == 0 ||
            faccessat(dir_fd, "remove", F_OK, 0) == 0) {
            LOGI("preAppSpecialize: module disabled by user, skip injection");
            close(dir_fd);
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        close(dir_fd);

        std::string scope_path = SCOPE_DIR;
        int user_id = args->uid / 100000;
        if (user_id != 0) {
            scope_path += "/user_";
            scope_path += std::to_string(user_id);
        }
        scope_path += target == TargetApp::QQ ? "/qq.disable" : "/tim.disable";
        if (access(scope_path.c_str(), F_OK) == 0) {
            LOGI("preAppSpecialize: %s disabled via WebUI (uid=%d), skip injection",
                 target == TargetApp::QQ ? "QQ" : "TIM", args->uid);
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        int apk_fd = open(SHARED_PAYLOAD_APK, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        if (apk_fd < 0) {
            LOGE("preAppSpecialize: open %s failed (errno=%d)", SHARED_PAYLOAD_APK, errno);
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        struct stat st {};
        if (fstat(apk_fd, &st) != 0 || !S_ISREG(st.st_mode) || st.st_size <= 0 ||
            static_cast<uint64_t>(st.st_size) > APK_MAX_BYTES) {
            LOGE("preAppSpecialize: invalid shared payload %s", SHARED_PAYLOAD_APK);
            close(apk_fd);
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        apk_fd_ = apk_fd;
        apk_size_ = static_cast<uint64_t>(st.st_size);
        process_name_ = std::move(nice_name);
        data_dir_ = get_jstring(env, args->app_data_dir);
        enabled_ = true;
        LOGI("preAppSpecialize: target %s (uid=%d)", process_name_.c_str(), args->uid);
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!enabled_) return;
        enabled_ = false;

        // 仅 MSF 进程：把 libfekit.so 的 fopen 调用中 /proc/self/smaps
        // 重定向到 /dev/null，过宿主进程检测（QQ/TIM 已由 match_target 过滤）。
        // 越早越好，需赶在 libfekit.so 加载之前注册回调。
        if (process_name_.size() >= 4 &&
            process_name_.compare(process_name_.size() - 4, 4, ":MSF") == 0) {
            install_fekit_fopen_hook();
        }

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

        // Copy the package into the app's data dir (only when missing or size
        // changed). ZygiskEntry.init later uses it to extract libdexkit.so.
        std::string apk_dst = target_dir + "/main.apk";
        if (!path_has_size(apk_dst, apk_size_)) {
            if (!copy_fd_to_path(apk_fd, apk_dst, APK_MAX_BYTES)) {
                LOGE("postAppSpecialize: failed to copy main.apk");
                close(apk_fd);
                return;
            }
        }
        close(apk_fd);

        // Read all classes*.dex from the copied package and build an
        // InMemoryDexClassLoader. An APK-path DexClassLoader is not usable
        // here: Android 10+ refuses to load dex from app-writable paths
        // ("Attempt to load writable dex file"), so the payload is loaded
        // from memory instead. kotlinx-coroutines' Main dispatcher discovery
        // (META-INF/services) is therefore unavailable; the Java side
        // compensates by hooking Dispatchers.getMain() in
        // ModuleLoader.installMainDispatcher().
        // Note: dex_bufs is intentionally never freed — the class loader
        // holds direct ByteBuffers referencing the underlying dex memory.
        auto *dex_bufs = new std::vector<std::vector<uint8_t>>();
        if (!read_dex_from_apk(env, apk_dst, dex_bufs)) {
            LOGE("postAppSpecialize: failed to read dex from %s", apk_dst.c_str());
            return;
        }
        jobject loader = build_dex_classloader(env, *dex_bufs);
        if (loader == nullptr) {
            LOGE("postAppSpecialize: failed to build InMemoryDexClassLoader");
            return;
        }

        // Load the entry class and register its natives.
        jclass entry = load_class_from_loader(env, loader, ENTRY_CLASS);
        if (entry == nullptr) {
            LOGE("postAppSpecialize: ZygiskEntry class not found");
            env->DeleteLocalRef(loader);
            return;
        }
        if (!register_entry_natives(env, loader)) {
            env->DeleteLocalRef(entry);
            env->DeleteLocalRef(loader);
            return;
        }

        // Call ZygiskEntry.init(processName, dataDir, apkPath).
        jmethodID init = env->GetStaticMethodID(
                entry, "init", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        if (init == nullptr) {
            env->ExceptionClear();
            LOGE("postAppSpecialize: ZygiskEntry.init not found");
            env->DeleteLocalRef(entry);
            env->DeleteLocalRef(loader);
            return;
        }
        jstring j_process = env->NewStringUTF(process_name_.c_str());
        jstring j_data = env->NewStringUTF(data_dir_.c_str());
        jstring j_apk = env->NewStringUTF(apk_dst.c_str());
        if (j_process == nullptr || j_data == nullptr || j_apk == nullptr ||
            env->ExceptionCheck()) {
            env->ExceptionClear();
            LOGE("postAppSpecialize: string allocation failed");
            env->DeleteLocalRef(entry);
            env->DeleteLocalRef(loader);
            return;
        }
        env->CallStaticVoidMethod(entry, init, j_process, j_data, j_apk);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            LOGE("postAppSpecialize: ZygiskEntry.init failed");
        } else {
            LOGI("postAppSpecialize: ZygiskEntry.init completed for %s",
                 process_name_.c_str());
        }
        env->DeleteLocalRef(j_process);
        env->DeleteLocalRef(j_data);
        env->DeleteLocalRef(j_apk);
        env->DeleteLocalRef(entry);
        env->DeleteLocalRef(loader);
    }

    void preServerSpecialize(zygisk::ServerSpecializeArgs *) override {
        api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

private:
    zygisk::Api *api = nullptr;
    JNIEnv *env = nullptr;
    int apk_fd_ = -1;
    uint64_t apk_size_ = 0;
    bool enabled_ = false;
    std::string process_name_;
    std::string data_dir_;
};

}  // namespace tcqt

REGISTER_ZYGISK_MODULE(tcqt::TcqtZygisk)
