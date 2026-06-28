package com.owo233.tcqt.hooks.func.activity

import android.app.Application
import android.view.View
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.hooks.base.toClass
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.dexkit.DexKitTask
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.hook.hookReplace
import com.owo233.tcqt.utils.hook.invokeOriginal
import com.owo233.tcqt.utils.reflect.findMethod
import com.owo233.tcqt.utils.reflect.invoke
import com.owo233.tcqt.utils.reflect.new
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.PicElement
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.io.File

@RegisterAction
class PicTypeEmoticon : IAction, DexKitTask {

    override val key: String get() = "pic_type_emoticon"
    override val name: String get() = "以图片方式打开表情"
    override val desc: String get() = "可以保存一些不让保存的表情。"
    override val uiTab: String get() = "界面"

    override fun onRun(app: Application, process: ActionProcess) {
        hookPicTypeEmoticon()
        hookMarketFace()
        hookPath()
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "emoticon_a" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.utils")
            matcher {
                paramCount(1)
                paramTypes("com.tencent.qqnt.kernel.nativeinterface.PicElement")
                returnType("java.lang.String")
                invokeMethods {
                    add {
                        name = "assembleMobileQQRichMediaFilePath"
                    }
                }
            }
        },
        "emoticon_c" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.utils")
            matcher {
                paramCount(2)
                paramTypes("com.tencent.qqnt.kernel.nativeinterface.PicElement", "int")
                returnType("java.lang.String")
                invokeMethods {
                    add {
                        name = "assembleMobileQQRichMediaFilePath"
                    }
                }
            }
        }
    )

    private fun hookPath() {
        requireMethod("emoticon_a").hookBefore { param ->
            val picElement = param.args[0] as PicElement
            if (picElement.md5HexStr == "tcqt_market_face_md5_placeholder") {
                param.result = picElement.sourcePath
            }
        }

        requireMethod("emoticon_c").hookBefore { param ->
            val picElement = param.args[0] as PicElement
            if (picElement.md5HexStr == "tcqt_market_face_md5_placeholder") {
                param.result = picElement.sourcePath
            }
        }
    }

    private fun hookPicTypeEmoticon() {
        "com.tencent.qqnt.aio.adapter.api.impl.RichMediaBrowserApiImpl".toClass.findMethod {
            name = "enterImagePreview"
            paramCount = 9
        }.hookBefore { param ->
            param.args[8] = false
        }
    }

    private fun hookMarketFace() {
        "com.tencent.qqnt.aio.adapter.api.impl.AIOMarketFaceApiImpl".toClass.findMethod {
            name = "enterMarketFacePreviewWithSource"
        }.hookReplace { param ->
            val clickedView = param.args[0] as View
            val msgRecord = param.args[1] as MsgRecord

            val relativePath = msgRecord.elements[0].marketFaceElement.staticFacePath
            if (relativePath.isEmpty()) return@hookReplace param.invokeOriginal()

            val context = clickedView.context
            val absolutePath = File(
                File(
                    context.getExternalFilesDir(null)!!.parentFile,
                    "Tencent/MobileQQ"
                ), relativePath
            ).absolutePath

            val msgElement = MsgElement().apply {
                val file = File(absolutePath)
                setPicElement(PicElement().apply {
                    sourcePath = absolutePath
                    md5HexStr = "tcqt_market_face_md5_placeholder"
                    fileName = file.name
                    fileSize = file.length()
                    picType = 1000
                    transferStatus = 0
                    progress = 0
                    invalidState = 0
                })
            }

            val elements = msgRecord.elements
            val aioMsgItem = AIOMsgItem(msgRecord)
            val appRuntime = QQInterfaces.appRuntime
            val api = "com.tencent.qqnt.aio.adapter.api.impl.RichMediaBrowserApiImpl".toClass.new()
            val oldMsgType = msgRecord.msgType
            val oldElements = ArrayList(elements)

            elements.clear()
            elements.add(msgElement)
            msgRecord.msgType = 2

            try {
                api.invoke(
                    "enterImagePreview",
                    appRuntime,
                    context,
                    clickedView,
                    aioMsgItem,
                    msgElement,
                    true,
                    null,
                    null,
                    false,
                    withSuper = false
                )
            } finally {
                elements.clear()
                elements.addAll(oldElements)
                msgRecord.msgType = oldMsgType
            }

            return@hookReplace null
        }
    }
}
