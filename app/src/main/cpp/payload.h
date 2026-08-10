#pragma once

#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

namespace tcqt {

// Create a directory (0700), ignoring EEXIST.
bool ensure_dir(const std::string &path);

// Copy a file from the module dir fd to dst_path using a PID-unique temp file
// and atomic rename. Returns false on any failure.
bool copy_module_file(int module_dir_fd, const char *src_rel, const std::string &dst_path,
                      uint64_t max_bytes);

// Read an entire file into memory.
bool read_file(const std::string &path, std::vector<uint8_t> *out);

// Build an InMemoryDexClassLoader over the given dex buffers with
// ClassLoader.getSystemClassLoader() as parent. The buffers must stay valid
// for the lifetime of the returned loader.
jobject build_dex_classloader(JNIEnv *env, const std::vector<std::vector<uint8_t>> &dex_bufs);

// Load a class via ClassLoader.loadClass(dot_name).
jclass load_class_from_loader(JNIEnv *env, jobject loader, const char *dot_name);

}  // namespace tcqt
