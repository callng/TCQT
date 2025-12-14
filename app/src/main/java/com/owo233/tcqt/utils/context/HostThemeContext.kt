package com.owo233.tcqt.utils.context

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.owo233.tcqt.utils.ResourcesUtils
import androidx.appcompat.view.ContextThemeWrapper

class HostThemeContext @JvmOverloads constructor(
    base: Context,
    theme: Int,
    configuration: Configuration? = null
) : ContextThemeWrapper(base, theme) {

    private val overrideResources: Resources? =
        configuration?.let { base.createConfigurationContext(it).resources }

    private val inflaterDelegate = ContextInflaterDelegate(this)
    private val classLoaderDelegate = ContextClassLoaderDelegate()

    init {
        ResourcesUtils.injectResourcesToContext(resources)
    }

    override fun getResources(): Resources {
        return overrideResources ?: super.getResources()
    }

    override fun getSystemService(name: String): Any? {
        return if (LAYOUT_INFLATER_SERVICE == name) {
            inflaterDelegate.getInflater()
        } else {
            super.getSystemService(name)
        }
    }

    override fun getClassLoader(): ClassLoader {
        return classLoaderDelegate.getClassLoader(javaClass.classLoader!!)
    }
}
