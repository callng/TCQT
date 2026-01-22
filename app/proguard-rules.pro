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

# 保留 XposedHelper 中创建的所有 Hook 对象
-keep,allowobfuscation class com.owo233.tcqt.foundation.utils.XposedHelper** { *; }

# 保留所有 IAction 实现类的无参构造函数（用于 newInstance）
-keepclassmembers class * implements com.owo233.tcqt.actions.IAction {
    public <init>();
}

# 保留 Kotlin object 的 INSTANCE 字段（用于单例访问）
-keepclassmembers class * implements com.owo233.tcqt.actions.IAction {
    public static ** INSTANCE;
}

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
-keepclassmembers class top.artmoe.inao.entries.** {
    <fields>;
}

# 忽略 kotlin.jvm.internal.Intrinsics 的检查
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

# 忽略 java.util.Objects 的检查
-assumenosideeffects class java.util.Objects {
    ** requireNonNull(...);
}

# Preserve annotated Javascript interface methods.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn androidx.constraintlayout.core.Metrics
-dontwarn androidx.constraintlayout.core.widgets.ConstraintAnchor$Type
-dontwarn androidx.constraintlayout.core.widgets.ConstraintWidget
-dontwarn androidx.constraintlayout.core.widgets.ConstraintWidgetContainer

-dontwarn java.beans.**
-obfuscationdictionary obf-dict.txt
-classobfuscationdictionary obf-dict.txt
-packageobfuscationdictionary obf-dict.txt
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

-renamesourcefileattribute *
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
# -keepattributes SourceFile,LineNumberTable
-dontpreverify
-dontnote kotlin.jvm.internal.SourceDebugExtension
-dontwarn kotlin.jvm.internal.SourceDebugExtension
