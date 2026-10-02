package com.owo233.tcqt.features.cleanup

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findMethodOrNull

/**
 * 群通知红点隐藏。
 *
 * 宿主「联系人」页顶部有「新朋友」「群通知」两行入口，两者的未读数**相加**后成为
 * 底栏「联系人」Tab 的红点（`MainAssistObserver#updateTabContactNotify`）：
 *
 * ```
 * 底栏数字 = INewFriendService.getAllUnreadMessageCount()            // 新朋友
 *          + ITroopNotificationRepoApi.getNotificationUnreadCount()  // 群通知
 * ```
 *
 * 而群通知的未读数只有这一个来源：联系人页「群通知」行
 * （`troopnotificationentry.d#h`）也是消费它来决定红点显隐的。
 *
 * 因此在这里按阈值把它归零，联系人页与底栏两处的群通知红点会**同时**消失；
 * 「新朋友」走的是 `INewFriendService` 那条独立链路，红点不受影响。
 */
@RegisterAction
object HideTroopNotificationBadge : Feature(
    key = "hide_troop_notification_badge",
    name = "群通知红点隐藏",
    desc = "隐藏联系人页面的群通知红点，移动滑块以修改阈值。",
) {

    /** 群通知数量超过该值才隐藏；默认 0，即只要有一条群通知就隐藏。 */
    private val threshold by sliderOption(
        settingKey = "threshold",
        name = "隐藏阈值",
        defaultValue = 0,
        desc = "群通知数量超过该值时隐藏红点。",
        min = 0,
        max = 99,
        suffix = " 条",
    )

    override fun install() {
        val clz = load(CLASS_TROOP_NOTIFICATION_REPO_IMPL)
        if (clz == null) {
            Log.e("群通知红点隐藏：未找到 $CLASS_TROOP_NOTIFICATION_REPO_IMPL，功能未生效")
            return
        }

        val method = clz.findMethodOrNull {
            name = METHOD_UNREAD_COUNT
            paramCount = 0
        }
        if (method == null) {
            Log.e("群通知红点隐藏：未找到 $METHOD_UNREAD_COUNT，功能未生效")
            return
        }

        method.hookAfter { param ->
            val real = param.result as? Int ?: return@hookAfter
            // 每次读取配置，界面上调整滑块后无需重启即可生效
            if (real > threshold) {
                param.result = 0
            }
        }
        Log.i("群通知红点隐藏：已挂钩 $CLASS_TROOP_NOTIFICATION_REPO_IMPL#$METHOD_UNREAD_COUNT")
    }

    /** QQ NT 架构的群通知仓库实现；接口 `ITroopNotificationRepoApi` 无法直接挂钩。 */
    private const val CLASS_TROOP_NOTIFICATION_REPO_IMPL =
        "com.tencent.qqnt.troop.impl.TroopNotificationRepoApiImpl"

    private const val METHOD_UNREAD_COUNT = "getNotificationUnreadCount"
}
