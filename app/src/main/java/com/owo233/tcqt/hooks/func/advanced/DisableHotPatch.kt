package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import android.content.Intent
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.hook.isPublic
import com.owo233.tcqt.utils.hook.paramCount
import com.tencent.mobileqq.earlydownload.xmldata.XmlData
import com.tencent.mobileqq.pb.PBInt32Field
import com.tencent.mobileqq.pb.PBRepeatMessageField
import mqq.app.AppRuntime

@RegisterAction
@RegisterSetting(
    key = "disable_hot_patch",
    name = "禁用热补丁",
    type = SettingType.BOOLEAN,
    desc = "顾名思义，但不会删除已有的热补丁。",
    uiTab = "高级"
)
class DisableHotPatch : IAction {

    override fun onRun(app: Application, process: ActionProcess) {
        load("com.tencent.rfix.lib.download.PatchDownloadTask")
            ?.getDeclaredMethod("run")
            ?.hookBefore {
                it.result = Unit
            }

        load("com.tencent.rfix.lib.engine.PatchEngineBase")?.let { methods ->
            val patchConfig = load("com.tencent.rfix.lib.config.PatchConfig")!!
            methods.declaredMethods.single {
                it.isPublic && it.returnType == Void.TYPE
                        && it.paramCount == 2
                        && it.parameterTypes[0] == String::class.java
                        && it.parameterTypes[1] == patchConfig
            }.hookBefore {
                it.result = Unit
            }
        }

        load("com.tencent.mobileqq.msf.core.net.utils.MsfHandlePatchUtils")
            ?.getDeclaredMethod(
                "handlePatchConfig",
                Int::class.javaPrimitiveType,
                List::class.java
            )?.hookBefore {
                it.result = Unit
            }

        load("com.tencent.mobileqq.msf.core.net.patch.RFixExtraConfig")
            ?.hookMethodBefore("isEnable") {
                val thisObj = it.thisObject
                val field =
                    thisObj.javaClass.getDeclaredField("disable").apply { isAccessible = true }
                if (!(field.get(thisObj) as? Boolean ?: return@hookMethodBefore)) {
                    field.set(thisObj, true)
                }
            }

        load("com.tencent.mobileqq.config.splashlogo.ConfigServlet")?.let { kConfigServlet ->
            val kRespGetConfig =
                load($$"com.tencent.mobileqq.config.struct.splashproto.ConfigurationService$RespGetConfig")!!
            val kRespGetConfigConfigList = kRespGetConfig.getDeclaredField("config_list")

            val kConfig =
                load($$"com.tencent.mobileqq.config.struct.splashproto.ConfigurationService$Config")!!
            val kConfigType = kConfig.getDeclaredField("type")

            kConfigServlet.declaredMethods.filter { m ->
                val args = m.parameterTypes
                m.returnType == Void.TYPE &&
                        args.size in 5..6 &&
                        args[0] == AppRuntime::class.java &&
                        args[1] == kRespGetConfig &&
                        args[2] == Intent::class.java &&
                        args.last() == Boolean::class.javaPrimitiveType &&
                        args.any { it.isArray && it.componentType == Int::class.javaPrimitiveType }
            }.forEach { method ->
                method.hookBefore {
                    val respGetConfig = it.args[1] ?: return@hookBefore
                    val configList = kRespGetConfigConfigList.get(respGetConfig)
                            as? PBRepeatMessageField<*> ?: return@hookBefore
                    val arrayList = configList.get() as ArrayList<*>
                    if (arrayList.isEmpty()) {
                        return@hookBefore
                    }
                    // debug dump type
                    /*arrayList.forEach { config ->
                        val type = (kConfigType?.get(config) as PBInt32Field).get()
                        logD(msg = "ConfigServlet type: $type")
                    }*/
                    // remove all hotpatch config, type == 46
                    arrayList.removeIf { config ->
                        val type = (kConfigType.get(config) as PBInt32Field).get()
                        type == 46
                    }
                    // if the array is empty, do not call the original method
                    if (arrayList.isEmpty()) {
                        it.result = null
                    }
                }
            }
        }

        load("com.tencent.mobileqq.msf.core.net.patch.PatchReporter")?.let { kPatchReporter ->
            kPatchReporter.declaredMethods.filter {
                it.name.startsWith("report") && it.returnType == Void.TYPE
            }.forEach { m ->
                m.hookBefore {
                    it.result = Unit
                }
            }
        }

        XmlData::class.java.hookMethodAfter(
            "updateServerInfo",
            XmlData::class.java
        ) {
            val xmlData = it.thisObject as XmlData
            xmlData.StoreBackup = false
            xmlData.load2G = false
            xmlData.load3G = false
            xmlData.loadWifi = false
            xmlData.net_2_2G = false
            xmlData.net_2_3G = false
            xmlData.net_2_wifi = false
        }
    }

    override val key: String get() = GeneratedSettingList.DISABLE_HOT_PATCH

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.ALL)
}
