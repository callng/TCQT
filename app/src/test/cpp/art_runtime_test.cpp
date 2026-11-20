#include <jni.h>
#include "zygisk/art/art_hook.h"

using namespace tcqt::zygisk;

extern "C" JNIEXPORT jboolean JNICALL
Java_ArtRuntimeRegression_initialize(JNIEnv *env, jclass) {
    return art_hook_init(env);
}
extern "C" JNIEXPORT jboolean JNICALL
Java_ArtRuntimeRegression_indexed(JNIEnv *env, jclass, jobject method) {
    return (reinterpret_cast<uintptr_t>(env->FromReflectedMethod(method)) & 1) != 0;
}
extern "C" JNIEXPORT jlong JNICALL
Java_ArtRuntimeRegression_methodId(JNIEnv *env, jclass, jobject method) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(env->FromReflectedMethod(method)));
}
extern "C" JNIEXPORT jint JNICALL
Java_ArtRuntimeRegression_hook(JNIEnv *env, jclass, jobject target, jobject backup, jobject bridge) {
    return art_hook_method(env, art_get_method(env, target), art_get_method(env, backup),
                           art_get_method(env, bridge));
}
extern "C" JNIEXPORT jint JNICALL
Java_ArtRuntimeRegression_unhook(JNIEnv *env, jclass, jobject target, jobject backup) {
    return art_unhook_method(env, art_get_method(env, target), art_get_method(env, backup));
}
extern "C" JNIEXPORT jobject JNICALL
Java_ArtRuntimeRegression_invoke(JNIEnv *env, jclass, jobject backup, jlong id,
                                  jclass target_class, jobject self, jobjectArray args) {
    return art_invoke_backup(env, backup, reinterpret_cast<jmethodID>(static_cast<uintptr_t>(id)),
                             target_class, self, args);
}
extern "C" JNIEXPORT jint JNICALL
Java_ArtRuntimeRegression_nativeTarget(JNIEnv *, jclass, jint value) {
    return value * 5;
}
