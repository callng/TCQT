package com.owo233.tcqt.hooks.func.basic

import android.app.Application
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.HookEnv.toHostClass
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookMethodAfter
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.reflect.findField
import com.owo233.tcqt.utils.reflect.findFieldOrNull
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.invokeAs
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
class TreatApk1AsApk : IAction, DexKitTask {

    override val name: String get() = "将 APK.1 作为 APK 打开"
    override val desc: String get() = "修正 QQ 接收的 .apk.1 文件类型。"
    override val uiTab: String get() = "基础"
    override val hidden: Boolean get() = !HookEnv.isQQ()

    override fun canRun(): Boolean = HookEnv.isQQ() && super.canRun()

    override fun onRun(app: Application, process: ActionProcess) {
        hookFileExtension()
        hookCachedFileType()
    }

    override val key: String get() = "treat_apk1_as_apk"
    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.MAIN)

    override fun getQueryMap(): Map<String, BaseMatcher> {
        if (!canRun()) return emptyMap()

        return mapOf(
            FILE_VIEWER_ADAPTER_CACHE_KEY to FindClass().apply {
                matcher {
                    usingEqStrings("EntityFileViewerAdapter")
                    fields {
                        addForType(FILE_MANAGER_ENTITY_CLASS)
                    }
                    methods {
                        add {
                            name("getEntity")
                            paramCount(0)
                            returnType(FILE_MANAGER_ENTITY_CLASS)
                        }
                        add {
                            name("getFileName")
                            paramCount(0)
                            returnType(String::class.java)
                        }
                        add {
                            name("getFilePath")
                            paramCount(0)
                            returnType(String::class.java)
                        }
                        add {
                            name("getFileType")
                            paramCount(0)
                            returnType(Int::class.javaPrimitiveType!!)
                        }
                    }
                }
            }
        )
    }

    private fun hookFileExtension() {
        "com.tencent.mobileqq.filemanager.api.impl.FileUtilImpl"
            .toHostClass()
            .hookMethodAfter({
                name = "getExtension"
                paramTypes(string)
                returnType = string
            }) { param ->
                val source = param.args[0] as? String
                if (isApk1(source)) {
                    param.result = APK_EXTENSION
                }
            }
    }

    private fun hookCachedFileType() {
        val adapterClass = requireClass(FILE_VIEWER_ADAPTER_CACHE_KEY)
        val getFileName = adapterClass.findMethod {
            name = "getFileName"
            paramCount = 0
            returnType = string
        }
        val getFilePath = adapterClass.findMethod {
            name = "getFilePath"
            paramCount = 0
            returnType = string
        }
        val getFileType = adapterClass.findMethod {
            name = "getFileType"
            paramCount = 0
            returnType = int
        }

        val entityClass = FILE_MANAGER_ENTITY_CLASS.toHostClass()
        val entityField = adapterClass.findField {
            type = entityClass
            isStatic = false
        }
        val entityFileTypeField = entityClass.findFieldOrNull {
            name = "nFileType"
            type = Int::class.javaPrimitiveType
            isStatic = false
        }

        if (entityFileTypeField == null) {
            Log.w(
                "TreatApk1AsApk: FileManagerEntity.nFileType unavailable; " +
                    "only the current getFileType result will be repaired"
            )
        }

        getFileType.hookAfter { param ->
            val fileName = param.thisObject.invokeAs<String?>(getFileName)
            val filePath = param.thisObject.invokeAs<String?>(getFilePath)

            if (!isApk1(fileName) && !isApk1(filePath)) return@hookAfter

            param.result = APK_FILE_TYPE

            if (entityFileTypeField != null) {
                val entity = entityField.get(param.thisObject) ?: return@hookAfter
                entityFileTypeField.setInt(entity, APK_FILE_TYPE)
            }
        }
    }

    private fun isApk1(value: String?): Boolean =
        value?.endsWith(APK1_SUFFIX, ignoreCase = true) == true

    private companion object {
        const val APK_EXTENSION = ".apk"
        const val APK1_SUFFIX = ".apk.1"
        const val APK_FILE_TYPE = 5
        const val FILE_MANAGER_ENTITY_CLASS =
            "com.tencent.mobileqq.filemanager.data.FileManagerEntity"
        const val FILE_VIEWER_ADAPTER_CACHE_KEY = "apk1_file_viewer_adapter"
    }
}
