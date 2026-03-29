package com.owo233.tcqt.loader.legacy

import com.owo233.tcqt.loader.api.Chain
import com.owo233.tcqt.loader.api.HookParam
import com.owo233.tcqt.loader.api.IHookEngine
import com.owo233.tcqt.loader.api.Invoker
import com.owo233.tcqt.loader.api.Unhook
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

class LegacyHookEngine : IHookEngine {

    override val apiLevel: Int = XposedBridge.getXposedVersion()
    override val frameworkName: String = "Xposed (Legacy)"
    override val frameworkVersion: String = XposedBridge.getXposedVersion().toString()
    override val frameworkVersionCode: Long = XposedBridge.getXposedVersion().toLong()
    override val bridgeClass: Class<*> = XposedBridge::class.java

    override fun hookBefore(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook {
        val unhook = XposedBridge.hookMethod(method, object : XC_MethodHook(priority) {
            override fun beforeHookedMethod(param: MethodHookParam) {
                callback(LegacyHookParam(param))
            }
        })
        return Unhook { unhook.unhook() }
    }

    override fun hookAfter(method: Member, priority: Int, callback: (HookParam) -> Unit): Unhook {
        val unhook = XposedBridge.hookMethod(method, object : XC_MethodHook(priority) {
            override fun afterHookedMethod(param: MethodHookParam) {
                callback(LegacyHookParam(param))
            }
        })
        return Unhook { unhook.unhook() }
    }

    override fun hookReplace(method: Member, priority: Int, callback: (Chain) -> Any?): Unhook {
        val unhook = XposedBridge.hookMethod(method, object : XC_MethodReplacement(priority) {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return callback(LegacyChain(param))
            }
        })
        return Unhook { unhook.unhook() }
    }

    override fun getInvoker(method: Member): Invoker {
        return LegacyInvoker(method)
    }

    override fun deoptimize(method: Member): Boolean {
        return false
    }

    override fun log(priority: Int, tag: String?, msg: String, t: Throwable?) {
        val finalMsg = if (tag.isNullOrEmpty()) msg else "$tag $msg"
        XposedBridge.log(finalMsg)

        if (t != null) {
            XposedBridge.log(t)
        }
    }
}
