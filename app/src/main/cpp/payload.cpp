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

bool copy_fd_to_path(int src_fd, const std::string &dst_path, uint64_t max_bytes) {
    if (src_fd < 0) return false;

    struct stat st{};
    if (fstat(src_fd, &st) != 0 || (st.st_mode & S_IFMT) != S_IFREG || st.st_size <= 0 ||
        static_cast<uint64_t>(st.st_size) > max_bytes) {
        LOGE("copy_fd_to_path: invalid source (size=%lld)", static_cast<long long>(st.st_size));
        return false;
    }

    std::string tmp_path = dst_path + "." + std::to_string(getpid()) + ".tmp";
    unlink(tmp_path.c_str());

    int dst_fd = open(tmp_path.c_str(),
                      O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (dst_fd < 0) {
        LOGE("copy_fd_to_path: cannot create %s (errno=%d)", tmp_path.c_str(), errno);
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

    if (!ok) {
        unlink(tmp_path.c_str());
        LOGE("copy_fd_to_path: copy failed for %s", dst_path.c_str());
        return false;
    }
    if (rename(tmp_path.c_str(), dst_path.c_str()) != 0) {
        unlink(tmp_path.c_str());
        LOGE("copy_fd_to_path: rename failed for %s (errno=%d)", dst_path.c_str(), errno);
        return false;
    }
    return true;
}

// Read every classes*.dex entry from an APK via JNI (java.util.zip.ZipFile),
// mirroring FunBox's loader: the dual-format package is a plain APK, so its
// dex files are read straight out of the zip. Entry names are probed in
// ascending numbering order (classes.dex, classes2.dex, ...).
bool read_dex_from_apk(JNIEnv *env, const std::string &apk_path,
                       std::vector<std::vector<uint8_t>> *out) {
    if (env == nullptr || out == nullptr) return false;
    out->clear();

    jclass zip_cls = env->FindClass("java/util/zip/ZipFile");
    jclass in_cls = env->FindClass("java/io/InputStream");
    jclass baos_cls = env->FindClass("java/io/ByteArrayOutputStream");
    if (zip_cls == nullptr || in_cls == nullptr || baos_cls == nullptr) {
        env->ExceptionClear();
        LOGE("read_dex_from_apk: classes not found");
        return false;
    }

    jmethodID zip_ctor = env->GetMethodID(zip_cls, "<init>", "(Ljava/lang/String;)V");
    jmethodID get_entry = env->GetMethodID(
            zip_cls, "getEntry", "(Ljava/lang/String;)Ljava/util/zip/ZipEntry;");
    jmethodID get_input = env->GetMethodID(
            zip_cls, "getInputStream", "(Ljava/util/zip/ZipEntry;)Ljava/io/InputStream;");
    jmethodID zip_close = env->GetMethodID(zip_cls, "close", "()V");
    jmethodID read = env->GetMethodID(in_cls, "read", "([B)I");
    jmethodID in_close = env->GetMethodID(in_cls, "close", "()V");
    jmethodID baos_ctor = env->GetMethodID(baos_cls, "<init>", "()V");
    jmethodID baos_write = env->GetMethodID(baos_cls, "write", "([BII)V");
    jmethodID to_byte_array = env->GetMethodID(baos_cls, "toByteArray", "()[B");
    if (zip_ctor == nullptr || get_entry == nullptr || get_input == nullptr ||
        zip_close == nullptr || read == nullptr || in_close == nullptr ||
        baos_ctor == nullptr || baos_write == nullptr || to_byte_array == nullptr) {
        env->ExceptionClear();
        LOGE("read_dex_from_apk: methods not found");
        return false;
    }

    jstring j_path = env->NewStringUTF(apk_path.c_str());
    if (j_path == nullptr) {
        env->ExceptionClear();
        return false;
    }
    jobject zip = env->NewObject(zip_cls, zip_ctor, j_path);
    env->DeleteLocalRef(j_path);
    if (zip == nullptr) {
        env->ExceptionClear();
        LOGE("read_dex_from_apk: cannot open %s", apk_path.c_str());
        return false;
    }

    jbyteArray j_buf = env->NewByteArray(64 * 1024);
    if (j_buf == nullptr) {
        env->ExceptionClear();
        env->DeleteLocalRef(zip);
        return false;
    }

    for (size_t n = 1; n <= MAX_DEX_FILES; ++n) {
        std::string name = (n == 1) ? "classes.dex" : "classes" + std::to_string(n) + ".dex";
        jstring j_name = env->NewStringUTF(name.c_str());
        if (j_name == nullptr) {
            env->ExceptionClear();
            break;
        }
        jobject entry = env->CallObjectMethod(zip, get_entry, j_name);
        env->DeleteLocalRef(j_name);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            break;
        }
        if (entry == nullptr) break;  // no more dex files
        jobject input = env->CallObjectMethod(zip, get_input, entry);
        env->DeleteLocalRef(entry);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            break;
        }
        if (input == nullptr) break;

        jobject baos = env->NewObject(baos_cls, baos_ctor);
        if (baos == nullptr) {
            env->ExceptionClear();
            env->DeleteLocalRef(input);
            break;
        }
        bool read_ok = true;
        jint nread;
        while ((nread = env->CallIntMethod(input, read, j_buf)) > 0) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                read_ok = false;
                break;
            }
            env->CallVoidMethod(baos, baos_write, j_buf, 0, nread);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                read_ok = false;
                break;
            }
        }
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            read_ok = false;
        }
        env->CallVoidMethod(input, in_close);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(input);

        if (!read_ok) {
            env->DeleteLocalRef(baos);
            break;
        }
        jbyteArray bytes = static_cast<jbyteArray>(
                env->CallObjectMethod(baos, to_byte_array));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            env->DeleteLocalRef(baos);
            break;
        }
        env->DeleteLocalRef(baos);
        if (bytes == nullptr) break;

        jsize len = env->GetArrayLength(bytes);
        if (len <= 0) {
            env->DeleteLocalRef(bytes);
            break;
        }
        std::vector<uint8_t> dex(static_cast<size_t>(len));
        env->GetByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte *>(dex.data()));
        env->DeleteLocalRef(bytes);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            break;
        }
        out->push_back(std::move(dex));
    }

    env->DeleteLocalRef(j_buf);
    env->CallVoidMethod(zip, zip_close);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(zip);
    return !out->empty();
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
