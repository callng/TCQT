package com.owo233.tcqt.loader.zygisk;

/**
 * Platform-type bridge: returns the receiver as-is. Kotlin sees the Java
 * return type {@code Object} as a flexible platform type, so the
 * {@code HookParam.thisObject} getter can yield {@code null} for static
 * methods without a Kotlin null-assertion (matching the Xposed semantics
 * where thisObject is {@code null} for static members).
 */
final class ZygiskThisObject {

    private ZygiskThisObject() {}

    static Object get(Object receiver) {
        return receiver;
    }
}
