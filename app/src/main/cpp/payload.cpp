#include "payload.h"

#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <vector>

#include "log.h"

namespace tcqt {

bool ensure_dir(const std::string &path) {
    if (path.empty()) return false;
    if (mkdir(path.c_str(), 0700) != 0 && errno != EEXIST) {
        LOGE("ensure_dir: mkdir %s failed (errno=%d)", path.c_str(), errno);
        return false;
    }
    chmod(path.c_str(), 0700);
    return true;
}

bool copy_module_file(int module_dir_fd, const char *src_rel, const std::string &dst_path,
                      uint64_t max_bytes) {
    if (module_dir_fd < 0 || src_rel == nullptr) return false;

    // O_NOFOLLOW prevents symlink attacks.
    int src_fd = openat(module_dir_fd, src_rel, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (src_fd < 0) {
        LOGE("copy_module_file: openat %s failed (errno=%d)", src_rel, errno);
        return false;
    }

    struct stat st{};
    if (fstat(src_fd, &st) != 0 || (st.st_mode & S_IFMT) != S_IFREG || st.st_size <= 0 ||
        static_cast<uint64_t>(st.st_size) > max_bytes) {
        LOGE("copy_module_file: invalid module payload %s", src_rel);
        close(src_fd);
        return false;
    }

    std::string tmp_path = dst_path + "." + std::to_string(getpid()) + ".tmp";
    unlink(tmp_path.c_str());

    int dst_fd = open(tmp_path.c_str(),
                      O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (dst_fd < 0) {
        LOGE("copy_module_file: cannot create %s (errno=%d)", tmp_path.c_str(), errno);
        close(src_fd);
        return false;
    }

    uint8_t buf[65536];
    bool ok = true;
    ssize_t n;
    while ((n = read(src_fd, buf, sizeof(buf))) > 0) {
        ssize_t w = write(dst_fd, buf, static_cast<size_t>(n));
        if (w != n) {
            ok = false;
            break;
        }
    }
    if (n < 0) ok = false;

    fsync(dst_fd);
    close(dst_fd);
    close(src_fd);

    if (!ok) {
        unlink(tmp_path.c_str());
        LOGE("copy_module_file: copy failed for %s", src_rel);
        return false;
    }
    if (rename(tmp_path.c_str(), dst_path.c_str()) != 0) {
        unlink(tmp_path.c_str());
        LOGE("copy_module_file: rename failed for %s (errno=%d)", dst_path.c_str(), errno);
        return false;
    }
    return true;
}

bool read_file(const std::string &path, std::vector<uint8_t> *out) {
    if (out == nullptr) return false;
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    out->clear();
    uint8_t buf[65536];
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf))) > 0) {
        out->insert(out->end(), buf, buf + n);
    }
    close(fd);
    return n == 0;
}

jobject build_dex_classloader(JNIEnv *env, const std::vector<std::vector<uint8_t>> &dex_bufs) {
    if (env == nullptr || dex_bufs.empty()) return nullptr;

    jclass imdcl_cls = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (imdcl_cls == nullptr) {
        env->ExceptionClear();
        LOGE("build_dex_classloader: InMemoryDexClassLoader not found");
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(
            imdcl_cls, "<init>", "([Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
    if (ctor == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(imdcl_cls);
        LOGE("build_dex_classloader: constructor not found");
        return nullptr;
    }

    jclass bb_cls = env->FindClass("java/nio/ByteBuffer");
    if (bb_cls == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(imdcl_cls);
        return nullptr;
    }

    jsize n = static_cast<jsize>(dex_bufs.size());
    jobjectArray buffers = env->NewObjectArray(n, bb_cls, nullptr);
    env->DeleteLocalRef(bb_cls);
    if (buffers == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(imdcl_cls);
        return nullptr;
    }

    for (jsize i = 0; i < n; ++i) {
        const auto &buf = dex_bufs[static_cast<size_t>(i)];
        jobject bb = env->NewDirectByteBuffer(
                const_cast<uint8_t *>(buf.data()), static_cast<jlong>(buf.size()));
        if (bb == nullptr || env->ExceptionCheck()) {
            env->ExceptionClear();
            env->DeleteLocalRef(buffers);
            env->DeleteLocalRef(imdcl_cls);
            return nullptr;
        }
        env->SetObjectArrayElement(buffers, i, bb);
        env->DeleteLocalRef(bb);
    }

    jclass cl_cls = env->FindClass("java/lang/ClassLoader");
    jmethodID get_sys = env->GetStaticMethodID(cl_cls, "getSystemClassLoader",
                                               "()Ljava/lang/ClassLoader;");
    jobject parent = env->CallStaticObjectMethod(cl_cls, get_sys);
    env->DeleteLocalRef(cl_cls);
    if (parent == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(buffers);
        env->DeleteLocalRef(imdcl_cls);
        return nullptr;
    }

    jobject loader = env->NewObject(imdcl_cls, ctor, buffers, parent);
    env->DeleteLocalRef(parent);
    env->DeleteLocalRef(buffers);
    env->DeleteLocalRef(imdcl_cls);
    if (loader == nullptr) {
        env->ExceptionClear();
        LOGE("build_dex_classloader: InMemoryDexClassLoader construction failed");
        return nullptr;
    }
    return loader;
}

jclass load_class_from_loader(JNIEnv *env, jobject loader, const char *dot_name) {
    if (env == nullptr || loader == nullptr || dot_name == nullptr) return nullptr;
    jstring jname = env->NewStringUTF(dot_name);
    if (jname == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jclass loader_cls = env->GetObjectClass(loader);
    jmethodID load = env->GetMethodID(loader_cls, "loadClass",
                                      "(Ljava/lang/String;)Ljava/lang/Class;");
    if (load == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(loader_cls);
        env->DeleteLocalRef(jname);
        return nullptr;
    }
    jclass clazz = static_cast<jclass>(env->CallObjectMethod(loader, load, jname));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        clazz = nullptr;
    }
    env->DeleteLocalRef(loader_cls);
    env->DeleteLocalRef(jname);
    return clazz;
}

}  // namespace tcqt
