# 保留 XposedHelper 中创建的所有 Hook 对象
-keep,allowobfuscation class com.owo233.tcqt.utils.XposedHelper** { *; }

# 保留 IAction 及其实现类的无参构造和 INSTANCE
-keepclassmembers class * implements com.owo233.tcqt.ext.IAction {
    public <init>();
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

# protobuf 保留字段名和类型
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

-keepattributes LineNumberTable,SourceFile
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn androidx.constraintlayout.core.Metrics
-dontwarn androidx.constraintlayout.core.widgets.ConstraintAnchor$Type
-dontwarn androidx.constraintlayout.core.widgets.ConstraintWidget
-dontwarn androidx.constraintlayout.core.widgets.ConstraintWidgetContainer
-dontwarn java.beans.**

-classobfuscationdictionary obf-dict.txt
-obfuscationdictionary obf-dict.txt

-repackageclasses ''
-allowaccessmodification
-dontoptimize
-dontpreverify
-overloadaggressively
-renamesourcefileattribute *
