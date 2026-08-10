#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <string>
#include <vector>

#include "jni_bridge.h"
#include "log.h"
#include "payload.h"
#include "zygisk.hpp"

namespace tcqt {

constexpr const char *PAYLOAD_APK = "payload/tcqt.apk";
constexpr const char *DEX_LIST = "payload/dex.list";
constexpr const char *ENTRY_CLASS = "com.owo233.tcqt.loader.zygisk.ZygiskEntry";
constexpr uint64_t APK_MAX_BYTES = 256ULL * 1024 * 1024;
constexpr uint64_t DEX_MAX_BYTES = 64ULL * 1024 * 1024;

bool is_qq_or_tim(const std::string &process_name) {
    const char *prefixes[] = {"com.tencent.mobileqq", "com.tencent.tim"};
    for (const char *prefix : prefixes) {
        if (process_name == prefix) return true;
        if (process_name.rfind(prefix, 0) == 0 && process_name[std::strlen(prefix)] == ':')
            return true;
    }
    return false;
}

std::string get_jstring(JNIEnv *env, jstring str) {
    if (str == nullptr) return {};
    const char *chars = env->GetStringUTFChars(str, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(str, chars);
    return out;
}

// Size of a regular file inside the module dir (0 on failure/not regular).
uint64_t module_file_size(int dir_fd, const char *rel) {
    int fd = openat(dir_fd, rel, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return 0;
    struct stat st {};
    uint64_t size = 0;
    if (fstat(fd, &st) == 0 && S_ISREG(st.st_mode)) size = static_cast<uint64_t>(st.st_size);
    close(fd);
    return size;
}

// Whether `path` exists as a regular file with the expected size.
bool path_has_size(const std::string &path, uint64_t expected) {
    if (expected == 0) return false;
    struct stat st {};
    if (stat(path.c_str(), &st) != 0) return false;
    return S_ISREG(st.st_mode) && static_cast<uint64_t>(st.st_size) == expected;
}

// Read payload/dex.list and validate class numbering (names must be legal and
// strictly increasing; R8 sharding may leave gaps, so contiguity is not
// required).
std::vector<std::string> read_dex_list(int module_dir_fd) {
    std::vector<std::string> names;
    int fd = openat(module_dir_fd, DEX_LIST, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) {
        LOGE("read_dex_list: open %s failed (errno=%d)", DEX_LIST, errno);
        return names;
    }
    std::string data;
    char buf[4096];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) data.append(buf, static_cast<size_t>(n));
    close(fd);
    if (n < 0) {
        LOGE("read_dex_list: read failed");
        return names;
    }

    size_t pos = 0;
    while (pos <= data.size()) {
        size_t eol = data.find('\n', pos);
        if (eol == std::string::npos) eol = data.size();
        std::string line = data.substr(pos, eol - pos);
        pos = eol + 1;
        while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
        if (!line.empty()) names.push_back(line);
    }

    size_t last_order = 0;
    for (const std::string &name : names) {
        size_t order = 0;
        if (name == "classes.dex") {
            order = 1;
        } else if (name.rfind("classes", 0) == 0 && name.size() > 8 &&
                   name.compare(name.size() - 4, 4, ".dex") == 0) {
            std::string mid = name.substr(7, name.size() - 4 - 7);
            bool all_digits =
                    !mid.empty() && mid.find_first_not_of("0123456789") == std::string::npos;
            if (all_digits) order = std::strtoul(mid.c_str(), nullptr, 10);
        }
        if (order == 0 || order <= last_order) {
            LOGE("read_dex_list: invalid or out-of-order entry '%s'", name.c_str());
            names.clear();
            return names;
        }
        last_order = order;
    }
    return names;
}

class TcqtZygisk : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        std::string nice_name = get_jstring(env, args->nice_name);
        if (!is_qq_or_tim(nice_name)) {
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        int dir_fd = api->getModuleDir();
        if (dir_fd < 0) {
            LOGE("preAppSpecialize: getModuleDir failed");
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }

        module_dir_fd_ = dir_fd;
        process_name_ = std::move(nice_name);
        data_dir_ = get_jstring(env, args->app_data_dir);
        dex_names_ = read_dex_list(dir_fd);
        enabled_ = !dex_names_.empty();
        if (!enabled_) {
            LOGE("preAppSpecialize: empty or missing payload/dex.list");
            close(dir_fd);
            module_dir_fd_ = -1;
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        LOGI("preAppSpecialize: target %s (uid=%d)", process_name_.c_str(), args->uid);
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *) override {
        if (!enabled_) return;
        enabled_ = false;

        if (module_dir_fd_ < 0 || data_dir_.empty()) {
            LOGE("postAppSpecialize: incomplete state");
            if (module_dir_fd_ >= 0) close(module_dir_fd_);
            return;
        }
        int mod_fd = module_dir_fd_;
        module_dir_fd_ = -1;

        std::string target_dir = data_dir_ + "/files/.tcqt";
        if (!ensure_dir(target_dir)) {
            close(mod_fd);
            return;
        }

        // Copy the APK payload (only when missing or size changed). It is
        // used by ZygiskEntry.init to extract libdexkit.so at runtime.
        std::string apk_dst = target_dir + "/tcqt.apk";
        uint64_t apk_size = module_file_size(mod_fd, PAYLOAD_APK);
        if (!path_has_size(apk_dst, apk_size)) {
            if (!copy_module_file(mod_fd, PAYLOAD_APK, apk_dst, APK_MAX_BYTES)) {
                LOGE("postAppSpecialize: failed to copy tcqt.apk");
                close(mod_fd);
                return;
            }
        }

        // Copy the dex payload and read it into memory, then build an
        // InMemoryDexClassLoader. An APK-path DexClassLoader is not usable
        // here: Android 10+ refuses to load dex from app-writable paths
        // ("Attempt to load writable dex file"), so the payload is loaded
        // from memory instead. kotlinx-coroutines' Main dispatcher discovery
        // (META-INF/services) is therefore unavailable; the Java side
        // compensates by hooking Dispatchers.getMain() in
        // ModuleLoader.installMainDispatcher().
        auto *dex_bufs = new std::vector<std::vector<uint8_t>>();
        for (const std::string &name : dex_names_) {
            std::string dst = target_dir + "/" + name;
            std::string rel = "payload/" + name;
            uint64_t dex_size = module_file_size(mod_fd, rel.c_str());
            if (!path_has_size(dst, dex_size)) {
                if (!copy_module_file(mod_fd, rel.c_str(), dst, DEX_MAX_BYTES)) {
                    LOGE("postAppSpecialize: failed to copy dex %s", name.c_str());
                    close(mod_fd);
                    return;
                }
            }
            std::vector<uint8_t> bytes;
            if (!read_file(dst, &bytes) || bytes.empty()) {
                LOGE("postAppSpecialize: failed to read dex %s", name.c_str());
                close(mod_fd);
                return;
            }
            dex_bufs->push_back(std::move(bytes));
        }
        close(mod_fd);
        jobject loader = build_dex_classloader(env, *dex_bufs);
        if (loader == nullptr) {
            LOGE("postAppSpecialize: failed to build InMemoryDexClassLoader");
            return;
        }

        // 4. Load the entry class and register its natives.
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

        // 5. Call ZygiskEntry.init(processName, dataDir, apkPath).
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
    int module_dir_fd_ = -1;
    bool enabled_ = false;
    std::string process_name_;
    std::string data_dir_;
    std::vector<std::string> dex_names_;
};

}  // namespace tcqt

REGISTER_ZYGISK_MODULE(tcqt::TcqtZygisk)
