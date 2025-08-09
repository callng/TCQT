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
-keep class com.owo233.tcqt.MainEntry implements de.robv.android.xposed.IXposedHookLoadPackage
-keep class com.owo233.tcqt.MainEntry {
    public void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}


# 保留所有 @Serializable 注解的类和它们的序列化器
#-keep @kotlinx.serialization.Serializable class * {
#    public static kotlinx.serialization.KSerializer serializer(...);
#}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留 IAction 实现类的无参构造
-keepclassmembers class * implements com.owo233.tcqt.ext.IAction {
    public <init>();
}

# 保留 IAction 接口
-keep interface com.owo233.tcqt.ext.IAction

# 保留 AlwaysRunAction 接口
-keep interface com.owo233.tcqt.ext.AlwaysRunAction

# 保留 ActionProcess 枚举
-keep enum com.owo233.tcqt.ext.ActionProcess

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
-keepclassmembers class com.owo233.tcqt.entries.**OuterClass$** {
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

-dontwarn java.beans.**
-obfuscationdictionary obf-dict.txt
-classobfuscationdictionary obf-dict.txt
-packageobfuscationdictionary obf-dict.txt
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

-keepattributes LineNumberTable,SourceFile