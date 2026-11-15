package com.owo233.tcqt.ui.component

import android.content.Context
import android.content.res.Configuration
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.owo233.tcqt.core.env.CommonContextWrapper.Companion.toCompatibleContext
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.ui.theme.SettingTheme

/** Embedded module UI, independent of the host's AndroidX class loader and view-tree owners. */
internal open class CompatibleComposeView(
    context: Context,
    private val followHostTheme: Boolean = false,
    content: @Composable () -> Unit,
) : FrameLayout(context.toCompatibleContext()) {
    private var owner: TCQTDialogLifecycleOwner? = null
    private var hostDarkTheme by mutableStateOf(followHostTheme && HookEnv.isNightMode())
    private val compose = ComposeView(this.context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            if (followHostTheme) {
                SettingTheme(themeMode = ModuleThemeMode.System, systemDarkTheme = hostDarkTheme, content = content)
            } else {
                SettingTheme(content = content)
            }
        }
    }

    init { addView(compose, LayoutParams(-1, -2)) }

    override fun onAttachedToWindow() {
        if (followHostTheme) hostDarkTheme = HookEnv.isNightMode()
        // Create fresh owners before the child attaches, including after RecyclerView recycling.
        owner = TCQTDialogLifecycleOwner().also {
            setViewTreeLifecycleOwner(it)
            setViewTreeSavedStateRegistryOwner(it)
            // The default window recomposer looks on QQ's content root, outside this subtree.
            compose.setParentCompositionContext(compose.createLifecycleAwareWindowRecomposer(lifecycle = it.lifecycle))
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        super.onAttachedToWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (followHostTheme && hasWindowFocus) hostDarkTheme = HookEnv.isNightMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (followHostTheme) hostDarkTheme = HookEnv.isNightMode()
    }

    override fun onDetachedFromWindow() {
        compose.disposeComposition()
        compose.setParentCompositionContext(null)
        owner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        owner = null
        super.onDetachedFromWindow()
    }
}
