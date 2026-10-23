package com.owo233.tcqt.core.env

import android.annotation.SuppressLint
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal object NativeLibs {

    private const val TAG = "NativeLibs"

    @Volatile
    private var nativeDir: File? = null

    private val loadState = ConcurrentHashMap<String, Boolean>()

    fun register(dir: File) {
        nativeDir = dir
    }

    fun pathOf(name: String): String? {
        val dir = nativeDir ?: return null
        val so = File(dir, "lib$name.so")
        return so.takeIf { it.isFile }?.absolutePath
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun load(name: String): Boolean {
        if (loadState[name] == true) return true
        val ok = try {
            val dir = nativeDir
            if (dir != null) {
                val so = File(dir, "lib$name.so")
                check(so.isFile) {
                    "lib$name.so not extracted under ${dir.absolutePath}"
                }
                System.load(so.absolutePath)
            } else {
                System.loadLibrary(name)
            }
            true
        } catch (t: Throwable) {
            Log.e(TAG, "load lib$name.so failed", t)
            false
        }
        // A missing extraction or ClassLoader path can become available later.
        if (ok) loadState[name] = true
        return ok
    }
}
