package com.owo233.tcqt.features.hooks.func.basic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.actions.ActionProcess
import com.owo233.tcqt.actions.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.features.hooks.base.loadOrThrow
import com.owo233.tcqt.foundation.utils.log.Log
import com.owo233.tcqt.foundation.utils.hookAfterMethod
import com.owo233.tcqt.foundation.utils.setObjectField

@RegisterAction
@RegisterSetting(
    key = "remove_share_limit",
    name = "移除转发选择数量限制",
    type = SettingType.BOOLEAN,
    desc = "移除转发消息时最多选择9名联系人的限制。",
)
class RemoveShareLimit : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("com.tencent.mobileqq.activity.ForwardRecentActivity")
            .declaredConstructors
            .first()
            .hookAfterMethod { param ->
                param.thisObject.setObjectField(
                    "mForwardTargetMap",
                    UnlimitedMap<String, Any>()
                )
            }

        // 测试部分
        /*Activity::class.java.hookBeforeMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val activity = param.thisObject as Activity
            val intent = activity.intent
            Log.d("Activity 启动: ${activity.javaClass.name}")
            dumpIntent(intent)
        }*/
    }

    override val key: String get() = GeneratedSettingList.REMOVE_SHARE_LIMIT

    fun dumpIntent(intent: Intent?) {
        if (intent == null) {
            Log.d("Intent = null")
            return
        }

        Log.d("Intent Data = ${intent.data}")
        Log.d("Intent Flags = ${intent.flags}")

        val extras = intent.extras ?: run {
            Log.d("Intent Extras: <empty>")
            return
        }

        Log.d("Intent Extras:")

        for (key in extras.keySet()) {
            @Suppress("DEPRECATION")
            val value = extras.get(key)
            val type = value?.javaClass?.name ?: "null"

            Log.d("  • $key = $value  (type = $type)")

            if (value is Bundle) {
                dumpBundle(value, "    ")
            }
        }
    }

    private fun dumpBundle(bundle: Bundle?, indent: String = "") {
        if (bundle == null) {
            Log.d("${indent}<null bundle>")
            return
        }

        Log.d("${indent}Bundle {")

        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            val type = value?.javaClass?.name ?: "null"

            Log.d("$indent  - $key = $value (type = $type)")

            // 递归
            if (value is Bundle) {
                dumpBundle(value, "$indent    ")
            }
        }

        Log.d("$indent}")
    }

    class UnlimitedMap<K, V> : LinkedHashMap<K, V>() {
        override val size: Int
            get() {
                val s = super.size
                return if (s == 9) 8 else s
            }
    }
}
