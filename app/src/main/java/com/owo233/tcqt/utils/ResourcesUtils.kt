package com.owo233.tcqt.utils

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import java.io.File

internal object ResourcesUtils {

    private lateinit var resourcesLoader: ResourcesLoader

    fun injectResourcesToContext(context: Context, moduleApkPath: String) {
        val res = context.resources
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            injectResourcesAboveApi30(res, moduleApkPath)
        } else {
            injectResourcesBelowApi30(res, moduleApkPath)
        }
    }

    private fun injectResourcesAboveApi30(res: Resources, path: String) {
        if (::resourcesLoader.isInitialized.not()) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                pfd.use {
                    val provider = ResourcesProvider.loadFromApk(it)
                    resourcesLoader = ResourcesLoader().apply {
                        addProvider(provider)
                    }
                }
            }.onFailure {
                return
            }
        }
        runOnUiThread {
            try {
                res.addLoaders(resourcesLoader)
                injectResourcesBelowApi30(res, path)
            } catch (e: IllegalArgumentException) {
                val expected1 = "Cannot modify resource loaders of ResourcesImpl not registered with ResourcesManager"
                if (expected1 == e.message) {
                    Log.e("injectResourcesBelowApi30", e)
                    injectResourcesBelowApi30(res, path)
                } else {
                    throw e
                }
            }
        }
    }

    private fun injectResourcesBelowApi30(res: Resources, path: String) {
        runCatching {
            val assetManager = res.assets
            val method = AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            method.isAccessible = true
            method.invoke(assetManager, path)
        }
    }

    private fun runOnUiThread(task: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
        } else {
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed(task, 0L)
        }
    }
}
