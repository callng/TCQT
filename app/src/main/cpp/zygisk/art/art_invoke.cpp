#include "zygisk/art/art_hook.h"
#include "zygisk/art/art_runtime.h"
#include <cstring>
#include <vector>
#include "zygisk/logging/log.h"
namespace tcqt::zygisk {
using namespace art_detail;
jobject art_invoke_backup(JNIEnv *env, jobject backup_method, jobject this_object, jobjectArray args) {
    if (backup_method == nullptr) return nullptr;

    jmethodID mid = env->FromReflectedMethod(backup_method);
    if (mid == nullptr) return nullptr;

    uintptr_t backup_art = reinterpret_cast<uintptr_t>(mid);
    size_t af_off = state().layout.access_flags_offset;
    uint32_t flags = __atomic_load_n(reinterpret_cast<const uint32_t *>(backup_art + af_off), __ATOMIC_RELAXED);
    bool is_static = (flags & ACC_STATIC) != 0;

    jclass method_cls = env->GetObjectClass(backup_method);
    jmethodID get_ret_mid = env->GetMethodID(method_cls, "getReturnType", "()Ljava/lang/Class;");
    auto ret_cls = static_cast<jclass>(env->CallObjectMethod(backup_method, get_ret_mid));
    env->DeleteLocalRef(method_cls);

    jclass class_cls = env->FindClass("java/lang/Class");
    jmethodID is_prim_mid = env->GetMethodID(class_cls, "isPrimitive", "()Z");
    bool is_prim = ret_cls != nullptr && env->CallBooleanMethod(ret_cls, is_prim_mid);

    char type_code = 'L';
    if (is_prim) {
        jmethodID get_name_mid = env->GetMethodID(class_cls, "getName", "()Ljava/lang/String;");
        auto name_str = static_cast<jstring>(env->CallObjectMethod(ret_cls, get_name_mid));
        const char *name = env->GetStringUTFChars(name_str, nullptr);
        if (strcmp(name, "void") == 0) type_code = 'V';
        else if (strcmp(name, "boolean") == 0) type_code = 'Z';
        else if (strcmp(name, "int") == 0) type_code = 'I';
        else if (strcmp(name, "long") == 0) type_code = 'J';
        else if (strcmp(name, "float") == 0) type_code = 'F';
        else if (strcmp(name, "double") == 0) type_code = 'D';
        else if (strcmp(name, "byte") == 0) type_code = 'B';
        else if (strcmp(name, "char") == 0) type_code = 'C';
        else if (strcmp(name, "short") == 0) type_code = 'S';
        env->ReleaseStringUTFChars(name_str, name);
        env->DeleteLocalRef(name_str);
    }
    if (ret_cls != nullptr) env->DeleteLocalRef(ret_cls);
    env->DeleteLocalRef(class_cls);

    jsize argc = (args != nullptr) ? env->GetArrayLength(args) : 0;
    std::vector<jvalue> jargs(argc);
    // 记录哪些槽位真正持有 local ref（jvalue 是 union，primitive 参数
    // 填 .z/.i/.j 后会污染 .l 字段，清理时必须按类型判断而非判空）。
    std::vector<bool> jarg_refs(argc, false);

    // primitive 参数必须从 boxed 对象 unbox 后填入 jvalue 对应字段；全部按
    // 对象引用（.l）传递会导致 JNI 签名不匹配，ART 检测到非法调用后会用
    // SIGSEGV（SI_TKILL）自杀，进程直接闪退（如 LoadedApk.<init> 的 boolean
    // 参数在 invokeBackup 时反复触发）。
    jclass bm_cls = env->GetObjectClass(backup_method);
    jmethodID get_params_mid = env->GetMethodID(
            bm_cls, "getParameterTypes", "()[Ljava/lang/Class;");
    env->DeleteLocalRef(bm_cls);
    jclass p_cls = env->FindClass("java/lang/Class");
    jmethodID p_is_prim_mid = env->GetMethodID(p_cls, "isPrimitive", "()Z");
    jmethodID p_get_name_mid = env->GetMethodID(p_cls, "getName", "()Ljava/lang/String;");
    env->DeleteLocalRef(p_cls);
    auto param_array = static_cast<jobjectArray>(
            env->CallObjectMethod(backup_method, get_params_mid));
    const jsize param_count =
            (param_array != nullptr) ? env->GetArrayLength(param_array) : 0;

    for (jsize i = 0; i < argc; ++i) {
        jobject arg = env->GetObjectArrayElement(args, i);
        if (i >= param_count) {
            jargs[i].l = arg;
            jarg_refs[i] = (arg != nullptr);
            continue;
        }
        auto pcls = static_cast<jclass>(env->GetObjectArrayElement(param_array, i));
        if (pcls == nullptr || !env->CallBooleanMethod(pcls, p_is_prim_mid)) {
            // 引用参数（或解析失败时兜底按引用传）
            jargs[i].l = arg;
            jarg_refs[i] = (arg != nullptr);
            if (pcls != nullptr) env->DeleteLocalRef(pcls);
            continue;
        }
        auto pname_str = static_cast<jstring>(env->CallObjectMethod(pcls, p_get_name_mid));
        const char *pname = env->GetStringUTFChars(pname_str, nullptr);
        env->DeleteLocalRef(pcls);

        // unbox：null 一律按 0/默认值处理
        jclass box_cls = (arg != nullptr) ? env->GetObjectClass(arg) : nullptr;
        if (strcmp(pname, "boolean") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "booleanValue", "()Z") : nullptr;
            jargs[i].z = (arg != nullptr && m != nullptr) ? env->CallBooleanMethod(arg, m) : JNI_FALSE;
        } else if (strcmp(pname, "byte") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "byteValue", "()B") : nullptr;
            jargs[i].b = (arg != nullptr && m != nullptr) ? env->CallByteMethod(arg, m) : 0;
        } else if (strcmp(pname, "char") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "charValue", "()C") : nullptr;
            jargs[i].c = (arg != nullptr && m != nullptr) ? env->CallCharMethod(arg, m) : 0;
        } else if (strcmp(pname, "short") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "shortValue", "()S") : nullptr;
            jargs[i].s = (arg != nullptr && m != nullptr) ? env->CallShortMethod(arg, m) : 0;
        } else if (strcmp(pname, "int") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "intValue", "()I") : nullptr;
            jargs[i].i = (arg != nullptr && m != nullptr) ? env->CallIntMethod(arg, m) : 0;
        } else if (strcmp(pname, "long") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "longValue", "()J") : nullptr;
            jargs[i].j = (arg != nullptr && m != nullptr) ? env->CallLongMethod(arg, m) : 0;
        } else if (strcmp(pname, "float") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "floatValue", "()F") : nullptr;
            jargs[i].f = (arg != nullptr && m != nullptr) ? env->CallFloatMethod(arg, m) : 0;
        } else if (strcmp(pname, "double") == 0) {
            jmethodID m = box_cls != nullptr ? env->GetMethodID(box_cls, "doubleValue", "()D") : nullptr;
            jargs[i].d = (arg != nullptr && m != nullptr) ? env->CallDoubleMethod(arg, m) : 0;
        } else {
            jargs[i].l = arg;
            jarg_refs[i] = (arg != nullptr);
        }
        if (box_cls != nullptr) env->DeleteLocalRef(box_cls);
        env->ReleaseStringUTFChars(pname_str, pname);
        env->DeleteLocalRef(pname_str);
    }
    if (param_array != nullptr) env->DeleteLocalRef(param_array);

    jclass target_cls = nullptr;
    if (is_static) {
        if (this_object != nullptr) {
            target_cls = env->GetObjectClass(this_object);
        } else {
            jclass m_cls = env->GetObjectClass(backup_method);
            jmethodID get_decl_mid = env->GetMethodID(m_cls, "getDeclaringClass", "()Ljava/lang/Class;");
            target_cls = static_cast<jclass>(env->CallObjectMethod(backup_method, get_decl_mid));
            env->DeleteLocalRef(m_cls);
        }
    }

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
        // Preserve a pending target exception; JNI class lookup would abort ART.
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Integer");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(I)Ljava/lang/Integer;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_j = [&](jlong v) {
        // Preserve a pending target exception; JNI class lookup would abort ART.
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Long");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(J)Ljava/lang/Long;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_f = [&](jfloat v) {
        // Preserve a pending target exception; JNI class lookup would abort ART.
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Float");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(F)Ljava/lang/Float;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_d = [&](jdouble v) {
        // Preserve a pending target exception; JNI class lookup would abort ART.
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Double");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(D)Ljava/lang/Double;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_byte = [&](jbyte v) {
        // Preserve a pending target exception; JNI class lookup would abort ART.
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Byte");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(B)Ljava/lang/Byte;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_char = [&](jchar v) {
        // Preserve a pending target exception; JNI class lookup would abort ART.
        if (env->ExceptionCheck()) return static_cast<jobject>(nullptr);
        jclass c = env->FindClass("java/lang/Character");
        jmethodID m = env->GetStaticMethodID(c, "valueOf", "(C)Ljava/lang/Character;");
        jobject r = env->CallStaticObjectMethod(c, m, v);
        env->DeleteLocalRef(c);
        return r;
    };
    auto box_short = [&](jshort v) {
        // Preserve a pending target exception; JNI class lookup would abort ART.
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
        jclass backup_decl_cls = nullptr;
        jclass m_cls = env->GetObjectClass(backup_method);
        jmethodID get_decl_mid = env->GetMethodID(m_cls, "getDeclaringClass", "()Ljava/lang/Class;");
        backup_decl_cls = static_cast<jclass>(env->CallObjectMethod(backup_method, get_decl_mid));
        env->DeleteLocalRef(m_cls);

        switch (type_code) {
            case 'V': env->CallNonvirtualVoidMethodA(this_object, backup_decl_cls, mid, jargs.data()); break;
            case 'Z': result = box_b(env->CallNonvirtualBooleanMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'I': result = box_i(env->CallNonvirtualIntMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'J': result = box_j(env->CallNonvirtualLongMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'F': result = box_f(env->CallNonvirtualFloatMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'D': result = box_d(env->CallNonvirtualDoubleMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'B': result = box_byte(env->CallNonvirtualByteMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'C': result = box_char(env->CallNonvirtualCharMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            case 'S': result = box_short(env->CallNonvirtualShortMethodA(this_object, backup_decl_cls, mid, jargs.data())); break;
            default: result = env->CallNonvirtualObjectMethodA(this_object, backup_decl_cls, mid, jargs.data()); break;
        }
        if (backup_decl_cls != nullptr) {
            env->DeleteLocalRef(backup_decl_cls);
        }
    }

    for (jsize i = 0; i < argc; ++i) {
        if (jarg_refs[i] && jargs[i].l != nullptr) env->DeleteLocalRef(jargs[i].l);
    }
    if (target_cls != nullptr && target_cls != this_object) {
        env->DeleteLocalRef(target_cls);
    }

    return result;
}

}  // namespace tcqt::zygisk
