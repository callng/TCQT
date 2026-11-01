package com.owo233.tcqt.loader.validation

import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.owo233.tcqt.R
import com.owo233.tcqt.core.config.TCQTSetting
import com.owo233.tcqt.core.env.HostBridge
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.ActionErrorStore
import com.owo233.tcqt.features.appearance.StickySearchBar
import com.owo233.tcqt.features.appearance.stickysearch.PinnedSearchBar
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only device evidence. No account names, messages or entered search text are collected. */
internal object StickySearchRegression {

    fun run(intent: Intent): JSONObject {
        when (intent.getStringExtra("uiAction")) {
            "enable" -> TCQTSetting.setBoolean(StickySearchBar.key, true)
            "disable" -> TCQTSetting.setBoolean(StickySearchBar.key, false)
        }
        val activity = requireNotNull(HostBridge.topActivity())
        val bars = JSONArray()
        val scrollers = JSONArray()
        var visiblePinnedBars = 0
        val queue = ArrayDeque<View>()
        queue.add(activity.window.decorView)
        var inspected = 0
        while (queue.isNotEmpty()) {
            check(++inspected < 10_000) { "Unexpectedly large host view tree" }
            val view = queue.removeFirst()
            if (PinnedSearchBar.isNativeSearch(view)) {
                val location = IntArray(2).also(view::getLocationOnScreen)
                val visible = Rect()
                val parent = view.parent as? View
                val isVisible = view.getGlobalVisibleRect(visible) && view.isShown
                val isPinned = parent?.getTag(R.id.tcqt_tag_pinned_search) != null
                if (isVisible && isPinned && visible.height() == view.height) visiblePinnedBars++
                val ancestors = JSONArray()
                var ancestor = parent
                while (ancestor != null && ancestors.length() < 14) {
                    val position = IntArray(2).also(ancestor::getLocationOnScreen)
                    ancestors.put(JSONObject().put("class", ancestor.javaClass.name)
                        .put("viewId", ancestor.id)
                        .put("left", ancestor.left).put("top", ancestor.top)
                        .put("translationY", ancestor.translationY).put("alpha", ancestor.alpha)
                        .put("visibility", ancestor.visibility)
                        .put("x", position[0]).put("y", position[1])
                        .put("width", ancestor.width).put("height", ancestor.height)
                        .put("layoutParams", ancestor.layoutParams?.javaClass?.name))
                    ancestor = ancestor.parent as? View
                }
                bars.put(JSONObject()
                    .put("identity", System.identityHashCode(view))
                    .put("visible", isVisible)
                    .put("x", location[0]).put("y", location[1])
                    .put("width", view.width).put("height", view.height)
                    .put("visibleHeight", visible.height())
                    .put("pinned", isPinned)
                    .put("parentClass", parent?.javaClass?.name)
                    .put("ancestors", ancestors))
            }
            if (view is ViewGroup && view.isShown && (view.javaClass.name ==
                    "com.tencent.mobileqq.activity.contacts.base.HeaderScrollView" ||
                    view.javaClass.name == "com.tencent.qqnt.chats.view.ClipSkinnableRecycleView" ||
                    view.javaClass.name == "com.tencent.mobileqq.widget.LebaOverScrollRecyclerView")) {
                val position = IntArray(2).also(view::getLocationOnScreen)
                scrollers.put(JSONObject().put("class", view.javaClass.name)
                    .put("top", view.top).put("translationY", view.translationY)
                    .put("y", position[1]).put("height", view.height)
                    .put("paddingTop", view.paddingTop)
                    .put("topMargin", (view.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin)
                    .put("scrollY", view.scrollY)
                    .put("canScrollUp", view.canScrollVertically(-1))
                    .put("canScrollDown", view.canScrollVertically(1))
                    .put("childCount", view.childCount)
                    .put("childTops", JSONArray((0 until view.childCount).map { view.getChildAt(it).top })))
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) queue.add(view.getChildAt(index))
        }
        if (intent.getBooleanExtra("assertPinned", false)) {
            check(TCQTSetting.getBoolean(StickySearchBar.key)) { "Feature is disabled" }
            check(bars.length() == 1 && visiblePinnedBars == 1) {
                "Expected one fully visible pinned native search bar: $bars"
            }
        }
        return JSONObject().put("hostVersionCode", HookEnv.versionCode)
            .put("enabled", TCQTSetting.getBoolean(StickySearchBar.key))
            .put("available", StickySearchBar.onInit())
            .put("error", ActionErrorStore.readAll()[StickySearchBar.key]?.details)
            .put("bars", bars).put("scrollers", scrollers)
    }
}
