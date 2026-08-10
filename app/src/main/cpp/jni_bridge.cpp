#include "jni_bridge.h"

#include "art_hook.h"
#include "log.h"
#include "payload.h"

namespace tcqt {

namespace {

// ── ZygiskEntry natives ──────────────────────────────────────────────────────

jobject get_class_loader(JNIEnv *env, jclass entry_class) {
    jclass class_cls = env->FindClass("java/lang/Class");
    if (class_cls == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jmethodID get_cl = env->GetMethodID(class_cls, "getClassLoader",
                                        "()Ljava/lang/ClassLoader;");
    env->DeleteLocalRef(class_cls);
    if (get_cl == nullptr) {
        env->ExceptionClear();
        return nullptr;
    }
    jobject loader = env->CallObjectMethod(entry_class, get_cl);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return nullptr;
    }
    return loader;
}

extern "C" jboolean jni_native_art_init(JNIEnv *env, jclass entry_cls) {
    if (!art_hook_init(env)) {
        LOGE("ZygiskEntry.nativeArtInit: art_hook_init failed");
        return JNI_FALSE;
    }
    jobject loader = get_class_loader(env, entry_cls);
    if (loader == nullptr) return JNI_FALSE;
    bool ok = register_hook_bridge_natives(env, loader);
    env->DeleteLocalRef(loader);
    if (!ok) {
        LOGE("ZygiskEntry.nativeArtInit: failed to register ZygiskHookBridge natives");
        return JNI_FALSE;
    }
    LOGI("ZygiskEntry.nativeArtInit: ART hook engine ready");
    return JNI_TRUE;
}

// ── ZygiskHookBridge natives ─────────────────────────────────────────────────

extern "C" jlong jni_native_get_art_method(JNIEnv *env, jclass, jobject executable) {
    return static_cast<jlong>(art_get_method(env, executable));
}

extern "C" jint jni_native_hook_method(JNIEnv *env, jclass, jlong target, jlong backup,
                                       jlong bridge) {
    return art_hook_method(env, static_cast<uintptr_t>(target),
                           static_cast<uintptr_t>(backup),
                           static_cast<uintptr_t>(bridge));
}

extern "C" jint jni_native_unhook_method(JNIEnv *env, jclass, jlong target, jlong backup) {
    return art_unhook_method(env, static_cast<uintptr_t>(target),
                             static_cast<uintptr_t>(backup));
}

extern "C" jboolean jni_native_trust_dex_file(JNIEnv *env, jclass, jobject dex_file) {
    return art_trust_dex_file(env, dex_file) ? JNI_TRUE : JNI_FALSE;
}

}  // namespace

bool register_entry_natives(JNIEnv *env, jobject class_loader) {
    jclass clazz = load_class_from_loader(
            env, class_loader, "com.owo233.tcqt.loader.zygisk.ZygiskEntry");
    if (clazz == nullptr) {
        LOGE("register_entry_natives: ZygiskEntry class not found");
        return false;
    }
    JNINativeMethod methods[] = {
            {const_cast<char *>("nativeArtInit"), const_cast<char *>("()Z"),
             reinterpret_cast<void *>(jni_native_art_init)},
    };
    jint rc = env->RegisterNatives(clazz, methods, 1);
    env->DeleteLocalRef(clazz);
    if (rc != 0) {
        LOGE("register_entry_natives: RegisterNatives failed (%d)", rc);
        return false;
    }
    return true;
}

bool register_hook_bridge_natives(JNIEnv *env, jobject class_loader) {
    jclass clazz = load_class_from_loader(
            env, class_loader, "com.owo233.tcqt.loader.zygisk.ZygiskHookBridge");
    if (clazz == nullptr) {
        LOGE("register_hook_bridge_natives: ZygiskHookBridge class not found");
        return false;
    }
    JNINativeMethod methods[] = {
            {const_cast<char *>("nativeGetArtMethod"),
             const_cast<char *>("(Ljava/lang/reflect/Executable;)J"),
             reinterpret_cast<void *>(jni_native_get_art_method)},
            {const_cast<char *>("nativeHookMethod"), const_cast<char *>("(JJJ)I"),
             reinterpret_cast<void *>(jni_native_hook_method)},
            {const_cast<char *>("nativeUnhookMethod"), const_cast<char *>("(JJ)I"),
             reinterpret_cast<void *>(jni_native_unhook_method)},
            {const_cast<char *>("nativeTrustDexFile"),
             const_cast<char *>("(Ldalvik/system/DexFile;)Z"),
             reinterpret_cast<void *>(jni_native_trust_dex_file)},
    };
    jint rc = env->RegisterNatives(clazz, methods, 4);
    env->DeleteLocalRef(clazz);
    if (rc != 0) {
        LOGE("register_hook_bridge_natives: RegisterNatives failed (%d)", rc);
        return false;
    }
    LOGI("register_hook_bridge_natives: ZygiskHookBridge natives registered");
    return true;
}

}  // namespace tcqt
