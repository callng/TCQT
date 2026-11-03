package com.owo233.tcqt.features.cleanup

import com.owo233.tcqt.annotations.ActionProcess
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.returnConstant
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethodOrNull
import com.owo233.tcqt.core.reflect.invoke
import com.owo233.tcqt.core.reflect.new
import com.qzone.reborn.feedpro.itemview.QzoneFeedProType
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

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
        val displayUpdate = requireMethod(DISPLAY_UPDATE)
        val feedViewType = requireMethod(FEED_VIEW_TYPE)
        val factory = getFeedFactory(feedViewType.declaringClass)
        val listIndex = displayUpdate.parameterTypes.indexOf(List::class.java)
        require(listIndex >= 0) { "Display update method has no List parameter: $displayUpdate" }
        val commonFeed = feedViewType.parameterTypes.single { it != Int::class.javaPrimitiveType }
        val feedFirst = feedViewType.parameterTypes[0] == commonFeed
        val insertedTypes = setOf(
            QzoneFeedProType.UNIQUE_TYPE_DOMAIN_RECO.mViewType,
            QzoneFeedProType.UNIQUE_TYPE_DOMAIN_RECO_V2.mViewType,
        )

        displayUpdate.hookBefore { param ->
            val feeds = param.args[listIndex] as? List<*> ?: return@hookBefore
            // 只判定顶层卡片，保留转发内嵌的原帖及宿主原列表/缓存。
            val filtered = feeds.filterIndexed { index, feed ->
                !commonFeed.isInstance(feed) || run {
                    val viewType = if (feedFirst) {
                        factory.invoke(feedViewType, feed, index)
                    } else {
                        factory.invoke(feedViewType, index, feed)
                    }
                    viewType !in insertedTypes
                }
            }
            if (filtered.size != feeds.size) {
                // 在宿主计算刷新、加载更多的通知条数之前替换展示副本。
                param.args[listIndex] = filtered
            }
        }

        if (options.isFlagEnabled(FLAG_HIDE_ENTRANCE)) {
            requireMethod(CONSTANT_ENTRANCE).returnConstant(false)
        }
    }

    private fun getFeedFactory(factoryClass: Class<*>): Any {
        // 旧版 getter 会初始化单例；9.3.10 起工厂类只保留 synthetic 字段访问器。
        val getter = factoryClass.findMethodOrNull {
            scope = declared
            isStatic = true
            includeSynthetic = false
            paramCount = 0
            returnType = factoryClass
            requireUnique = true
        }
        if (getter != null) return requireNotNull(factoryClass.invoke(getter))

        return factoryClass.findField {
            scope = declared
            isStatic = true
            type = factoryClass
            requireUnique = true
        }.get(null) ?: factoryClass.new()
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = buildMap {
        put(DISPLAY_UPDATE, FindMethod().apply {
            searchPackages("com.qzone.reborn.feedpro.viewmodel")
            matcher {
                paramCount(4)
                returnType("void")
                usingEqStrings("onFeedDataChange, feedList: ", "lastModifyRecord, startPosition: ", ", pageCount: ")
                declaredClass {
                    methods {
                        add {
                            paramCount(0)
                            returnType("java.lang.String")
                            usingEqStrings("QzoneFriendFeedProViewModel")
                        }
                    }
                }
            }
        })
        put(FEED_VIEW_TYPE, FindMethod().apply {
            searchPackages("com.qzone.reborn.feedpro.itemview")
            matcher {
                paramCount(2)
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
        })
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_35)) {
            put(CONSTANT_ENTRANCE, FindMethod().apply {
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
                            returnType("void")
                            usingEqStrings("isConstantEntranceEnabled: ", "em_qz_public_recommend_en")
                        }
                    }
                }
            })
        }
    }

    private const val FLAG_HIDE_ENTRANCE = 0
    private const val DISPLAY_UPDATE = "qzone_friend_feed_display_update"
    private const val FEED_VIEW_TYPE = "qzone_feed_view_type"
    private const val CONSTANT_ENTRANCE = "qzone_constant_entrance"
}
