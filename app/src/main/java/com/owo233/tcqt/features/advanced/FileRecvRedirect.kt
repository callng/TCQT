package com.owo233.tcqt.features.advanced

import android.os.Environment
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findMethod
import java.io.File

@RegisterAction(priority = ActionPriority.CRITICAL)
object FileRecvRedirect : Feature(
    key = "file_recv_redirect",
    name = "文件接收重定向",
    desc = "目前只能重定向到/sdcard/Download/{HostAppName}/",
) {


    private val defaultPath: String by lazy {
        "${HookEnv.application.getExternalFilesDir(null)!!.parent!!}/Tencent/${HookEnv.appName}file_recv/"
    }

    private val downLoadPath: String by lazy {
        "${Environment.getExternalStorageDirectory().absolutePath}/Download/${HookEnv.appName}/"
    }

    private val targetDir: File by lazy { File(downLoadPath) }


    /**
     * `VFSAssistantUtils.getSDKPrivatePath` 在宿主 onCreate 期间就会被调用，
     * 第一次调用不能漏，必须在 onCreate Before 中同步安装。
     */

    override fun install() {
        if (!isTargetDirUsable()) {
            Log.e("FileRecvRedirect: 目标目录[${downLoadPath}]不可用!!!")
            return
        }

        "com.tencent.mobileqq.vfs.VFSAssistantUtils".toClass.findMethod {
            name = "getSDKPrivatePath"
            paramCount = 1
            paramTypes = arrayOf(string)
        }.hookAfter { param ->
            val result = param.result as String
            val file = File(result)
            if (file.exists() && file.isFile) return@hookAfter
            if (result.startsWith(defaultPath)) {
                param.result = File(downLoadPath, file.name).absolutePath
            }
        }
    }

    private fun isTargetDirUsable(): Boolean {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return false
        if (targetDir.exists() && targetDir.isFile) {
            if (!targetDir.delete()) return false
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) return false
        return targetDir.exists() && targetDir.isDirectory && targetDir.canWrite()
    }
}
