package com.owo233.tcqt.activity

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.owo233.tcqt.HookEnv

@Suppress("DEPRECATION")
abstract class BaseComposeActivity : ComponentActivity() {

    protected var isDarkTheme by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupTransparentStatusBar()

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        isDarkTheme = HookEnv.isNightMode()
        updateStatusBarAppearance()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isDarkTheme = HookEnv.isNightMode()
        updateStatusBarAppearance()
    }

    private fun setupTransparentStatusBar() {
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
        }
    }

    private fun updateStatusBarAppearance() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
    }

    private fun applyTheme(context: Context) {
        val isNight = HookEnv.isNightMode()
        val res = context.resources
        val config = Configuration(res.configuration)
        val mode = if (isNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = mode or (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        res.updateConfiguration(config, null)
    }
}
