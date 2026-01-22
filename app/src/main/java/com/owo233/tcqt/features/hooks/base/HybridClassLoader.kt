package com.owo233.tcqt.features.hooks.base

import android.annotation.SuppressLint
import android.content.Context
import com.owo233.tcqt.foundation.utils.log.Log

object HybridClassLoader : ClassLoader(Context::class.java.classLoader) {

    private var loaderParent: ClassLoader? = null

    private var hostLoader: ClassLoader? = null

    @SuppressLint("DiscouragedPrivateApi")
    fun inject(self: ClassLoader) {
        if (self.parent == this) return

        val originalParent = self.parent
        setLoaderParent(originalParent)

        try {
            val fParent = ClassLoader::class.java.getDeclaredField("parent")
            fParent.isAccessible = true
            fParent.set(self, this)
            Log.i("Inject success: HybridClassLoader is now the parent of $self")
        } catch (e: Exception) {
            Log.e("Inject failed", e)
        }
    }

    fun setHostClassLoader(loader: ClassLoader?) {
        hostLoader = loader
    }

    private fun setLoaderParent(loader: ClassLoader?) {
        loaderParent = if (loader == this::class.java.classLoader) null else loader
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        try {
            return parent.loadClass(name)
        } catch (_: ClassNotFoundException) {
        }

        if (name.startsWith("com.owo233.tcqt.")) {
            return loaderParent!!.loadClass(name)
        }

        if (isConflictingClass(name)) {
            throw ClassNotFoundException("$name is conflicting, delegate to module self.")
        }

        loaderParent?.let {
            try {
                return it.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        if (hostLoader != null && isHostClass(name)) {
            try {
                return hostLoader!!.loadClass(name)
            } catch (_: ClassNotFoundException) {
            }
        }

        throw ClassNotFoundException(name)
    }

    // 宿主类加载器的类名前缀
    private val HOST_PREFIXES = arrayOf(
        "androidx.constraintlayout.",
        "com.tencent.",
        "com.qq.",
        "com.qzone.",
        "mqq.",
        "oicq.",
        "tencent.",
    )

    // 冲突类加载器的类名前缀
    private val CONFLICTING_PREFIXES = arrayOf(
        "android.support.",
        "androidx.appcompat.",
        "kotlin.",
        "kotlinx.",
        "com.android.tools.r8.",
        "com.google.android.",
        "com.google.gson.",
        "com.google.common.",
        "com.google.protobuf.",
        "com.microsoft.appcenter.",
        "org.intellij.lang.annotations.",
        "org.jetbrains.annotations.",
        "com.bumptech.glide.",
        "com.google.errorprone.annotations.",
        "org.jf.dexlib2.",
        "org.jf.util.",
        "javax.annotation.",
        "_COROUTINE."
    )

    private fun isHostClass(name: String): Boolean {
        return HOST_PREFIXES.any { name.startsWith(it) }
    }

    private fun isConflictingClass(name: String): Boolean {
        return CONFLICTING_PREFIXES.any { name.startsWith(it) }
    }
}
