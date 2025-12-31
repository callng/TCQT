package com.owo233.tcqt.hooks.func.activity

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.loadOrThrow
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.reflect.FieldUtils
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole

@RegisterAction
@RegisterSetting(
    key = "sort_at_list",
    name = "优化排序@列表",
    type = SettingType.BOOLEAN,
    desc = "键入'@'时重新排序成员列表，由群主·管理员·机器人·至普通群成员。",
    uiTab = "界面"
)
class SortAtList : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        loadOrThrow("com.tencent.mobileqq.aio.input.at.common.SubmitListEvent")
            .hookAfterMethod("getItemList") { param ->
                val list = param.result as? List<Any?> ?: return@hookAfterMethod

                param.result = list.sortedWith(compareBy { item ->
                    rank(extractMemberInfo(item))
                })
            }
    }

    override val key: String get() = GeneratedSettingList.SORT_AT_LIST

    private fun extractMemberInfo(item: Any?): MemberInfo? {
        if (item == null) return null

        return FieldUtils.create(item)
            .typed<MemberInfo>()
            .preferInstance(true)
            .index(0)
            .getOrNull() as? MemberInfo
    }

    private fun rank(info: MemberInfo?): Int {
        if (info == null) return 0
        if (info.isRobot) return 3

        return when (info.role) {
            MemberRole.OWNER -> 1
            MemberRole.ADMIN -> 2
            MemberRole.MEMBER -> 4
            else -> 5
        }
    }
}
