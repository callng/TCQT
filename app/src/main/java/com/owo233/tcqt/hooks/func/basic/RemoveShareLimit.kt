package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.HookEnv.requireMinQQVersion
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.paramCount
import com.owo233.tcqt.utils.reflect.allConstructors
import com.owo233.tcqt.utils.reflect.setObjectByType
import com.tencent.mobileqq.selectmember.ResultRecord
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
@RegisterSetting(
    key = "remove_share_limit",
    name = "移除转发选择数量限制",
    type = SettingType.BOOLEAN,
    desc = "移除转发消息时最多选择9名联系人的限制。",
)
class RemoveShareLimit : IAction, DexKitTask {

    private val isKuiklyUISupported: Boolean by lazy {
        requireMinQQVersion(QQVersion.QQ_9_2_25)
    }

    private lateinit var friendListActivityCls: Class<*>
    private lateinit var recentActivityCls: Class<*>
    private lateinit var troopListFragmentCls: Class<*>
    private lateinit var selectTroopListFragmentCls: Class<*>

    override val key: String get() = GeneratedSettingList.REMOVE_SHARE_LIMIT

    override fun onRun(app: Application, process: ActionProcess) {
        if (isKuiklyUISupported) {
            requireClass("remove_share_limit")
                .allConstructors()
                .filter { it.paramCount >= 3 }
                .forEach {
                    it.hookBefore { param ->
                        param.args[2] = Int.MAX_VALUE
                    }
                }
        }

        listOf(
            friendListActivityCls,
            recentActivityCls,
            troopListFragmentCls,
            selectTroopListFragmentCls
        ).forEach { cls ->
            cls.allConstructors().first().hookAfter { param ->
                param.thisObject.setObjectByType<Map<String, ResultRecord>>(UnlimitedMap())
            }
        }
    }

    override fun onInit(): Boolean {
        friendListActivityCls =
            "com.tencent.mobileqq.activity.ForwardFriendListActivity".toHostClass()
        recentActivityCls =
            "com.tencent.mobileqq.activity.ForwardRecentActivity".toHostClass()
        troopListFragmentCls =
            "com.tencent.mobileqq.activity.ForwardTroopListFragment".toHostClass()
        selectTroopListFragmentCls =
            "com.tencent.mobileqq.selectmember.troop.SelectTroopListFragment".toHostClass()

        return super.onInit()
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = if (isKuiklyUISupported) {
        mapOf(
            "remove_share_limit" to FindClass().apply {
                matcher {
                    usingStrings("ChatSelectorConfig(")
                }
            }
        )
    } else {
        emptyMap()
    }

    private class UnlimitedMap<K, V> : LinkedHashMap<K, V>() {
        override val size: Int
            get() {
                val s = super.size
                return if (s == 9) 8 else s
            }
    }
}
