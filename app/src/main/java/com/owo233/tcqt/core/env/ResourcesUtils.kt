package com.owo233.tcqt.core.env

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.owo233.tcqt.core.log.Log
import java.io.File
import java.util.WeakHashMap

internal object ResourcesUtils {

    private lateinit var resourcesLoader: ResourcesLoader
    private val legacyAssetPaths = WeakHashMap<AssetManager, String>()

    fun injectResourcesToContext(res: Resources) {
        if (ProcUtil.isMSF.not()) {
            val moduleApkPath = HookEnv.moduleApkPath
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    injectResourcesAboveApi30(res, moduleApkPath)
                } else {
                    injectResourcesBelowApi30(res, moduleApkPath)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
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
                injectResourcesBelowApi30(res, path)
                return
            }
        }
        // addLoaders 自身按 loader 去重；成功后无需再用 addAssetPath 重建资源表。
        try {
            res.addLoaders(resourcesLoader)
        } catch (e: IllegalArgumentException) {
            val expected1 =
                "Cannot modify resource loaders of ResourcesImpl not registered with ResourcesManager"
            if (expected1 == e.message) {
                Log.e("injectResourcesBelowApi30", e)
                injectResourcesBelowApi30(res, path)
            } else {
                throw e
            }
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun injectResourcesBelowApi30(res: Resources, path: String) {
        runCatching {
            val assetManager = res.assets
            if (legacyAssetPaths[assetManager] == path) return
            val method =
                AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            method.isAccessible = true
            val cookie = method.invoke(assetManager, path) as? Int ?: 0
            if (cookie != 0) legacyAssetPaths[assetManager] = path
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
