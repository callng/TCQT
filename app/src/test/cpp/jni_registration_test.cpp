#include <jni.h>
#include <cstdio>
#include <cstdlib>
#include <string>

#include "zygisk/art/art_hook.h"
#include "zygisk/jni_bridge.h"
#include "zygisk/payload.h"

// Exercise the production registration tables against a real JVM. Only
// Android logging, ART hooks, and class-loader selection are replaced here.
extern "C" int __android_log_print(int, const char *, const char *, ...) { return 0; }
namespace tcqt::zygisk {
void tcqt_log_write(int, const char *, ...) {}
bool log_debug_enabled() { return false; }
bool art_hook_init(JNIEnv *) { return false; }
uintptr_t art_get_method(JNIEnv *, jobject) { return 0; }
int art_hook_method(JNIEnv *, uintptr_t, uintptr_t, uintptr_t) { return -1; }
int art_unhook_method(JNIEnv *, uintptr_t, uintptr_t) { return -1; }
jobject art_invoke_backup(JNIEnv *, jobject, jmethodID, jclass, jobject, jobjectArray) { return nullptr; }
jclass load_class_from_loader(JNIEnv *env, jobject selected_class, const char *) {
    return static_cast<jclass>(env->NewLocalRef(selected_class));
}
}  // namespace tcqt::zygisk

void require(bool condition, const char *message) {
    if (!condition) { std::fprintf(stderr, "FAIL: %s\n", message); std::exit(1); }
}

int main(int argc, char **argv) {
    require(argc == 2, "classpath argument");
    std::string classpath = std::string("-Djava.class.path=") + argv[1];
    JavaVMOption option{classpath.data(), nullptr};
    JavaVMInitArgs args{};
    args.version = JNI_VERSION_1_8;
    args.nOptions = 1;
    args.options = &option;
    JavaVM *vm = nullptr;
    JNIEnv *env = nullptr;
    require(JNI_CreateJavaVM(&vm, reinterpret_cast<void **>(&env), &args) == JNI_OK,
            "create JVM");
    struct Case { const char *name; bool bridge; bool expected; };
    for (const auto &test : {
            Case{"EntryGood", false, true}, Case{"EntryBad", false, false},
            Case{"BridgeGood", true, true}, Case{"BridgeBad", true, false}}) {
        jclass fixture = env->FindClass(test.name);
        require(fixture != nullptr, "load fixture");
        bool result = test.bridge ? tcqt::zygisk::register_hook_bridge_natives(env, fixture)
                                  : tcqt::zygisk::register_entry_natives(env, fixture);
        require(result == test.expected, "registration result");
        require(!env->ExceptionCheck(), "registration leaked a pending JNI exception");
        // This is the next module's operation in the reported crash stack.
        jclass next_class = env->FindClass("java/lang/Object");
        require(next_class != nullptr && !env->ExceptionCheck(), "next module FindClass");
        env->DeleteLocalRef(next_class);
        env->DeleteLocalRef(fixture);
        std::printf("PASS: %s registration leaves JNI clean\n", test.name);
    }
    vm->DestroyJavaVM();
}
