#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_runtime.h"
#include <atomic>
#include <cstring>
#include <mutex>
#include <vector>
#include "zygisk/logging/log.h"

namespace tcqt::zygisk {
using namespace art_detail;

namespace {

// 参数类型编码。只存编码，不持有 jclass，类卸载后不会留下悬空的 klass 指针。
enum ParamKind : int8_t {
    kRef = 0,
    kBoolean = 1,
    kByte = 2,
    kChar = 3,
    kShort = 4,
    kInt = 5,
    kLong = 6,
    kFloat = 7,
    kDouble = 8,
};

constexpr size_t kBucketCount = 256;
constexpr size_t kMaxEntries = 4096;

/**
 * 单个 backup 方法（按 ArtMethod 指针）的签名缓存。
 *
 * 发布后不可变，读路径无锁；`method_ref` 是 backup Method 的弱全局引用，
 * 每次命中用 IsSameObject 校验。类被卸载后弱引用会被清空，于是旧条目自然失效，
 * 同一个 ArtMethod 地址被复用时也不会读到上一个方法的签名。
 */
struct SignatureEntry {
    uintptr_t art = 0;
    jobject method_ref = nullptr;
    char return_code = 'L';
    bool is_static = false;
    std::vector<int8_t> params;
    std::atomic<SignatureEntry *> next{nullptr};
};

std::atomic<SignatureEntry *> g_buckets[kBucketCount];
std::atomic<uint32_t> g_entry_count{0};
std::mutex g_publish_mutex;

inline size_t bucket_index(uintptr_t art) {
    return (art >> 3) & (kBucketCount - 1);
}

char return_code_of(const char *name) {
    if (strcmp(name, "void") == 0) return 'V';
    if (strcmp(name, "boolean") == 0) return 'Z';
    if (strcmp(name, "byte") == 0) return 'B';
    if (strcmp(name, "char") == 0) return 'C';
    if (strcmp(name, "short") == 0) return 'S';
    if (strcmp(name, "int") == 0) return 'I';
    if (strcmp(name, "long") == 0) return 'J';
    if (strcmp(name, "float") == 0) return 'F';
    if (strcmp(name, "double") == 0) return 'D';
    return 'L';
}

int8_t param_kind_of(const char *name) {
    if (strcmp(name, "boolean") == 0) return kBoolean;
    if (strcmp(name, "byte") == 0) return kByte;
    if (strcmp(name, "char") == 0) return kChar;
    if (strcmp(name, "short") == 0) return kShort;
    if (strcmp(name, "int") == 0) return kInt;
    if (strcmp(name, "long") == 0) return kLong;
    if (strcmp(name, "float") == 0) return kFloat;
    if (strcmp(name, "double") == 0) return kDouble;
    return kRef;
}

/** 读取 backup ArtMethod 的 ACC_STATIC。只读 ArtMethod 自身内存，不触碰 klass。 */
bool read_is_static(uintptr_t art) {
    const size_t offset = state().layout.access_flags_offset;
    if (art == 0 || offset == 0) return false;
    const uint32_t flags =
            __atomic_load_n(reinterpret_cast<const uint32_t *>(art + offset), __ATOMIC_RELAXED);
    return (flags & ACC_STATIC) != 0;
}

void clear_pending_exception(JNIEnv *env) {
    if (env->ExceptionCheck()) env->ExceptionClear();
}

/**
 * 解析一次反射签名。只在缓存未命中时执行。
 *
 * 只有完整解析成功才返回 true —— 解析失败的结果（默认返回类型/引用参数）
 * 不能写进缓存，否则一次瞬时异常会被固化成错误签名，后续每次派发都按错误
 * 类型调用。失败时调用方退化为“本次按引用传”的旧行为，不缓存。
 */
bool resolve_signature(JNIEnv *env, jobject method, SignatureEntry *out) {
    jclass method_cls = env->GetObjectClass(method);
    if (method_cls == nullptr) return false;
    jmethodID get_ret = env->GetMethodID(method_cls, "getReturnType", "()Ljava/lang/Class;");
    jmethodID get_params = env->GetMethodID(method_cls, "getParameterTypes", "()[Ljava/lang/Class;");
    jclass class_cls = env->FindClass("java/lang/Class");
    if (get_ret == nullptr || get_params == nullptr || class_cls == nullptr) {
        clear_pending_exception(env);
        if (class_cls != nullptr) env->DeleteLocalRef(class_cls);
        env->DeleteLocalRef(method_cls);
        return false;
    }
    jmethodID is_prim = env->GetMethodID(class_cls, "isPrimitive", "()Z");
    jmethodID get_name = env->GetMethodID(class_cls, "getName", "()Ljava/lang/String;");
    if (is_prim == nullptr || get_name == nullptr) {
        clear_pending_exception(env);
        env->DeleteLocalRef(class_cls);
        env->DeleteLocalRef(method_cls);
        return false;
    }

    bool ok = true;
    auto ret_cls = static_cast<jclass>(env->CallObjectMethod(method, get_ret));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        ret_cls = nullptr;
    }
    if (ret_cls == nullptr) {
        ok = false;
    } else if (env->CallBooleanMethod(ret_cls, is_prim)) {
        auto name = static_cast<jstring>(env->CallObjectMethod(ret_cls, get_name));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            name = nullptr;
        }
        if (name == nullptr) {
            ok = false;
        } else {
            const char *text = env->GetStringUTFChars(name, nullptr);
            if (text != nullptr) {
                out->return_code = return_code_of(text);
                env->ReleaseStringUTFChars(name, text);
            } else {
                ok = false;
            }
            env->DeleteLocalRef(name);
        }
    }
    clear_pending_exception(env);
    if (ret_cls != nullptr) env->DeleteLocalRef(ret_cls);

    auto param_array = static_cast<jobjectArray>(env->CallObjectMethod(method, get_params));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        param_array = nullptr;
    }
    if (param_array == nullptr) {
        ok = false;
    } else {
        const jsize count = env->GetArrayLength(param_array);
        out->params.reserve(static_cast<size_t>(count));
        for (jsize i = 0; i < count; ++i) {
            auto param_cls = static_cast<jclass>(env->GetObjectArrayElement(param_array, i));
            int8_t kind = kRef;
            if (param_cls != nullptr && env->CallBooleanMethod(param_cls, is_prim)) {
                auto name = static_cast<jstring>(env->CallObjectMethod(param_cls, get_name));
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    name = nullptr;
                }
                if (name != nullptr) {
                    const char *text = env->GetStringUTFChars(name, nullptr);
                    if (text != nullptr) {
                        kind = param_kind_of(text);
                        env->ReleaseStringUTFChars(name, text);
                    } else {
                        ok = false;
                    }
                    env->DeleteLocalRef(name);
                } else {
                    ok = false;
                }
            }
            clear_pending_exception(env);
            out->params.push_back(kind);
            if (param_cls != nullptr) env->DeleteLocalRef(param_cls);
        }
        env->DeleteLocalRef(param_array);
    }

    env->DeleteLocalRef(class_cls);
    env->DeleteLocalRef(method_cls);
    return ok;
}

SignatureEntry *find_signature(JNIEnv *env, uintptr_t art, jobject method) {
    auto *entry = g_buckets[bucket_index(art)].load(std::memory_order_acquire);
    for (; entry != nullptr; entry = entry->next.load(std::memory_order_acquire)) {
        if (entry->art != art) continue;
        // 弱引用被清空（类卸载）或指向另一个 Method 时视为未命中。
        if (env->IsSameObject(entry->method_ref, method)) return entry;
    }
    return nullptr;
}

SignatureEntry *acquire_signature(JNIEnv *env, uintptr_t art, jobject method) {
    if (auto *hit = find_signature(env, art, method)) return hit;

    // 反射解析放在锁外：解析期间可能触发 Java 代码，避免持锁时重入派发。
    auto *entry = new SignatureEntry();
    entry->art = art;
    entry->is_static = read_is_static(art);
    if (!resolve_signature(env, method, entry)) {
        delete entry;
        return nullptr;
    }

    entry->method_ref = env->NewWeakGlobalRef(method);
    if (entry->method_ref == nullptr) {
        clear_pending_exception(env);
        delete entry;
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_publish_mutex);
    // 并发首次派发只发布一份；发现别人已发布就丢弃自己这份，保证按 ArtMethod 去重。
    if (auto *hit = find_signature(env, art, method)) {
        env->DeleteWeakGlobalRef(entry->method_ref);
        delete entry;
        return hit;
    }
    if (g_entry_count.load(std::memory_order_relaxed) >= kMaxEntries) {
        env->DeleteWeakGlobalRef(entry->method_ref);
        delete entry;
        return nullptr;
    }

    const size_t index = bucket_index(art);
    entry->next.store(g_buckets[index].load(std::memory_order_relaxed), std::memory_order_relaxed);
    g_buckets[index].store(entry, std::memory_order_release);
    g_entry_count.fetch_add(1, std::memory_order_relaxed);
    return entry;
}

}  // namespace

jobject art_invoke_backup(JNIEnv *env, jobject backup_method, jmethodID mid,
                          jclass target_cls, jobject this_object, jobjectArray args) {
    if (env == nullptr || backup_method == nullptr || mid == nullptr || target_cls == nullptr) return nullptr;

    const uintptr_t backup_art = art_get_method(env, backup_method);
    if (backup_art == 0) {
        jclass error = env->FindClass("java/lang/IllegalStateException");
        if (error != nullptr) {
            env->ThrowNew(error, "Cannot read backup ArtMethod");
            env->DeleteLocalRef(error);
        }
        return nullptr;
    }

    SignatureEntry local;
    SignatureEntry *signature = acquire_signature(env, backup_art, backup_method);
    if (signature == nullptr) {
        // 缓存不可用（条目到达上限/弱引用创建失败）时退化为一次性解析，正确性不变。
        local.art = backup_art;
        local.is_static = read_is_static(backup_art);
        resolve_signature(env, backup_method, &local);
        signature = &local;
    }

    const bool is_static = signature->is_static;
    const char type_code = signature->return_code;
    const std::vector<int8_t> &param_kinds = signature->params;
    const jsize param_count = static_cast<jsize>(param_kinds.size());

    jsize argc = (args != nullptr) ? env->GetArrayLength(args) : 0;
    std::vector<jvalue> jargs(argc);
    // 记录哪些槽位真正持有 local ref（jvalue 是 union，primitive 参数
    // 填 .z/.i/.j 后会污染 .l 字段，清理时必须按类型判断而非判空）。
    std::vector<bool> jarg_refs(argc, false);

    // primitive 参数必须从 boxed 对象 unbox 后填入 jvalue 对应字段；全部按
    // 对象引用（.l）传递会导致 JNI 签名不匹配，ART 检测到非法调用后会用
    // SIGSEGV（SI_TKILL）自杀，进程直接闪退。
    for (jsize i = 0; i < argc; ++i) {
        jobject arg = env->GetObjectArrayElement(args, i);
        const int8_t kind = (i < param_count) ? param_kinds[static_cast<size_t>(i)] : kRef;
        if (kind == kRef || arg == nullptr) {
            if (kind == kRef) {
                jargs[i].l = arg;
                jarg_refs[i] = (arg != nullptr);
            }
            continue;
        }

        jclass box_cls = env->GetObjectClass(arg);
        switch (kind) {
            case kBoolean: {
                jmethodID m = env->GetMethodID(box_cls, "booleanValue", "()Z");
                jargs[i].z = (m != nullptr) ? env->CallBooleanMethod(arg, m) : JNI_FALSE;
                break;
            }
            case kByte: {
                jmethodID m = env->GetMethodID(box_cls, "byteValue", "()B");
                jargs[i].b = (m != nullptr) ? env->CallByteMethod(arg, m) : 0;
                break;
            }
            case kChar: {
                jmethodID m = env->GetMethodID(box_cls, "charValue", "()C");
                jargs[i].c = (m != nullptr) ? env->CallCharMethod(arg, m) : 0;
                break;
            }
            case kShort: {
                jmethodID m = env->GetMethodID(box_cls, "shortValue", "()S");
                jargs[i].s = (m != nullptr) ? env->CallShortMethod(arg, m) : 0;
                break;
            }
            case kInt: {
                jmethodID m = env->GetMethodID(box_cls, "intValue", "()I");
                jargs[i].i = (m != nullptr) ? env->CallIntMethod(arg, m) : 0;
                break;
            }
            case kLong: {
                jmethodID m = env->GetMethodID(box_cls, "longValue", "()J");
                jargs[i].j = (m != nullptr) ? env->CallLongMethod(arg, m) : 0;
                break;
            }
            case kFloat: {
                jmethodID m = env->GetMethodID(box_cls, "floatValue", "()F");
                jargs[i].f = (m != nullptr) ? env->CallFloatMethod(arg, m) : 0;
                break;
            }
            case kDouble: {
                jmethodID m = env->GetMethodID(box_cls, "doubleValue", "()D");
                jargs[i].d = (m != nullptr) ? env->CallDoubleMethod(arg, m) : 0;
                break;
            }
            default:
                jargs[i].l = arg;
                jarg_refs[i] = true;
                break;
        }
        if (box_cls != nullptr) env->DeleteLocalRef(box_cls);
    }

    // The reflected backup caches its generated declaring class, while its
    // ArtMethod now belongs to the original target. JNI (especially CheckJNI)
    // requires the target's actual declaring class for both invocation kinds.
    jobject result = nullptr;
    auto box_b = [&](jboolean v) {
        // Preserve a pending target exception; JNI class lookup would abort ART.
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Boolean");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(Z)Ljava/lang/Boolean;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_i = [&](jint v) {
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Integer");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(I)Ljava/lang/Integer;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_j = [&](jlong v) {
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Long");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(J)Ljava/lang/Long;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_f = [&](jfloat v) {
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Float");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(F)Ljava/lang/Float;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_d = [&](jdouble v) {
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Double");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(D)Ljava/lang/Double;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_byte = [&](jbyte v) {
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Byte");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(B)Ljava/lang/Byte;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_char = [&](jchar v) {
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Character");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(C)Ljava/lang/Character;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_short = [&](jshort v) {
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Short");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(S)Ljava/lang/Short;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };

    if (is_static) {
        switch (type_code) {
            case 'V': env->CallStaticVoidMethodA(target_cls, mid, jargs.data()); break;
            case 'Z': result = box_b(env->CallStaticBooleanMethodA(target_cls, mid, jargs.data())); break;
            case 'I': result = box_i(env->CallStaticIntMethodA(target_cls, mid, jargs.data())); break;
            case 'J': result = box_j(env->CallStaticLongMethodA(target_cls, mid, jargs.data())); break;
            case 'F': result = box_f(env->CallStaticFloatMethodA(target_cls, mid, jargs.data())); break;
            case 'D': result = box_d(env->CallStaticDoubleMethodA(target_cls, mid, jargs.data())); break;
            case 'B': result = box_byte(env->CallStaticByteMethodA(target_cls, mid, jargs.data())); break;
            case 'C': result = box_char(env->CallStaticCharMethodA(target_cls, mid, jargs.data())); break;
            case 'S': result = box_short(env->CallStaticShortMethodA(target_cls, mid, jargs.data())); break;
            default: result = env->CallStaticObjectMethodA(target_cls, mid, jargs.data()); break;
        }
    } else {
        switch (type_code) {
            case 'V': env->CallNonvirtualVoidMethodA(this_object, target_cls, mid, jargs.data()); break;
            case 'Z': result = box_b(env->CallNonvirtualBooleanMethodA(this_object, target_cls, mid, jargs.data())); break;
            case 'I': result = box_i(env->CallNonvirtualIntMethodA(this_object, target_cls, mid, jargs.data())); break;
            case 'J': result = box_j(env->CallNonvirtualLongMethodA(this_object, target_cls, mid, jargs.data())); break;
            case 'F': result = box_f(env->CallNonvirtualFloatMethodA(this_object, target_cls, mid, jargs.data())); break;
            case 'D': result = box_d(env->CallNonvirtualDoubleMethodA(this_object, target_cls, mid, jargs.data())); break;
            case 'B': result = box_byte(env->CallNonvirtualByteMethodA(this_object, target_cls, mid, jargs.data())); break;
            case 'C': result = box_char(env->CallNonvirtualCharMethodA(this_object, target_cls, mid, jargs.data())); break;
            case 'S': result = box_short(env->CallNonvirtualShortMethodA(this_object, target_cls, mid, jargs.data())); break;
            default: result = env->CallNonvirtualObjectMethodA(this_object, target_cls, mid, jargs.data()); break;
        }
    }

    for (jsize i = 0; i < argc; ++i) {
        if (jarg_refs[i] && jargs[i].l != nullptr) env->DeleteLocalRef(jargs[i].l);
    }

    return result;
}
}  // namespace tcqt::zygisk
