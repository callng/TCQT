package com.owo233.tcqt.hooks

import android.content.Context
import android.content.Intent
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookAfterMethod
import com.owo233.tcqt.utils.hookBeforeMethod
import com.owo233.tcqt.utils.isPublic
import com.owo233.tcqt.utils.paramCount
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
    uiTab = "高级",
    uiOrder = 102
)
class DisableHotPatch : IAction {

    override fun onRun(ctx: Context, process: ActionProcess) {
        XpClassLoader.load("com.tencent.rfix.lib.download.PatchDownloadTask")
            ?.getDeclaredMethod("run")
            ?.hookBeforeMethod {
                it.result = Unit
            }

        XpClassLoader.load("com.tencent.rfix.lib.engine.PatchEngineBase")?.let { methods ->
            val patchConfig = XpClassLoader.load("com.tencent.rfix.lib.config.PatchConfig")!!
            methods.declaredMethods.single {
                it.isPublic && it.returnType == Void.TYPE
                        && it.paramCount == 2
                        && it.parameterTypes[0] == String::class.java
                        && it.parameterTypes[1] == patchConfig
            }.hookBeforeMethod {
                it.result = Unit
            }
        }

        XpClassLoader.load("com.tencent.mobileqq.msf.core.net.utils.MsfHandlePatchUtils")
            ?.getDeclaredMethod(
                "handlePatchConfig",
                Int::class.javaPrimitiveType,
                List::class.java
            )?.hookBeforeMethod {
                it.result = Unit
            }

        XpClassLoader.load("com.tencent.mobileqq.msf.core.net.patch.RFixExtraConfig")
            ?.hookBeforeMethod("isEnable") {
                val thisObj = it.thisObject
                val field = thisObj.javaClass.getDeclaredField("disable").apply { isAccessible = true }
                if (!(field.get(thisObj) as? Boolean ?: return@hookBeforeMethod)) {
                    field.set(thisObj, true)
                }
            }

        XpClassLoader.load("com.tencent.mobileqq.config.splashlogo.ConfigServlet")?.let { kConfigServlet ->
            val kRespGetConfig = XpClassLoader.load("com.tencent.mobileqq.config.struct.splashproto.ConfigurationService\$RespGetConfig")!!
            val kRespGetConfigConfigList = kRespGetConfig.getDeclaredField("config_list")

            val kConfig = XpClassLoader.load("com.tencent.mobileqq.config.struct.splashproto.ConfigurationService\$Config")!!
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
                method.hookBeforeMethod {
                    val respGetConfig = it.args[1] ?: return@hookBeforeMethod
                    val configList = kRespGetConfigConfigList.get(respGetConfig)
                            as? PBRepeatMessageField<*> ?: return@hookBeforeMethod
                    val arrayList = configList.get() as ArrayList<*>
                    if (arrayList.isEmpty()) {
                        return@hookBeforeMethod
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

        XpClassLoader.load("com.tencent.mobileqq.msf.core.net.patch.PatchReporter")?.let { kPatchReporter ->
            kPatchReporter.declaredMethods.filter {
                it.name.startsWith("report") && it.returnType == Void.TYPE
            }.forEach { m ->
                m.hookBeforeMethod {
                    it.result = Unit
                }
            }
        }

        XmlData::class.java.hookAfterMethod(
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

    override val processes: Set<ActionProcess> get() =
        setOf(ActionProcess.MAIN, ActionProcess.MSF, ActionProcess.TOOL)
}
