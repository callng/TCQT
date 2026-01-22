package com.owo233.tcqt.foundation.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import com.owo233.tcqt.bootstrap.HookEnv
import com.owo233.tcqt.foundation.utils.ResourcesUtils
import com.tencent.mobileqq.vas.theme.api.ThemeUtil

class CommonContextWrapper @JvmOverloads constructor(
    base: Context,
    themeResId: Int,
    configuration: Configuration? = null
) : ContextThemeWrapper(base, themeResId) {

    private var overrideResources: Resources? = null

    private val cachedInflater: LayoutInflater by lazy {
        LayoutInflater.from(getBaseContextImpl(baseContext)).cloneInContext(this)
    }

    private val classLoaderRef: ClassLoader by lazy {
        SavedInstanceStatePatchedClassReferencer(CommonContextWrapper::class.java.classLoader!!)
    }

    init {
        if (configuration != null) {
            overrideResources = base.createConfigurationContext(configuration).resources
        }
        ResourcesUtils.injectResourcesToContext(resources)
    }

    override fun getClassLoader(): ClassLoader {
        return classLoaderRef
    }

    override fun getResources(): Resources {
        return overrideResources ?: super.getResources()
    }

    override fun getSystemService(name: String): Any? {
        if (LAYOUT_INFLATER_SERVICE == name) {
            return cachedInflater
        }
        return baseContext.getSystemService(name)
    }

    companion object {
        @SuppressLint("PrivateApi")
        private fun getBaseContextImpl(context: Context): Context {
            var currentCtx = context
            val contextImplClass = try {
                Class.forName("android.app.ContextImpl")
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }

            while (currentCtx is ContextWrapper) {
                val base = currentCtx.baseContext ?: break
                currentCtx = base
            }

            if (!contextImplClass.isInstance(currentCtx)) {
                throw UnsupportedOperationException("Unable to get base context from ${currentCtx.javaClass.name}")
            }
            return currentCtx
        }

        @SuppressLint("Recycle")
        fun isAppCompatContext(context: Context): Boolean {
            if (!checkContextClassLoader(context)) return false

            val attrs = intArrayOf(androidx.appcompat.R.attr.windowActionBar)
            val a = context.obtainStyledAttributes(attrs)
            return try {
                a.hasValue(0)
            } finally {
                a.recycle()
            }
        }

        /*fun isMaterialDesignContext(context: Context): Boolean {
            if (!isAppCompatContext(context)) return false

            val attrs = intArrayOf(com.google.android.material.R.attr.colorPrimaryVariant)
            val a = context.obtainStyledAttributes(attrs)
            return try {
                a.hasValue(0)
            } finally {
                a.recycle()
            }
        }*/

        fun checkContextClassLoader(context: Context): Boolean {
            val cl = context.classLoader ?: return false
            return try {
                cl.loadClass(AppCompatActivity::class.java.name) == AppCompatActivity::class.java
            } catch (_: ClassNotFoundException) {
                false
            }
        }

        private fun recreateNightModeConfig(base: Context, uiNightMode: Int): Configuration? {
            val baseConfig = base.resources.configuration ?: return null
            val currentNightMode = baseConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == uiNightMode) {
                return null
            }

            return Configuration(baseConfig).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or uiNightMode
            }
        }

        fun Context.toCompatibleContext(): Context {
            if (isAppCompatContext(this)) return this

            val themeId = if (isNightMode()) {
                androidx.appcompat.R.style.Theme_AppCompat
            } else {
                androidx.appcompat.R.style.Theme_AppCompat_Light
            }
            val nightModeMask = getNightModeMasked()

            return CommonContextWrapper(
                this,
                themeId,
                recreateNightModeConfig(this, nightModeMask)
            )
        }

        private fun getNightModeMasked(): Int {
            return if (ThemeUtil.isNowThemeIsNight(null, true, null)
                || (if (HookEnv.isQQ()) ThemeUtil.isThemeNightModeV2() else false)) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
        }

        private fun isNightMode(): Boolean {
            return getNightModeMasked() == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
