package com.owo233.tcqt.utils

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.owo233.tcqt.R
import com.owo233.tcqt.hooks.base.hostInfo
import com.owo233.tcqt.hooks.base.modulePath
import java.io.File

fun injectRes(res: Resources = hostInfo.application.resources) {
    /*val assets = res.assets
    assets.invoke("addAssetPath", modulePath)
    try {
        val str = res.getString(R.string.res_inject_success)
        logI(msg = "Resources injection result: $str")
    } catch (_: Resources.NotFoundException) {
        logE(msg = "Resources injection failed")
    }*/

    runCatching {
        res.getString(R.string.res_inject_success)
        return
    }

    val sModulePath = modulePath
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        injectResourcesAboveApi30(res, sModulePath)
    } else {
        injectResourcesBelowApi30(res, sModulePath)
    }
}

@SuppressLint("DiscouragedPrivateApi")
private fun injectResourcesBelowApi30(res: Resources, path: String) {
    try {
        val assets = res.assets
        val addAssetPath = AssetManager::class.java.getDeclaredMethod(
            "addAssetPath",
            String::class.java
        ).apply { isAccessible = true }
        val cookie = addAssetPath.invoke(assets, path) as Int
        try {
            logD(msg = "Resources injection result: ${res.getString(R.string.res_inject_success)}, $cookie")
        } catch (e: Resources.NotFoundException) {
            logE(msg = "injectResourcesBelowApi30 Resources injection failed", cause = e)
        }
    } catch (e: Exception) {
        logE(msg = "injectResourcesBelowApi30 Resources injection failed", cause = e)
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun injectResourcesAboveApi30(res: Resources, path: String) {
    if (ResourcesLoaderHolderApi30.sResourcesLoader == null) {
        runCatching {
            ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                ResourcesLoader().apply {
                    addProvider(ResourcesProvider.loadFromApk(pfd))
                    ResourcesLoaderHolderApi30.sResourcesLoader = this
                }
            }
        }.onFailure { e ->
            logE(msg = "injectResourcesAboveApi30 Resources injection failed", cause = e)
            return
        }
    }

    SyncUtils.runOnUiThread {
        try {
            res.addLoaders(ResourcesLoaderHolderApi30.sResourcesLoader)
        } catch (e: IllegalArgumentException) {
            val expected1 = "Cannot modify resource loaders of ResourcesImpl not registered with ResourcesManager"
            if (expected1 == e.message) {
                logE(msg = "Resources injection failed, try injectResourcesAboveApi30", cause = e)
                injectResourcesAboveApi30(res, path)
            } else {
                throw e
            }
        }

        try {
            res.getString(R.string.res_inject_success)
        } catch (e: Resources.NotFoundException) {
            logE(msg = "injectResourcesAboveApi30 Resources injection failed", cause = e)
        }
    }
}

private object ResourcesLoaderHolderApi30 {
    var sResourcesLoader: ResourcesLoader? = null
}
