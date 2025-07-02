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
-keep @kotlinx.serialization.Serializable class * {
    public static kotlinx.serialization.KSerializer serializer(...);
}

# 保留 IAction 实现类的无参构造
-keepclassmembers class * implements com.owo233.tcqt.ext.IAction {
    public <init>();
}

# 保留 IAction 接口
-keep interface com.owo233.tcqt.ext.IAction

# 保留 ActionProcess 枚举
-keep enum com.owo233.tcqt.ext.ActionProcess

# 保留所有类中名为 top 的字段
-keepclassmembers class * {
    volatile long top;
}

-obfuscationdictionary obf-dict.txt
-classobfuscationdictionary obf-dict.txt
-packageobfuscationdictionary obf-dict.txt
-repackageclasses ''
-allowaccessmodification
-overloadaggressively