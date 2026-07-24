package com.owo233.tcqt.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.owo233.tcqt.activity.SettingTheme
import com.owo233.tcqt.ui.CommonContextWrapper.Companion.toCompatibleContext
import android.graphics.Color as AndroidColor

class TCQTDialogLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
            return
        }
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}

@Suppress("DEPRECATION")
abstract class CompatibleComposeDialog(
    context: Context
) : Dialog(context.toCompatibleContext(), android.R.style.Theme_Material_Light_NoActionBar) {

    private val dialogLifecycleOwner = TCQTDialogLifecycleOwner()
    protected var isVisible by mutableStateOf(false)
    private var composeView: ComposeView? = null
    private var isDismissing = false

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(true)
        configureWindow()
    }

    protected open fun configureWindow() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            
            statusBarColor = AndroidColor.TRANSPARENT
            navigationBarColor = AndroidColor.TRANSPARENT

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }

            WindowCompat.setDecorFitsSystemWindows(this, false)

            setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.CENTER)
            
            // Clear default window dimming to draw and fade the dim background ourselves in Compose
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            
            // Remove default window animations to prevent conflicts with Compose transitions
            setWindowAnimations(0)
            
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(dialogLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(dialogLifecycleOwner)
        }

        setContentView(composeView!!)

        window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(dialogLifecycleOwner)
            decorView.setViewTreeSavedStateRegistryOwner(dialogLifecycleOwner)
        }

        composeView?.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides dialogLifecycleOwner) {
                SettingTheme {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        isVisible = true
                    }
                    DialogContent()
                }
            }
        }
    }

    @Composable
    protected abstract fun DialogContent()

    protected fun dismissWithAnimation() {
        dismiss()
    }

    override fun onStart() {
        super.onStart()
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        dialogLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onStop()
    }

    override fun dismiss() {
        if (!isDismissing) {
            isDismissing = true
            isVisible = false
            window?.decorView?.postDelayed({
                super.dismiss()
            }, 300)
        } else {
            composeView = null
            super.dismiss()
        }
    }
}
