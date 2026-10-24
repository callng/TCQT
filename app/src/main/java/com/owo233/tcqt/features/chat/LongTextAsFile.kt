package com.owo233.tcqt.features.chat

import android.os.Bundle
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.dexkit.DexKitLookupTracker
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.Toasts
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.sync.ModuleScope
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import com.tencent.qqnt.msg.api.IMsgService
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.result.MethodData
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@RegisterAction
object LongTextAsFile : Feature(
    key = "long_text_as_file",
    name = "超长文本转文件",
    desc = "解除输入字数限制，消息超过 QQ 字数上限时自动发送为 TXT 文件。",
    priority = ActionPriority.EARLY,
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
), DexKitTask {

    private val sending = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    override fun install() {
        val methods = getCacheKeys().associateWith { requireMethod(it).apply { isAccessible = true } }
        val sendClick = methods.getValue(SEND_CLICK)
        val readText = methods.getValue(INPUT_TEXT)
        val readElements = methods.getValue(INPUT_ELEMENTS)
        val convertText = methods.getValue(INPUT_TEXT_TO_ELEMENTS)
        val inputTextClass = convertText.parameterTypes.single()
        val readLimit = methods.getValue(MAX_LENGTH)
        val readParam = methods.getValue(RUNTIME_PARAM)
        val readSession = methods.getValue(PARAM_SESSION)
        val makeContact = methods.getValue(SESSION_CONTACT)
        val makeFile = methods.getValue(FILE_ELEMENT)
        val clearInput = methods.getValue(CLEAR_INPUT)
        val elementKind = methods.getValue(ELEMENT_KIND)
        val elementText = methods.getValue(ELEMENT_TEXT)
        val textContent = methods.getValue(TEXT_CONTENT)
        val delegateClass = sendClick.declaringClass
        val inputField = delegateClass.instanceField(readText.declaringClass)
        val limitField = delegateClass.instanceField(readLimit.declaringClass)
        val runtimeField = delegateClass.instanceField(readParam.declaringClass)
        val contactReceiver = makeContact.receiver()
        val fileReceiver = makeFile.receiver()
        val textConverterReceiver = convertText.receiver()

        sendClick.hookBefore { param ->
            val delegate = param.thisObject
            runCatching {
                val input = inputField.get(delegate)
                val text = (readText.invoke(input) as CharSequence).toString()
                val limit = readLimit.invoke(limitField.get(delegate)) as Int
                check(limit > 0) { "QQ input limit is invalid: $limit" }
                val inputParts = readElements.invoke(input) as List<*>
                var measuredLength = 0L
                var hasAttachment = false
                for (part in inputParts) {
                    // 输入层片段与消息层元素不是同一种模型；图片片段是文本片段的子类。
                    // 只转换纯文本片段，避免计数时触发宿主图片构造及其文件操作。
                    if (part?.javaClass != inputTextClass) {
                        hasAttachment = true
                        continue
                    }
                    val elements = convertText.invoke(textConverterReceiver, part) as List<*>
                    for (element in elements) {
                        check(elementKind.declaringClass.isInstance(element)) {
                            "Input conversion returned ${element?.javaClass?.name}, " +
                                "expected ${elementKind.declaringClass.name}, key=$INPUT_TEXT_TO_ELEMENTS"
                        }
                        when (elementKind.invoke(element) as Int) {
                            1 -> measuredLength += (textContent.invoke(elementText.invoke(element)) as String).length
                            6 -> measuredLength += 3 // 与宿主发送前的 QQ 表情计数规则一致。
                            else -> hasAttachment = true
                        }
                    }
                }
                if (maxOf(text.length.toLong(), measuredLength) <= limit) return@hookBefore

                // 在宿主长度检查及清空输入前接管发送；输入法发送也会进入此方法。
                param.result = null
                if (hasAttachment) {
                    Toasts.error("请先移除图片等附件，再将超长文本作为文件发送")
                    return@hookBefore
                }
                check(text.isNotEmpty()) { "Long input has no text representation" }
                val session = readSession.invoke(readParam.invoke(runtimeField.get(delegate)))
                val contact = makeContact.invoke(contactReceiver, session) as Contact
                if (!synchronized(sending) { sending.add(delegate) }) return@hookBefore

                val completed = AtomicBoolean()
                fun finish(code: Int, message: String?, failure: Throwable? = null) {
                    if (!completed.compareAndSet(false, true)) return
                    ModuleScope.launchMain {
                        try {
                            if (code != 0) {
                                Log.e("long_text_as_file: 文件发送失败, code=$code, error=$message", failure)
                                Toasts.error("文件发送失败，原文已保留")
                                return@launchMain
                            }
                            // 异步发送期间可能切换会话、修改文本或插入附件，不能清掉新的草稿。
                            runCatching {
                                val currentSession = readSession.invoke(readParam.invoke(runtimeField.get(delegate)))
                                val currentInput = inputField.get(delegate)
                                val currentElements = readElements.invoke(currentInput) as List<*>
                                if (currentSession === session &&
                                    (readText.invoke(currentInput) as CharSequence).toString() == text &&
                                    currentElements.all { it?.javaClass == inputTextClass }
                                ) {
                                    clearInput.invoke(delegate, Bundle().apply { putInt("clearInputStatus", 1) })
                                }
                            }.onFailure { Log.e("long_text_as_file: 文件已发送，清理原草稿失败", it) }
                            Toasts.success("消息内容已作为文件发送")
                        } finally {
                            synchronized(sending) { sending.remove(delegate) }
                        }
                    }
                }

                ModuleScope.launchIO("long_text_as_file") {
                    runCatching {
                        val file = writeTextFile(text)
                        val element = makeFile.invoke(fileReceiver, file.absolutePath) as MsgElement
                        QRoute.api(IMsgService::class.java).sendMsg(contact, arrayListOf(element)) { code, error ->
                            finish(code, error)
                        }
                    }.onFailure { finish(-1, it.message, it) }
                }
            }.onFailure {
                Log.e("long_text_as_file: 准备超长文本发送失败", it)
                Toasts.error("超长文本处理失败，原文已保留")
            }
        }

        // 仅绕过该聊天输入框安装的两种 QQ 长度过滤器。
        // InputFilter 返回 null 才是允许完整输入，返回空字符串会拒绝输入。
        methods.getValue(TEXT_FILTER).hookBefore { it.result = null }
        methods.getValue(SPAN_FILTER).hookBefore { it.result = null }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        FILTER_INSTALLER to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.input.edit.delegate")
            matcher {
                returnType("void")
                usingEqStrings("mEditText")
                invokeMethods {
                    add { name = "setFilters"; paramTypes("android.text.InputFilter[]") }
                    add { name = "getInputQQTextBuilder" }
                }
            }
        },
        CHECK_LENGTH to FindMethod().apply {
            matcher {
                declaredClass(SEND_DELEGATE)
                paramCount(0)
                returnType("boolean")
                usingFields {
                    add { type($$"com.tencent.mobileqq.aio.input.sendmsg.AIOSendMsgUIState$ShowInputOverLengthToast") }
                }
                invokeMethods { add { declaredClass("java.lang.CharSequence"); name = "length" } }
            }
        },
        SEND_CLICK to FindMethod().apply {
            matcher {
                declaredClass(SEND_DELEGATE)
                paramCount(0)
                returnType("void")
                usingEqStrings("handleSendBtnClicked")
            }
        },
        SEND_ELEMENTS to FindMethod().apply {
            matcher {
                declaredClass(SEND_DELEGATE)
                paramTypes("java.util.List", "android.os.Bundle", "java.lang.Long", "java.lang.String")
                returnType("void")
                usingEqStrings("[sendMsg] elements is empty", "checkInputEmojiLengthLimit emojiCount:")
            }
        },
        INPUT_CONVERTER to FindMethod().apply {
            matcher {
                declaredClass(SEND_DELEGATE)
                paramCount(2)
                returnType("java.util.List")
                usingEqStrings("aio_input_insert_image_905_116748503")
            }
        },
        INPUT_TEXT to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.aio.input.sendmsg")
            matcher {
                paramCount(0)
                returnType("java.lang.CharSequence")
                usingEqStrings("input_text")
            }
        },
        CLEAR_INPUT to FindMethod().apply {
            matcher {
                declaredClass(SEND_DELEGATE)
                paramTypes("android.os.Bundle")
                returnType("void")
                usingEqStrings("clearInputStatus", "[clearEditContent] start")
            }
        },
        RUNTIME_PARAM to FindMethod().apply {
            searchPackages("com.tencent.aio.api.runtime")
            matcher { paramCount(0); returnType("com.tencent.aio.data.AIOParam") }
        },
        PARAM_SESSION to FindMethod().apply {
            matcher {
                declaredClass("com.tencent.aio.data.AIOParam")
                paramCount(0)
                returnType("com.tencent.aio.data.AIOSession")
            }
        },
        SESSION_CONTACT to FindMethod().apply {
            matcher {
                declaredClass("com.tencent.mobileqq.aio.utils.AIOUtil")
                paramTypes("com.tencent.aio.data.AIOSession")
                returnType("com.tencent.qqnt.kernelpublic.nativeinterface.Contact")
                usingEqStrings("aioSession")
            }
        },
        FILE_ELEMENT to FindMethod().apply {
            matcher {
                declaredClass("com.tencent.qqnt.msg.MsgUtil")
                paramTypes("java.lang.String")
                returnType("com.tencent.qqnt.kernel.nativeinterface.MsgElement")
                usingEqStrings("path")
                invokeMethods {
                    add { declaredClass("com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil"); name = "genFileMd5Hex" }
                    add { declaredClass("com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil"); name = "getFileSize" }
                }
            }
        },
    )

    override fun getCacheKeys(): Set<String> = getQueryMap().keys + setOf(
        TEXT_FILTER, SPAN_FILTER, MAX_LENGTH, INPUT_ELEMENTS, INPUT_TEXT_TO_ELEMENTS,
        ELEMENT_KIND, ELEMENT_TEXT, TEXT_CONTENT,
    )

    override fun execute(bridge: DexKitBridge, cache: MutableMap<String, String>, tracker: DexKitLookupTracker) {
        super.execute(bridge, cache, tracker)

        fun anchor(key: String): MethodData = checkNotNull(bridge.getMethodData(cache.getValue(key)))
        fun calls(key: String): List<MethodData> = anchor(key).invokes.distinctBy { it.descriptor }
        fun resolve(key: String, query: () -> MethodData) {
            lookup(key, bridge, cache, tracker) { query().descriptor }
        }

        // 从输入框安装方法的构造调用追踪 InputFilter，实现类名变化后仍可重查。
        val filterMethods by lazy {
            calls(FILTER_INSTALLER)
                .asSequence()
                .filter { it.isConstructor }
                .mapNotNull { it.declaredClass }
                .filter { clazz -> clazz.interfaces.any { it.name == "android.text.InputFilter" } }
                .flatMap { it.methods }
                .filter { it.name == "filter" && it.paramCount == 6 && it.returnTypeName == "java.lang.CharSequence" }
                .distinctBy { it.descriptor }
                .toList()
                .also { check(it.size == 2) { "Expected two QQ input length filters: $it" } }
        }
        resolve(TEXT_FILTER) {
            filterMethods.single { method ->
                method.invokes.any { it.className == "java.lang.Character" && it.name == "isHighSurrogate" }
            }
        }
        resolve(SPAN_FILTER) { filterMethods.single { it.descriptor != cache.getValue(TEXT_FILTER) } }
        resolve(MAX_LENGTH) {
            calls(CHECK_LENGTH).single {
                it.paramCount == 0 && it.returnTypeName == "int" && it.className != "java.lang.CharSequence"
            }
        }
        resolve(INPUT_ELEMENTS) {
            checkNotNull(anchor(INPUT_TEXT).declaredClass).methods.single {
                it.paramCount == 0 && it.returnTypeName == "java.util.List"
            }
        }
        resolve(INPUT_TEXT_TO_ELEMENTS) {
            // 追踪宿主输入转换入口实际调用的文本转换器，以其参数类型区分文本与图片片段。
            calls(INPUT_CONVERTER).single {
                it.paramCount == 1 && it.returnTypeName == "java.util.List" &&
                    it.paramTypeNames.single().startsWith("com.tencent.mobileqq.aio.input.")
            }
        }
        resolve(ELEMENT_KIND) {
            calls(SEND_ELEMENTS).single {
                it.paramCount == 0 && it.returnTypeName == "int" && it.className.startsWith("com.tencent.mobileqq.aio.msg.data.")
            }
        }
        resolve(TEXT_CONTENT) {
            calls(SEND_ELEMENTS).single {
                it.paramCount == 0 && it.returnTypeName == "java.lang.String" && it.className.startsWith("com.tencent.qqnt.aio.msg.element.")
            }
        }
        resolve(ELEMENT_TEXT) {
            val textClass = anchor(TEXT_CONTENT).className
            calls(SEND_ELEMENTS).single { it.paramCount == 0 && it.returnTypeName == textClass }
        }
    }

    private fun Class<*>.instanceField(fieldType: Class<*>) = findField {
        type = fieldType
        isStatic = false
        requireUnique = true
    }

    private fun Method.receiver(): Any? = if (Modifier.isStatic(modifiers)) null else declaringClass.findField {
        type = declaringClass
        isStatic = true
        requireUnique = true
    }.get(null)

    private fun writeTextFile(text: String): File {
        val dir = File(HookEnv.hostAppContext.filesDir, "tcqt-long-text")
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create long-text directory" }
        val baseName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.US).format(Date())
        var suffix = 0
        var file: File
        do {
            file = File(dir, baseName + (if (suffix == 0) "" else "_${suffix}") + ".txt")
            suffix++
        } while (!file.createNewFile())
        try {
            file.writeText(text, Charsets.UTF_8)
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        // 保留文件供宿主上传、重试和本地打开使用，不能在 sendMsg 回调时删除。
        return file
    }

    private const val SEND_DELEGATE = "com.tencent.mobileqq.aio.input.sendmsg.AIOSendMsgVMDelegate"
    private const val FILTER_INSTALLER = "long_text_filter_installer"
    private const val TEXT_FILTER = "long_text_text_filter"
    private const val SPAN_FILTER = "long_text_span_filter"
    private const val CHECK_LENGTH = "long_text_check_length"
    private const val SEND_CLICK = "long_text_send_click"
    private const val SEND_ELEMENTS = "long_text_send_elements"
    private const val MAX_LENGTH = "long_text_max_length"
    private const val INPUT_TEXT = "long_text_input_text"
    private const val INPUT_ELEMENTS = "long_text_input_elements"
    private const val INPUT_CONVERTER = "long_text_input_converter"
    private const val INPUT_TEXT_TO_ELEMENTS = "long_text_input_text_to_elements"
    private const val ELEMENT_KIND = "long_text_element_kind"
    private const val ELEMENT_TEXT = "long_text_element_text"
    private const val TEXT_CONTENT = "long_text_text_content"
    private const val CLEAR_INPUT = "long_text_clear_input"
    private const val RUNTIME_PARAM = "long_text_runtime_param"
    private const val PARAM_SESSION = "long_text_param_session"
    private const val SESSION_CONTACT = "long_text_session_contact"
    private const val FILE_ELEMENT = "long_text_file_element"
}
