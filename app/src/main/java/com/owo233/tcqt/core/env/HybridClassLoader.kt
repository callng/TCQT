package com.owo233.tcqt.core.env

import android.annotation.SuppressLint
import android.content.Context
import com.owo233.tcqt.core.log.Log
import dalvik.system.BaseDexClassLoader
import dalvik.system.DexFile

object HybridClassLoader : ClassLoader(Context::class.java.classLoader) {

    private var loaderParent: ClassLoader? = null

    private var hostLoader: ClassLoader? = null

    @Volatile
    private var moduleClasses: Set<String> = emptySet()

    /** Actual DEX names, including R8 names; enumerating them does not load or initialize classes. */
    internal fun moduleClassNames(): Set<String> = moduleClasses

    @SuppressLint("DiscouragedPrivateApi")
    fun inject(self: ClassLoader) {
        if (self.parent == this) return

        val originalParent = self.parent
        setLoaderParent(originalParent)
        // Resolve the miss type while the module still has its original parent.
        // Constructing it for the first time from loadClass would recurse.
        ModuleClassLookupMiss(javaClass.name)

        moduleClasses = runCatching {
            if (self !is BaseDexClassLoader) return@runCatching emptySet()
            val pathListField = BaseDexClassLoader::class.java.getDeclaredField("pathList")
                .apply { isAccessible = true }
            val pathList = pathListField.get(self)!!
            val elements = pathList.javaClass.getDeclaredField("dexElements")
                .apply { isAccessible = true }.get(pathList) as Array<*>
            buildSet {
                elements.filterNotNull().forEach { element ->
                    runCatching {
                        val dex = element.javaClass
                            .getDeclaredField("dexFile")
                            .apply { isAccessible = true }
                            .get(element) as? DexFile

                        dex?.entries()?.let { entries ->
                            while (entries.hasMoreElements()) {
                                add(entries.nextElement().replace('/', '.'))
                            }
                        }
                    }.onFailure {
                        Log.w("无法读取 DEX Element", it)
                    }
                }
            }
        }.getOrElse {
            Log.w("无法枚举模块 DEX 类，使用普通类加载路径", it)
            emptySet()
        }

        try {
            val fParent = ClassLoader::class.java.getDeclaredField("parent")
            fParent.isAccessible = true
            fParent.set(self, this)
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
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // The child owns these exact names. A normal parent miss would search the
        // system DEX and build several exception stacks for every first UI class.
        // Return control to the child without changing platform/host class routing.
        // R8 can ship android.* API modelling stubs in the DEX. They must never
        // shadow the device implementation (e.g. WindowInsetsAnimation.Callback).
        if (name in moduleClasses && !isPlatformClass(name)) throw ModuleClassLookupMiss(name)
        return super.loadClass(name, resolve)
    }

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        // ClassLoader.loadClass 已经查询过 parent；首次加载 Compose 的大量类时，
        // 再查一次只会为每个模块类额外制造一次 ClassNotFoundException。
        if (name.startsWith("com.owo233.tcqt.")) {
            return loaderParent!!.loadClass(name)
        }

        if (isConflictingClass(name)) {
            throw ModuleClassLookupMiss(name)
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

        throw ModuleClassLookupMiss(name)
    }

    // 宿主类加载器的类名前缀
    private val HOST_PREFIXES = arrayOf(
        "androidx.constraintlayout.",
        "cooperation.qzone.",
        "com.tencent.",
        "com.qq.",
        "com.qzone.",
        "mqq.",
        "oicq.",
        "tencent.",
        "NS_MINI_INTERFACE",
        "NS_COMM"
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

    private fun isPlatformClass(name: String): Boolean =
        (name.startsWith("android.") && !name.startsWith("android.support.")) ||
            PLATFORM_PREFIXES.any(name::startsWith)

    private val PLATFORM_PREFIXES = arrayOf(
        "java.", "javax.", "dalvik.", "libcore.", "sun.", "com.android.",
        "org.apache.http.", "org.json.", "org.xml.", "org.w3c.",
    )

    /** A parent lookup miss is control flow, not a failure worth collecting a stack for. */
    private class ModuleClassLookupMiss(name: String) : ClassNotFoundException(name) {
        override fun fillInStackTrace(): Throwable = this
    }
}
