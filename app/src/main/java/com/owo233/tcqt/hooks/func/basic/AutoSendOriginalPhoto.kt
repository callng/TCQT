// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import android.widget.CheckBox
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.ProcUtil
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.returnConstant
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findMethodOrNull
import com.owo233.tcqt.utils.reflect.findMethods
import com.owo233.tcqt.utils.reflect.getObjectOrNull
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import java.lang.reflect.Method

@RegisterAction
class AutoSendOriginalPhoto : IAction, DexKitTask {

    override val name: String get() = "聊天自动发送原图"
    override val desc: String get() = "在聊天相册选择图片时自动勾选原图。"
    override val uiTab: String get() = "基础"
    override val key: String get() = "auto_send_original_photo"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN, ActionProcess.OTHER)

    override fun onRun(app: Application, process: ActionProcess) {
        if (process == ActionProcess.OTHER && !ProcUtil.isPeak) return

        Reflection.init()
        hookPhotoPanel()
        hookAlbumConfig()
        hookGuildPhotoPanel()
        hookPickerBottomBar()
    }

    private fun hookPhotoPanel() {
        val initOrRefresh = Reflection.photoPanelRefresh ?: return
        initOrRefresh.hookAfter { param ->
            Reflection.photoPanelSetOriginalChecked?.invoke(param.thisObject, true)
        }
    }

    private fun hookAlbumConfig() {
        Reflection.albumRawConfigMethods.forEach { method ->
            method.returnConstant(true)
        }
    }

    private fun hookGuildPhotoPanel() {
        Reflection.guildPanelRefresh?.hookAfter { param ->
            val holder = param.thisObject.getObjectOrNull("a") ?: return@hookAfter
            val checkBox = holder.getObjectOrNull("d") as? CheckBox ?: return@hookAfter
            if (!checkBox.isChecked) {
                checkBox.post { checkBox.isChecked = true }
            }
        }
    }

    private fun hookPickerBottomBar() {
        val method = runCatching { requireMethod(PICKER_BOTTOM_BAR_RAW) }.getOrNull()
            ?: return

        method.hookBefore { param ->
            param.args.forEachIndexed { index, arg ->
                if (arg is Boolean) {
                    param.args[index] = true
                }
            }
        }
    }

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>) {
        bridge.findMethod(
            FindMethod().apply {
                matcher {
                    declaredClass("com.tencent.qqnt.qbasealbum.album.view.PickerBottomBarPart")
                    usingStrings("isRaw")
                }
            }
        ).firstOrNull()?.let { cache[PICKER_BOTTOM_BAR_RAW] = it.descriptor }
            ?: Log.d("聊天自动发送原图: 未定位 PickerBottomBarPart 原图参数方法")
    }

    private object Reflection {

        var photoPanelRefresh: Method? = null
            private set
        var photoPanelSetOriginalChecked: Method? = null
            private set
        var guildPanelRefresh: Method? = null
            private set

        val albumRawConfigMethods = mutableListOf<Method>()

        fun init() {
            photoPanelRefresh = null
            photoPanelSetOriginalChecked = null
            guildPanelRefresh = null
            albumRawConfigMethods.clear()

            initPhotoPanel()
            initAlbumConfig()
            initGuildPhotoPanel()
        }

        private fun initPhotoPanel() {
            val photoPanel = load("com.tencent.mobileqq.aio.panel.photo.PhotoPanelVB") ?: return
            photoPanelSetOriginalChecked = photoPanel.findMethods {
                paramCount = 1
                paramTypes(boolean)
                returnType = void
            }.lastOrNull().also {
                if (it == null) Log.d("聊天自动发送原图: PhotoPanelVB 原图勾选方法未定位")
            }

            photoPanelRefresh = photoPanel.findMethodOrNull {
                name = "handleUIState"
            } ?: photoPanel.findMethodOrNull {
                name = "Q0"
                paramCount = 0
            }

            if (photoPanelRefresh == null) {
                Log.d("聊天自动发送原图: PhotoPanelVB UI 状态方法未定位")
            }
        }

        private fun initAlbumConfig() {
            val config = load("com.tencent.qqnt.qbasealbum.model.Config") ?: return
            listOf("s", "z").mapNotNullTo(albumRawConfigMethods) { name ->
                config.findMethodOrNull {
                    this.name = name
                    paramCount = 0
                    returnType = boolean
                }
            }
        }

        private fun initGuildPhotoPanel() {
            val guildPanel = load("com.tencent.guild.aio.panel.photo.GuildPhotoPanelVB") ?: return
            guildPanelRefresh = guildPanel.findMethodOrNull {
                name = "e"
                paramCount = 0
            }
        }
    }

    companion object {
        private const val PICKER_BOTTOM_BAR_RAW = "AutoSendOriginalPhoto.pickerBottomBarRaw"
    }
}
