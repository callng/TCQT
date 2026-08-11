#pragma once

#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

namespace tcqt {

// Upper bound for classes*.dex entries probed inside the package APK.
constexpr size_t MAX_DEX_FILES = 256;

// Create a directory (0700), ignoring EEXIST.
bool ensure_dir(const std::string &path);

// Copy a file from an already-open fd (opened in preAppSpecialize while still
// root, so it stays readable after the app drops privileges) to dst_path using
// a PID-unique temp file and atomic rename. Returns false on any failure.
bool copy_fd_to_path(int src_fd, const std::string &dst_path, uint64_t max_bytes);

// Read every classes*.dex entry (classes.dex, classes2.dex, ...) from the APK
// file at apk_path into memory, in ascending numbering order, via
// java.util.zip.ZipFile. Returns false when no dex could be read.
bool read_dex_from_apk(JNIEnv *env, const std::string &apk_path,
                       std::vector<std::vector<uint8_t>> *out);

// Build an InMemoryDexClassLoader over the given dex buffers with
// ClassLoader.getSystemClassLoader() as parent. The buffers must stay valid
// for the lifetime of the returned loader.
jobject build_dex_classloader(JNIEnv *env, const std::vector<std::vector<uint8_t>> &dex_bufs);

// Load a class via ClassLoader.loadClass(dot_name).
jclass load_class_from_loader(JNIEnv *env, jobject loader, const char *dot_name);

}  // namespace tcqt
