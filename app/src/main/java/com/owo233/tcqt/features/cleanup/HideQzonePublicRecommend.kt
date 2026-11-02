package com.owo233.tcqt.features.cleanup

import com.owo233.tcqt.annotations.ActionProcess
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Modifier

@RegisterAction(processes = [ActionProcess.MAIN, ActionProcess.QZONE])
object HideQzonePublicRecommend : Feature(
    key = "hide_qzone_public_recommend",
    name = "隐藏空友爱看",
    desc = "隐藏好友动态列表中自动插入的推荐卡片。",
    requires = Requires(host = Requires.Host.QQOnly, minQQVersion = QQVersion.QQ_9_2_27),
), DexKitTask {

    private val options by multiIntOption(
        settingKey = "options",
        name = "可选项",
        options = listOf("隐藏右上角空友爱看入口"),
        isHide = HookEnv.versionCode < QQVersion.QQ_9_2_35,
    )

    override fun install() {
        val onFriendFeedsChanged = requireMethod(FRIEND_FEEDS).apply { isAccessible = true }
        val feedViewType = requireMethod(FEED_VIEW_TYPE).apply { isAccessible = true }
        val factoryClass = feedViewType.declaringClass
        val factory = factoryClass.declaredMethods.single {
            Modifier.isStatic(it.modifiers) && !it.isSynthetic &&
                it.parameterCount == 0 && it.returnType == factoryClass
        }.apply { isAccessible = true }.invoke(null)
        val commonFeed = feedViewType.parameterTypes[1]
        val types = loadOrThrow("com.qzone.reborn.feedpro.itemview.QzoneFeedProType")
        val getViewType = types.getMethod("getMViewType")
        val insertedTypes = listOf("UNIQUE_TYPE_DOMAIN_RECO", "UNIQUE_TYPE_DOMAIN_RECO_V2")
            .map { getViewType.invoke(types.getField(it).get(null)) as Int }
            .toSet()

        onFriendFeedsChanged.hookBefore { param ->
            val feeds = param.args[2] as? List<*> ?: return@hookBefore
            // 使用宿主自己的顶层类型判定，不检查转发内嵌的原帖，也不改写原列表/缓存。
            val filtered = feeds.filterIndexed { index, feed ->
                !commonFeed.isInstance(feed) ||
                    feedViewType.invoke(factory, index, feed) !in insertedTypes
            }
            if (filtered.size != feeds.size) {
                // 原回调按显示副本计算刷新与加载更多的通知条数。
                param.args[2] = filtered
            }
        }

        if (shouldHideEntrance()) {
            // 专属入口分支同时负责引导气泡；保留推荐页路由及详情查看流程。
            requireMethod(CONSTANT_ENTRANCE).apply { isAccessible = true }.hookBefore { param ->
                param.result = false
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        FRIEND_FEEDS to FindMethod().apply {
            searchPackages("com.qzone.reborn.feedpro.viewmodel")
            matcher {
                paramTypes("int", "java.lang.String", "java.util.List")
                returnType("void")
                usingEqStrings("REFRESH", "LOAD_MORE", "MEMORY_CACHE", "DISK_CACHE", "INSERT_FAKE")
            }
        },
        FEED_VIEW_TYPE to FindMethod().apply {
            searchPackages("com.qzone.reborn.feedpro.itemview")
            matcher {
                paramTypes("int", null)
                returnType("int")
                declaredClass {
                    methods {
                        add {
                            paramTypes("int", "android.content.Context")
                            returnType("com.qzone.reborn.feedpro.itemview.QzoneBaseFeedProItemView")
                            usingEqStrings("item_view_construct", "#newInstance")
                        }
                    }
                }
            }
        },
        CONSTANT_ENTRANCE to FindMethod().apply {
            matcher {
                paramCount(0)
                returnType("boolean")
                declaredClass {
                    methods {
                        add {
                            paramCount(0)
                            returnType("void")
                            usingEqStrings("QzonePublicRecommendService", "loadEntranceCtrl")
                        }
                    }
                }
                callerMethods {
                    add {
                        paramTypes("android.view.View")
                        returnType("void")
                        usingEqStrings("isConstantEntranceEnabled: ", "em_qz_public_recommend_en")
                    }
                }
            }
        },
    )

    override fun getCacheKeys(): Set<String> = buildSet {
        add(FRIEND_FEEDS)
        add(FEED_VIEW_TYPE)
        if (shouldHideEntrance()) {
            add(CONSTANT_ENTRANCE)
        }
    }

    private fun shouldHideEntrance(): Boolean =
        HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_35) && options.isFlagEnabled(FLAG_HIDE_ENTRANCE)

    private const val FLAG_HIDE_ENTRANCE = 0
    private const val FRIEND_FEEDS = "qzone_public_recommend_friend_feeds"
    private const val FEED_VIEW_TYPE = "qzone_public_recommend_feed_view_type"
    private const val CONSTANT_ENTRANCE = "qzone_public_recommend_constant_entrance"
}
