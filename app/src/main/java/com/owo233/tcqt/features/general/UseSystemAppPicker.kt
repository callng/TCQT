package com.owo233.tcqt.features.general

import android.app.Activity
import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.hook.hookReplace
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.invoke

@RegisterAction(processes = [ActionProcess.MAIN])
object UseSystemAppPicker : Feature(
    key = "use_system_app_picker",
    name = "使用系统应用选择器",
    desc = "将文件的“其他应用打开”交给 Android 系统处理。",
) {

    override fun install() {
        val targetClass = FILE_MANAGER_UTIL_IMPL.toHostClass()
        val systemOpenMethod = targetClass.findMethod {
            name = "openFileWithOtherAppWithSystem"
            paramTypes(Context::class.java, String::class.java)
            returnType = void
        }

        targetClass.findMethod {
            name = "openWithOtherApp"
            paramTypes(Activity::class.java, String::class.java)
            returnType = void
        }.hookReplace { param ->
            requireNotNull(param.thisObject).invoke(systemOpenMethod, *param.args)
        }
    }

    private const val FILE_MANAGER_UTIL_IMPL =
        "com.tencent.mobileqq.filemanager.api.impl.FileManagerUtilImpl"
}
