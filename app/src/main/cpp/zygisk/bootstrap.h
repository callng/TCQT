#pragma once

#include <jni.h>

#include <string>

namespace tcqt::zygisk {

// Build the in-memory payload class loader, register JNI methods and invoke
// ZygiskEntry.init. Successful loaders retain their backing dex buffers.
bool bootstrap_payload(JNIEnv *env, const std::string &apk_path,
                       const std::string &process_name, const std::string &data_dir);

}  // namespace tcqt::zygisk
