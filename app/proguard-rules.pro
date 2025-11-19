# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 保留Hook入口
-keep class com.owo233.tcqt.MainEntry {
    public <init>();
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
    public void initZygote(de.robv.android.xposed.IXposedHookZygoteInit$StartupParam);
}

# 保留所有 IAction 实现类的无参构造函数（用于 newInstance）
-keepclassmembers class * implements com.owo233.tcqt.ext.IAction {
    public <init>();
}

# 保留 Kotlin object 的 INSTANCE 字段（用于单例访问）
-keepclassmembers class * implements com.owo233.tcqt.ext.IAction {
    public static ** INSTANCE;
}

# 保留 IAction 接口
# -keep interface com.owo233.tcqt.ext.IAction

# 保留 ActionProcess 枚举
# -keep enum com.owo233.tcqt.ext.ActionProcess

# 大多数 volatile 字段是由 AtomicFU（Kotlin 的原子操作库）自动处理的，不应该被改名或删除。
-keepclassmembers class kotlinx.io.** {
    volatile <fields>;
}

-keepclassmembers class kotlinx.coroutines.io.** {
    volatile <fields>;
}

-keepclassmembernames class kotlinx.io.** {
    volatile <fields>;
}

-keepclassmembernames class kotlinx.coroutines.io.** {
    volatile <fields>;
}

# protobuf
-keepclassmembers class top.artmoe.inao.entries.**OuterClass$** {
    <fields>;
    <methods>;
}

# 忽略 kotlin.jvm.internal.Intrinsics 的检查
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

# 忽略 java.util.Objects 的检查
-assumenosideeffects class java.util.Objects {
    public static ** requireNonNull(...);
}

# 去除 DebugMetadataKt() 注释
-assumenosideeffects public final class kotlin.coroutines.jvm.internal.DebugMetadataKt {
   private static final kotlin.coroutines.jvm.internal.DebugMetadata getDebugMetadataAnnotation(kotlin.coroutines.jvm.internal.BaseContinuationImpl) return null;
}

# Kotlin function interfaces
-keep class kotlin.jvm.functions.Function1 { *; }

-dontwarn java.beans.**
-obfuscationdictionary obf-dict.txt
-classobfuscationdictionary obf-dict.txt
-packageobfuscationdictionary obf-dict.txt
-repackageclasses '5cfa840f.owo233.job'
-allowaccessmodification
-overloadaggressively

-renamesourcefileattribute *
-keepattributes !SourceFile,!SourceDebugExtension,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable
-dontnote kotlin.jvm.internal.SourceDebugExtension
-dontwarn kotlin.jvm.internal.SourceDebugExtension