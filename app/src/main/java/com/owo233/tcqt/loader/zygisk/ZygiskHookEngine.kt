package com.owo233.tcqt.loader.zygisk

import android.util.Log
import com.owo233.tcqt.data.TCQTBuild
import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.loader.api.HookParam
import com.owo233.tcqt.loader.api.IHookEngine
import com.owo233.tcqt.loader.api.Invoker
import com.owo233.tcqt.loader.api.Unhook
import java.lang.reflect.Member

class ZygiskHookEngine : IHookEngine {

    override val apiLevel: Int = 0
    override val frameworkName: String = "Zygisk"
    override val frameworkVersion: String = TCQTBuild.VER_NAME
    override val frameworkVersionCode: Long = TCQTBuild.VER_CODE.toLong()
    override val bridgeClass: Class<*>? = null

    override fun hookBefore(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook =
        ZygiskHookBridge.hookBefore(method, priority, callback)

    override fun hookAfter(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook =
        ZygiskHookBridge.hookAfter(method, priority, callback)

    override fun hookReplace(method: Member, priority: Int, callback: (Chain) -> Any?): Unhook =
        ZygiskHookBridge.hookReplace(method, priority, callback)

    override fun getInvoker(method: Member): Invoker = ZygiskHookBridge.ZygiskInvoker(method)

    override fun deoptimize(method: Member): Boolean = false

    override fun log(priority: Int, tag: String?, msg: String, t: Throwable?) {
        val tagName = tag ?: TCQTBuild.HOOK_TAG
        if (t != null) {
            Log.println(priority, tagName, "$msg\n${Log.getStackTraceString(t)}")
        } else {
            Log.println(priority, tagName, msg)
        }
    }
}
