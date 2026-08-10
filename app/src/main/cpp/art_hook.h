#pragma once

#include <jni.h>

#include <cstdint>

namespace tcqt {

// Initialize the ART hook engine: resolve libart.so symbols, probe the
// ArtMethod layout, and prepare the trampoline pool. Safe to call once.
bool art_hook_init(JNIEnv *env);

bool art_hook_initialized();

// Get the ArtMethod pointer backing a java.lang.reflect.Executable.
uintptr_t art_get_method(JNIEnv *env, jobject executable);

// Install a hook: target's entry point is redirected to a trampoline that
// jumps into the bridge ArtMethod's entry point; the original ArtMethod
// contents are snapshotted into backup. Returns 0 on success.
int art_hook_method(JNIEnv *env, uintptr_t target, uintptr_t backup, uintptr_t bridge);

// Restore the target ArtMethod from backup. Returns 0 on success.
int art_unhook_method(JNIEnv *env, uintptr_t target, uintptr_t backup);

// Mark a DexFile as trusted (bypass hidden API checks on generated dex).
bool art_trust_dex_file(JNIEnv *env, jobject dex_file);

}  // namespace tcqt
