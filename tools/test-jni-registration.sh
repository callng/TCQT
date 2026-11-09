#!/usr/bin/env bash
# Linux/WSL, JDK 21 and g++: real JNI failure handling without an Android device.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TEST_JDK=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}
OUTPUT=$(mktemp -d)
trap 'rm -rf "$OUTPUT"' EXIT
mkdir -p "$OUTPUT/android" "$OUTPUT/dalvik/system"
cat > "$OUTPUT/android/log.h" <<'HEADER'
#pragma once
enum { ANDROID_LOG_DEBUG = 3, ANDROID_LOG_INFO, ANDROID_LOG_WARN, ANDROID_LOG_ERROR };
extern "C" int __android_log_print(int, const char *, const char *, ...);
HEADER
cat > "$OUTPUT/dalvik/system/DexFile.java" <<'JAVA'
package dalvik.system;
public class DexFile {}
JAVA
cat > "$OUTPUT/Fixtures.java" <<'JAVA'
class EntryGood {
    static native boolean nativeIsDebugLogging();
    static native boolean nativeArtInit();
    static native void nativeLog(int priority, String tag, String text);
}
class EntryBad {
    static native boolean nativeIsDebugLogging();
    static native boolean nativeArtInit();
    // An incompatible payload: the method exists with the wrong JNI signature.
    static native void nativeLog(String tag, String text);
}
class BridgeGood {
    static native long nativeGetArtMethod(java.lang.reflect.Executable method);
    static native int nativeHookMethod(long target, long backup, long bridge);
    static native int nativeUnhookMethod(long target, long backup);
    static native boolean nativeTrustDexFile(dalvik.system.DexFile file);
    static native Object nativeInvokeBackup(java.lang.reflect.Method method, Object self, Object[] args);
}
class BridgeBad {
    static native long nativeGetArtMethod(java.lang.reflect.Executable method);
    static native int nativeHookMethod(long target, long backup, long bridge);
    // Also exercise failure after the first methods were already registered.
}
JAVA
"$TEST_JDK/bin/javac" -d "$OUTPUT" "$OUTPUT/Fixtures.java" "$OUTPUT/dalvik/system/DexFile.java"
"${CXX:-g++}" -std=c++20 -Wall -Wextra -Werror \
  -I "$OUTPUT" -I "$TEST_JDK/include" -I "$TEST_JDK/include/linux" \
  -I "$ROOT/app/src/main/cpp" \
  "$ROOT/app/src/test/cpp/jni_registration_test.cpp" \
  "$ROOT/app/src/main/cpp/zygisk/jni_bridge.cpp" \
  -L "$TEST_JDK/lib/server" -Wl,-rpath,"$TEST_JDK/lib/server" -ljvm -o "$OUTPUT/test"
"$OUTPUT/test" "$OUTPUT"
