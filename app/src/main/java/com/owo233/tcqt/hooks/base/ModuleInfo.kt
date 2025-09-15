package com.owo233.tcqt.hooks.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.XModuleResources
import com.owo233.tcqt.MainEntry
import com.owo233.tcqt.utils.logE
import com.owo233.tcqt.R
import com.owo233.tcqt.utils.logD
import java.lang.reflect.Method

lateinit var modulePath: String
lateinit var moduleRes: XModuleResources

val moduleClassLoader: ClassLoader = MainEntry::class.java.classLoader!!
var moduleLoadInit = false

@SuppressLint("DiscouragedPrivateApi")
internal fun resInjection(context: Context = hostInfo.application): Boolean {
    if (!::modulePath.isInitialized) {
        logE(msg = "modulePath is not initialized")
        return false
    }

    return runCatching {
        val res: Resources = context.resources
        val method = cachedAddAssetPath ?: AssetManager::class.java
            .getDeclaredMethod("addAssetPath", String::class.java)
            .apply { isAccessible = true }
            .also { cachedAddAssetPath = it }

        val result = method.invoke(res.assets, modulePath) as Int

        if (result > 0) {
            logD(msg = "resInjection: ${R.string.module_res_injection}")
            true
        } else {
            logE(msg = "resInjection failed: modulePath=$modulePath, id=$result")
            false
        }
    }.getOrElse {
        logE(msg = "resInjection exception: modulePath=$modulePath", cause = it)
        false
    }
}

@Volatile
private var cachedAddAssetPath: Method? = null
