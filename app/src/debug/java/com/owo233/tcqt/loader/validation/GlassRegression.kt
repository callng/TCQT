package com.owo233.tcqt.loader.validation

import android.app.Application
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.features.appearance.liquidglass.FloatingBarHostLayout
import com.owo233.tcqt.features.appearance.liquidglass.FloatingBottomBarConfigStore
import com.owo233.tcqt.features.appearance.liquidglass.GlassBarHostLayout
import com.owo233.tcqt.features.appearance.liquidglass.GlassBarInstaller
import com.owo233.tcqt.features.appearance.liquidglass.NewViewBarInstaller
import com.owo233.tcqt.features.appearance.liquidglass.PageViewChanges
import com.owo233.tcqt.features.appearance.liquidglass.QQTabLocator
import com.owo233.tcqt.features.appearance.liquidglass.TabViewOperations
import com.owo233.tcqt.features.appearance.liquidglass.TabViewSnapshot
import com.owo233.tcqt.features.appearance.liquidglass.BottomBarTheme
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import androidx.core.view.isInvisible
import androidx.core.graphics.drawable.toDrawable

internal object GlassRegression {
    private var nativeTab: WeakReference<ViewGroup>? = null
    private var nativeParent: WeakReference<ViewGroup>? = null
    private var orientationHook: com.owo233.tcqt.core.hook.Unhook? = null

    fun synthetic(app: Application): Int {
        val parent = FrameLayout(app)
        parent.addView(View(app), FrameLayout.LayoutParams(10, 10))
        val tab = LinearLayout(app).apply { gravity = 5; setPadding(1, 2, 3, 4); alpha = .7f }
        val label = TextView(app).apply { text = "Tab"; translationY = 3f; background = ColorDrawable(123) }
        tab.addView(label, LinearLayout.LayoutParams(54, 32, 1f))
        parent.addView(tab, FrameLayout.LayoutParams(200, 64))
        val originalGravity = tab.gravity
        val snapshot = TabViewSnapshot(tab)
        val host = FrameLayout(app)
        parent.removeView(tab); parent.addView(host); host.addView(tab)
        label.layoutParams.width = 999; label.layoutParams.height = 888
        (label.layoutParams as LinearLayout.LayoutParams).weight = 0f
        label.background = null; label.translationY = 100f
        tab.gravity = 3; tab.alpha = 1f; tab.setPadding(0, 0, 0, 0)
        TabViewOperations.unclipAncestors(parent)
        snapshot.restore(); snapshot.restore()
        check(tab.parent === parent && parent.indexOfChild(tab) == 1)
        check(label.layoutParams.width == 54 && label.layoutParams.height == 32)
        check((label.layoutParams as LinearLayout.LayoutParams).weight == 1f)
        check(label.background != null && label.translationY == 3f)
        check(tab.gravity == originalGravity) { "gravity ${tab.gravity} != $originalGravity" }
        check(tab.alpha == .7f) { "alpha ${tab.alpha}" }
        check(tab.paddingBottom == 4) { "padding ${tab.paddingBottom}" }
        check(parent.clipChildren) { "ancestor clipping not restored" }
        parent.removeView(host)

        val movingSnapshot = TabViewSnapshot(tab)
        tab.translationY = 32f; tab.alpha = .4f; label.translationY = 99f
        movingSnapshot.restore(preserveNativeMotion = true)
        check(tab.translationY == 32f && tab.alpha == .4f && label.translationY == 3f)
        tab.translationY = 0f; tab.alpha = .7f

        val changes = PageViewChanges()
        changes.remember(tab)
        var callbacks = 0
        changes.listen(tab) { _, _, _, _, _, _, _, _, _ -> callbacks++ }
        tab.setPadding(1, 2, 3, 100); tab.clipToPadding = false; tab.layoutParams.height = -1
        changes.restore(); changes.restore()
        tab.layout(0, 0, 10, 10)
        check(callbacks == 0 && tab.paddingBottom == 4 && tab.clipToPadding && tab.layoutParams.height == 64)

        label.layout(0, 0, 20, 20)
        check(TabViewOperations.leafContentWidth(tab) == 20)
        label.visibility = View.GONE
        check(TabViewOperations.leafContentWidth(tab) == 0)

        val scene = FrameLayout(app)
        val wrapper = FrameLayout(app).apply { setPadding(14, 14, 14, 14) }
        val native = LinearLayout(app)
        val surface = View(app)
        val indicator = View(app)
        wrapper.addView(surface, FrameLayout.LayoutParams(-1, -1))
        wrapper.addView(indicator, FrameLayout.LayoutParams(76, 56))
        wrapper.addView(native, FrameLayout.LayoutParams(312, 64))
        scene.addView(wrapper, FrameLayout.LayoutParams(340, 92).apply { gravity = android.view.Gravity.BOTTOM })
        val widthSpec = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY)
        scene.measure(widthSpec, heightSpec); scene.layout(0, 0, 400, 1000)
        repeat(12) {
            native.visibility = View.VISIBLE
            native.translationY = 64f; native.alpha = .6f
            check(TabViewOperations.followNativeBar(wrapper, native, surface, indicator, 4f))
            check(wrapper.translationY + surface.translationY >= 986f)
            check(surface.alpha == .6f && indicator.alpha == .6f && indicator.translationY == 68f)
            native.translationY = 0f; native.visibility = View.GONE
            check(!TabViewOperations.followNativeBar(wrapper, native, surface, indicator, 4f))
            scene.measure(widthSpec, heightSpec); scene.layout(0, 0, 400, 1000)
            check(wrapper.isInvisible && wrapper.height == 92)
            native.visibility = View.VISIBLE; native.alpha = 1f
            check(TabViewOperations.followNativeBar(wrapper, native, surface, indicator, 4f))
            check(wrapper.translationY == 0f && surface.translationY == 0f && indicator.translationY == 4f)
        }

        for ((fill, text) in listOf(Color.rgb(246, 246, 250) to Color.DKGRAY,
            Color.rgb(43, 43, 46) to Color.LTGRAY, Color.rgb(240, 224, 236) to Color.rgb(89, 54, 81))) {
            val tabs = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(fill) }
            repeat(4) { index ->
                val cell = FrameLayout(app).apply { isSelected = index == 0 }
                cell.addView(UnreadBadge(app).apply { this.text = "99"; setTextColor(Color.WHITE); setBackgroundColor(Color.RED) })
                cell.addView(TextView(app).apply {
                    this.text = "Tab $index"
                    setTextColor(if (index == 0) Color.BLUE else text)
                    background = Color.TRANSPARENT.toDrawable()
                })
                tabs.addView(cell)
            }
            tabs.layout(0, 0, 312, 64)
            val originalBounds = android.graphics.Rect(3, 4, 300, 56)
            tabs.background.bounds = originalBounds
            val theme = BottomBarTheme.fromNative(tabs)
            check(theme.surface == fill && theme.foreground == text)
            check(theme.dark == (text == Color.LTGRAY))
            check(tabs.background.bounds == originalBounds)
            for (index in 0 until tabs.childCount) {
                (tabs.getChildAt(index) as ViewGroup).getChildAt(1).visibility = View.GONE
            }
            check(BottomBarTheme.fromNative(tabs).dark == (text == Color.LTGRAY))
            TabViewOperations.stripBarBackgrounds(tabs)
            check((tabs.getChildAt(0) as ViewGroup).getChildAt(0).background != null)
        }
        return 8
    }

    private class UnreadBadge(app: Application) : androidx.appcompat.widget.AppCompatTextView(app)

    @androidx.annotation.RequiresApi(33)
    fun action(app: Application, action: String): JSONObject {
        val configBackup = File(app.filesDir, ".tcqt/validation/glass-original.json")
        val orientationBackup = File(app.filesDir, ".tcqt/validation/glass-orientation.txt")
        if (action == "landscape" || action == "orientationRestore") {
            val activity = requireNotNull(HostBridge.topActivity())
            if (action == "landscape") {
                if (!orientationBackup.exists()) orientationBackup.writeText(activity.requestedOrientation.toString())
                // QQ requests portrait again during configuration changes. Restrict the temporary
                // override to this host Activity class and remove it before restoring its direction.
                if (orientationHook == null) {
                    val activityName = activity.javaClass.name
                    orientationHook = com.owo233.tcqt.core.hook.HookEngineManager.engine.hookBefore(
                        android.app.Activity::class.java.getDeclaredMethod("setRequestedOrientation", Int::class.javaPrimitiveType)
                    ) { param ->
                        if (param.thisObject?.javaClass?.name == activityName) {
                            param.args[0] = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                    }
                }
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else if (orientationBackup.exists()) {
                orientationHook?.unhook(); orientationHook = null
                activity.requestedOrientation = orientationBackup.readText().toInt()
                orientationBackup.delete()
            }
            return JSONObject().put("requested", action)
        }
        if (action == "restore") {
            NewViewBarInstaller.restore(); GlassBarInstaller.restore()
            nativeTab?.get()?.let { check(it.parent === nativeParent?.get()) }
            if (configBackup.isFile) {
                val original = JSONObject(configBackup.readText())
                val edits = mutableMapOf<String, Any>()
                original.keys().forEach { key ->
                    val entry = original.getJSONObject(key)
                    if (entry.getBoolean("exists")) edits[key] = entry.getInt("value") else TCQTSetting.remove(key)
                }
                TCQTSetting.applyEdits(edits)
                configBackup.delete()
            }
            return JSONObject().put("restored", true).put("hostSettingKeys", TCQTSetting.getAllKeys().size)
        }
        val activity = requireNotNull(HostBridge.topActivity())
        val tab = requireNotNull(QQTabLocator.locateTabView(activity.window.decorView))
        if (action == "inspect") {
            val host = tab.parent as? ViewGroup ?: error("Missing host")
            check(host is FloatingBarHostLayout || host is GlassBarHostLayout)
            check(host.isAttachedToWindow && host.width > 0 && host.height > 0)
            check(host.width <= host.rootView.width) { "Bar clipped by window: ${host.width} > ${host.rootView.width}" }
            val row = requireNotNull(QQTabLocator.findTabRow(tab))
            check(QQTabLocator.tabCount(row) > 0)
            return JSONObject().put("host", host.javaClass.simpleName).put("width", host.width)
                .put("height", host.height).put("tabs", QQTabLocator.tabCount(row))
                .put("viewportWidth", host.rootView.width).put("viewportHeight", host.rootView.height)
        }
        check(TCQTSetting.settingMap.isNotEmpty())
        val keys = listOf(FloatingBottomBarConfigStore.IMPLEMENTATION_KEY, FloatingBottomBarConfigStore.MODE_KEY,
            FloatingBottomBarConfigStore.SCALE_KEY, FloatingBottomBarConfigStore.POSITION_KEY)
        if (!configBackup.exists()) {
            val saved = JSONObject()
            keys.forEach { saved.put(it, JSONObject().put("exists", TCQTSetting.containsKey(it)).put("value", TCQTSetting.getInt(it))) }
            configBackup.writeText(saved.toString())
            nativeTab = WeakReference(tab)
            nativeParent = WeakReference(tab.parent as? ViewGroup)
        }
        when (action) {
            "classic", "newNormal", "newGlass" -> {
                NewViewBarInstaller.restore(); GlassBarInstaller.restore()
                TCQTSetting.applyEdits(mapOf(FloatingBottomBarConfigStore.IMPLEMENTATION_KEY to if (action == "classic") 1 else 2,
                    FloatingBottomBarConfigStore.MODE_KEY to if (action == "newGlass") 2 else 1))
                if (action == "classic") GlassBarInstaller.scheduleInstall(activity) else NewViewBarInstaller.scheduleInstall(activity)
            }
            "scale80" -> TCQTSetting.setInt(FloatingBottomBarConfigStore.SCALE_KEY, 80)
            "scale120" -> TCQTSetting.setInt(FloatingBottomBarConfigStore.SCALE_KEY, 120)
            "bottom" -> TCQTSetting.setInt(FloatingBottomBarConfigStore.POSITION_KEY, 2)
            else -> error("Unknown glass action: $action")
        }
        return JSONObject().put("requested", action)
    }
}
