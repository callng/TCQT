package com.owo233.tcqt.hooks.func.advanced

import android.app.Application
import android.os.Environment
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.utils.hook.hookMethodBefore
import com.owo233.tcqt.utils.log.Log
import java.io.File

@RegisterAction
@RegisterSetting(
    key = "file_recv_redirect",
    name = "文件接收重定向",
    type = SettingType.BOOLEAN,
    desc = "目前只能重定向到/sdcard/Download/{HostAppName}/",
    uiTab = "高级"
)
class FileRecvRedirect : IAction {

    private val downLoadPath: String by lazy {
        "${Environment.getExternalStorageDirectory().absolutePath}/Download/${HookEnv.appName}/"
    }

    private val targetDir: File by lazy { File(downLoadPath) }

    override val key: String
        get() = GeneratedSettingList.FILE_RECV_REDIRECT

    override fun onRun(app: Application, process: ActionProcess) {
        if (!isTargetDirUsable()) {
            Log.e("FileRecvRedirect: 目标目录[${downLoadPath}]不可用!!!")
            return
        }

        "com.tencent.mobileqq.filemanager.api.impl.FileSandboxPathUtilApiImpl".toClass
            .hookMethodBefore("getMobileQQFileSavePath") { param ->
                param.result = downLoadPath
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
