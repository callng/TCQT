#pragma once

#include <jni.h>

namespace tcqt::zygisk {

// Register ZygiskEntry natives (nativeArtInit) on the class loaded through the
// given InMemoryDexClassLoader.
bool register_entry_natives(JNIEnv *env, jobject class_loader);

// Register ZygiskHookBridge natives (getArtMethod/hook/unhook/invokeBackup).
bool register_hook_bridge_natives(JNIEnv *env, jobject class_loader);

}  // namespace tcqt::zygisk
