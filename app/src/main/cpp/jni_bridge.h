#pragma once

#include <jni.h>

namespace tcqt {

// Set and get the global compatibility mode flag.
void set_compat_mode(bool enabled);
bool is_compat_mode();

// Register ZygiskEntry natives (nativeArtInit) on the class loaded through the
// given InMemoryDexClassLoader.
bool register_entry_natives(JNIEnv *env, jobject class_loader);

// Register ZygiskHookBridge natives (getArtMethod/hook/unhook/trustDexFile).
bool register_hook_bridge_natives(JNIEnv *env, jobject class_loader);

}  // namespace tcqt
