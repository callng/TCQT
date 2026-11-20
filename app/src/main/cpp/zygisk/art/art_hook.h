#pragma once

#include <jni.h>

#include <cstdint>

namespace tcqt::zygisk {

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

// Restore the fields changed by this hook. Returns 0 when removed, 1 when a
// later framework owns the entry (retain an empty Java passthrough), <0 on error.
int art_unhook_method(JNIEnv *env, uintptr_t target, uintptr_t backup);

// Safely invoke a backup ArtMethod from Native code bypassing Java reflection receiver checks.
// backup_id must be obtained before copying the target over the backup: indexed
// JNI ID creation uses the method's original declaring-class array position.
jobject art_invoke_backup(JNIEnv *env, jobject backup_method, jmethodID backup_id,
                          jclass target_class, jobject this_object, jobjectArray args);

}  // namespace tcqt::zygisk
