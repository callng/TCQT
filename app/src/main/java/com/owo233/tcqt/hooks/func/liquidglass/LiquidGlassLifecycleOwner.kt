package com.owo233.tcqt.hooks.func.liquidglass

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 把宿主 Activity 的生命周期「桥接」成一个 [LifecycleOwner]，供 ComposeView 驱动 Compose。
 */
internal class LiquidGlassLifecycleOwner(activity: Activity) : LifecycleOwner, SavedStateRegistryOwner {

    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        field = LifecycleRegistry(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    init {
        savedStateController.performRestore(null)
        activity.window.decorView.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    fun handle(event: Lifecycle.Event) {
        lifecycle.handleLifecycleEvent(event)
    }
}
