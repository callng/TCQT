package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.MultiIntSetting
import com.owo233.tcqt.ext.Setting
import com.owo233.tcqt.ext.isFlagEnabled
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.internals.setting.TCQTSetting
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.reflect.field
import com.owo233.tcqt.utils.reflect.findField
import com.owo233.tcqt.utils.reflect.findMethod

@RegisterAction
class SimplifyQQSettingMe : IAction {

    override val key: String get() = "simplify_qq_setting_me"
    override val name: String get() = "侧滑栏精简"
    override val desc: String get() = "对侧滑栏功能入口进行精简隐藏。"
    override val uiTab: String get() = "界面"
    override val settings: List<Setting<*>>
        get() = listOf(
            MultiIntSetting(
                SETTING_KEY,
                "要精简的项目",
                options = map.values.toList()
            )
        )

    override fun onRun(app: Application, process: ActionProcess) {
        val selectedItemIds = TCQTSetting.getInt(SETTING_KEY).let { flags ->
            map.keys.filterIndexedTo(mutableSetOf()) { index, _ ->
                flags.isFlagEnabled(index)
            }
        }
        if (selectedItemIds.isEmpty()) return

        val clazz = "com.tencent.mobileqq.parts.QQSettingMeMenuPanelPartV3".toClass

        clazz.findMethod {
            name = "onInitView"
        }.hookAfter { param ->
            val obj = param.thisObject

            @Suppress("UNCHECKED_CAST")
            val bizDataList = clazz.findField { type = ArrayList::class.java }
                .get(obj) as ArrayList<Any>

            // 移除所有需要隐藏的条目
            bizDataList.removeAll { item ->
                val bean = item.field("a", withSuper = false)
                    ?.get(item) ?: return@removeAll false
                needRemove(bean, selectedItemIds)
            }

            // 取 adapter 并刷新数据
            val listItemAdapter =
                clazz.declaredFields.first { it.type.name.contains("adapter") }
                    .apply { isAccessible = true }
                    .get(obj)

            "com.tencent.biz.richframework.part.adapter.AsyncListDifferDelegationAdapter".toClass
                .findMethod {
                    name = "setItems"
                    paramCount = 1
                }.invoke(listItemAdapter, bizDataList)
        }
    }

    private fun needRemove(bean: Any, selectedItemIds: Set<String>): Boolean =
        bean::class.java.declaredFields
            .filter { it.type == String::class.java }
            .any { f ->
                f.isAccessible = true
                f.get(bean) as? String in selectedItemIds
            }

    private companion object {
        const val SETTING_KEY = "simplify_qq_setting_me.type"

        val map: Map<String, String> = linkedMapOf(
            "d_album" to "相册",
            "d_favorite" to "收藏",
            "d_document" to "文件",
            "d_qqwallet" to "钱包",
            "d_vip_identity" to "会员中心",
            "d_decoration" to "个性装扮",
            "d_vip_card" to "免流量"
        )
    }
}
