/**
 * 摘取自: https://github.com/cinit/QAuxiliary/commit/72b7ff9881d7d14f5d04f8f35f616921cb152fc9
 * 提供者: HdShare
 */
package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.QQVersion
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.returnConstant
import org.luckypray.dexkit.DexKitBridge

@RegisterAction
class EmotionSharePanelDownload : IAction, DexKitTask {

    override val key: String get() = "emotion_share_panel_download"
    override val name: String get() = "表情分享菜单允许保存图片"
    override val desc: String
        get() = "表情分享菜单显示保存到手机选项，保存商城表情可能会导致宿主崩溃!这个时候应该使用《以图片方式打开表情》。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30)) {
            requireMethod(EMOTION_DETAIL_AI).returnConstant(true)
        } else {
            requireMethod(EMOTION_DOWNLOAD_DISABLE_SWITCH).returnConstant(false)
        }
    }

    override fun getCacheKeys(): Set<String> {
        return buildSet {
            if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30)) {
                add(EMOTION_DETAIL_AI)
            } else {
                add(EMOTION_DOWNLOAD_DISABLE_SWITCH)
            }
        }
    }

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        if (HookEnv.requireMinQQVersion(QQVersion.QQ_9_2_30)) {
            cache[EMOTION_DETAIL_AI] = bridge.findClass {
                searchPackages("com.tencent.mobileqq.emotionintegrate")
                matcher {
                    usingStrings("MsgEmoticonPreviewData", "doRestoreSaveInstanceState")
                }
            }.findMethod {
                matcher {
                    returnType(Boolean::class.java)
                    usingNumbers(14)
                }
            }.single().descriptor
        } else {
            cache[EMOTION_DOWNLOAD_DISABLE_SWITCH] = bridge.findMethod {
                searchPackages("com.tencent.mobileqq.emotionintegrate")
                matcher {
                    returnType(Boolean::class.java)
                    usingStrings("emotion_download_disable_8980_887036489")
                }
            }.single().descriptor
        }
    }

    private companion object {
        const val EMOTION_DETAIL_AI = "EmotionDetailAi "
        const val EMOTION_DOWNLOAD_DISABLE_SWITCH = "EmotionDownloadDisableSwitch"
    }
}
