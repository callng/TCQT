package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.AlwaysRunAction
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.ext.XpClassLoader
import com.owo233.tcqt.utils.Log
import com.owo233.tcqt.utils.PlatformTools
import com.owo233.tcqt.utils.afterHook
import com.owo233.tcqt.utils.hookMethod
import com.owo233.tcqt.utils.isAbstract
import com.owo233.tcqt.utils.paramCount
import de.robv.android.xposed.XC_MethodHook

@RegisterAction
class MenuBuilder : AlwaysRunAction() {

    private val decorators: Array<OnMenuBuilder> = arrayOf(
        PttForward()
    )

    override fun onRun(ctx: Context, process: ActionProcess) {
        val activeDecorators = decorators.filter { it.canRun() }
        if (activeDecorators.isEmpty()) return

        if (PlatformTools.isNt()) {
            val msgClass = XpClassLoader.load("com.tencent.mobileqq.aio.msg.AIOMsgItem")
                ?: error("MenuBuilder Load AIOMsgItem Error")
            val baseContentComponentClass = XpClassLoader.load(
                "com.tencent.mobileqq.aio.msglist.holder.component.BaseContentComponent"
            )?: error("MenuBuilder Load BaseContentComponent Error")

            val getMsgMethod = baseContentComponentClass.declaredMethods.first {
                it.returnType == msgClass && it.paramCount == 0
            }.apply { isAccessible = true }
            val listMethodName = baseContentComponentClass.declaredMethods.first {
                it.isAbstract && it.returnType == MutableList::class.java && it.paramCount == 0
            }.name

            val targets = activeDecorators
                .flatMap { it.targetComponentTypes.asIterable() }
                .toSet()

            for (target in targets) {
                val targetClass = XpClassLoader.load(target)
                    ?: error("MenuBuilder Load $target Error")
                targetClass.getMethod(listMethodName).hookMethod(afterHook(48) {
                    val msg = getMsgMethod.invoke(it.thisObject)!!
                    for (decorator in decorators) {
                        if (target in decorator.targetComponentTypes) {
                            try {
                                decorator.onGetMenuNt(msg, target, it)
                            } catch (e: Exception) {
                                Log.e(msg = "MenuBuilder error", e)
                            }
                        }
                    }
                })
            }
        }
    }
}

interface OnMenuBuilder : IAction {
    val targetComponentTypes: Array<String>

    fun onGetMenuNt(
        msg: Any,
        componentType: String,
        param: XC_MethodHook.MethodHookParam
    )
}
