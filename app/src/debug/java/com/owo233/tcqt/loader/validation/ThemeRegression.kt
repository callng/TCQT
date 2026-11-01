package com.owo233.tcqt.loader.validation

import android.app.Activity
import com.owo233.tcqt.core.config.ModuleThemeMode
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.config.ThemeSettings
import org.json.JSONObject
import java.io.File

/** Reversible theme-only UI validation; absent keys are removed on restore. */
internal object ThemeRegression {
    fun apply(activity: Activity, action: String) {
        val snapshot = File(activity.filesDir, ".tcqt/validation/theme-original.json")
        if (action == "themeReset") {
            if (snapshot.isFile) {
                val original = JSONObject(snapshot.readText())
                if (original.getBoolean("hasTheme")) TCQTSetting.setString(ThemeSettings.KEY_THEME_MODE, original.getString("theme"))
                else TCQTSetting.remove(ThemeSettings.KEY_THEME_MODE)
                if (original.getBoolean("hasMonet")) TCQTSetting.setBoolean(ThemeSettings.KEY_MONET_ENABLED, original.getBoolean("monet"))
                else TCQTSetting.remove(ThemeSettings.KEY_MONET_ENABLED)
                check(snapshot.delete())
            }
        } else {
            if (!snapshot.exists()) {
                snapshot.parentFile!!.mkdirs()
                snapshot.writeText(JSONObject()
                    .put("hasTheme", TCQTSetting.containsKey(ThemeSettings.KEY_THEME_MODE))
                    .put("theme", TCQTSetting.getRawString(ThemeSettings.KEY_THEME_MODE))
                    .put("hasMonet", TCQTSetting.containsKey(ThemeSettings.KEY_MONET_ENABLED))
                    .put("monet", TCQTSetting.getBoolean(ThemeSettings.KEY_MONET_ENABLED)).toString())
            }
            ThemeSettings.setThemeMode(ModuleThemeMode.Dark)
            ThemeSettings.monetEnabled = action == "themeMonet"
        }
        activity.recreate()
    }
}
