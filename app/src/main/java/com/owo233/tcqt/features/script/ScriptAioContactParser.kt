package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.log.LogUtils
import java.lang.reflect.Field

/**
 * 从宿主 `AIODelegate` 解析当前会话。
 *
 * 宿主没给稳定 getter，字段名也全被混淆，因此**只按类型找，不按名字找**：
 *
 * ```
 * AIODelegate.u  : Lcom/tencent/aio/data/AIOContact;
 * AIOContact.d   : I                  // chatType
 * AIOContact.e   : Ljava/lang/String; // peerUid
 * AIOContact.f   : Ljava/lang/String; // guildId
 * AIOContact.h   : Ljava/lang/String; // nick
 * ```
 *
 * 上面这套对应关系不是猜的：`AIOContact.toString()` 里逐字段拼的就是
 * `AIOContact(chatType=…, peerUid='…', guildId='…', nick='…')`，字段语义由此坐实
 * （QQ 9.3.70 classes4/classes5.dex）。
 *
 * 字段的**混淆名**每个版本都可能变，但类型与相对顺序稳定，所以这里按
 * 「类型为 AIOContact 的字段」+「内层 int / 三个 String 按声明顺序」读取。
 */
internal object ScriptAioContactParser {

    private const val AIO_CONTACT_TYPE = "com.tencent.aio.data.AIOContact"

    /** 宿主 Contact 字符串形态的键值对（兜底路径与测试用）。 */
    private val FIELD_PATTERN = Regex("""(\w+)=([^,)]*)""")

    /**
     * 解析 Host Contact / AIOContact 的字符串形态。
     *
     * 仅在拿不到实例、只能拿到 `toString()` 时使用；[fromDelegate] 走的是直读字段。
     *
     * @param fallbackUin 转换不出 UIN 时的兜底值
     */
    fun parseContactString(input: String, fallbackUin: String = ""): ScriptChatContext {
        val map = FIELD_PATTERN.findAll(input).associate {
            it.groupValues[1] to it.groupValues[2].trim('\'', ' ')
        }

        val chatType = map["chatType"]?.toIntOrNull() ?: 0
        return build(
            chatType = chatType,
            peerUid = map["peerUid"].orEmpty(),
            guildId = map["guildId"].orEmpty(),
            nick = map["nick"].orEmpty(),
            fallbackUin = fallbackUin,
        )
    }

    /** 从 AIODelegate 实例解析当前会话；拿不到时返回空会话。 */
    fun fromDelegate(delegate: Any, fallbackUin: String = ""): ScriptChatContext {
        val contact = contactValue(delegate)
        if (contact == null) {
            LogUtils.androidNoFilter.w(
                "脚本引擎: AIODelegate 上找不到 $AIO_CONTACT_TYPE 字段（宿主结构变了）"
            )
            return ScriptChatContext()
        }
        return fromContactObject(contact, fallbackUin)
    }

    /**
     * 从一个 AIOContact 形态的对象解析会话（字段直读，失败退化成解析 `toString()`）。
     *
     * 独立成公开内部方法是为了能单测：宿主类不可用，用结构等价的假对象即可覆盖。
     */
    fun fromContactObject(contact: Any, fallbackUin: String = ""): ScriptChatContext {
        val parsed = readAioContact(contact)
        if (parsed == null) {
            // 结构对不上时退化成解析 toString，至少拿到 chatType/peerUid
            val raw = runCatching { contact.toString() }.getOrNull()
            LogUtils.androidNoFilter.w("脚本引擎: AIOContact 字段结构变了，退化解析 raw=$raw")
            return raw?.let { runCatching { parseContactString(it, fallbackUin) }.getOrNull() }
                ?: ScriptChatContext()
        }

        return build(
            chatType = parsed.chatType,
            peerUid = parsed.peerUid,
            guildId = parsed.guildId,
            nick = parsed.nick,
            fallbackUin = fallbackUin,
        )
    }

    private data class AioContact(
        val chatType: Int,
        val peerUid: String,
        val guildId: String,
        val nick: String,
    )

    /** 读取 AIOContact 的 4 个字段：1 个 int + 3 个 String（按声明顺序）。 */
    private fun readAioContact(contact: Any): AioContact? = runCatching {
        val declared = contact.javaClass.declaredFields

        val chatType = declared.firstOrNull { it.type == Integer.TYPE }?.let { field ->
            field.isAccessible = true
            field.getInt(contact)
        } ?: return null

        val strings = declared
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .map { it.get(contact) as? String ?: "" }

        if (strings.size < 3) return null

        AioContact(
            chatType = chatType,
            peerUid = strings[0],
            guildId = strings[1],
            nick = strings[2],
        )
    }.onFailure {
        LogUtils.androidNoFilter.w("脚本引擎: 读取 AIOContact 失败", it)
    }.getOrNull()

    /** 统一出口：补 peerUin 的推导规则。 */
    private fun build(
        chatType: Int,
        peerUid: String,
        guildId: String,
        nick: String,
        fallbackUin: String,
    ): ScriptChatContext {
        val peerUin = when {
            // 群聊 / 频道：peerUid 本身就是群号
            chatType == ScriptRichText.CHAT_GROUP -> peerUid
            peerUid.isEmpty() -> fallbackUin
            else -> ScriptContactResolver.uinFromUid(peerUid).ifEmpty { fallbackUin }
        }
        return ScriptChatContext(chatType, peerUid, peerUin, guildId, nick)
    }

    /** 沿继承链找类型为 AIOContact 的字段并取值。 */
    private fun contactValue(delegate: Any): Any? {
        var clazz: Class<*>? = delegate.javaClass
        while (clazz != null && clazz != Any::class.java) {
            clazz.declaredFields.firstOrNull { field: Field ->
                field.type.name == AIO_CONTACT_TYPE
            }?.let { field ->
                runCatching {
                    field.isAccessible = true
                    return field.get(delegate)
                }.onFailure {
                    LogUtils.androidNoFilter.w("脚本引擎: 读取会话字段失败 ${field.name}", it)
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    /** 供测试与调试：列出候选字段。 */
    fun contactFieldNames(delegate: Any): List<String> {
        val names = mutableListOf<String>()
        var clazz: Class<*>? = delegate.javaClass
        while (clazz != null && clazz != Any::class.java) {
            clazz.declaredFields
                .filter { it.type.name == AIO_CONTACT_TYPE }
                .forEach { names += "${clazz.simpleName}.${it.name}" }
            clazz = clazz.superclass
        }
        return names
    }
}

/** UID → UIN 转换；单独抽出来便于测试替身。 */
internal object ScriptContactResolver {

    @Volatile
    var converter: ((String) -> String)? = null

    fun uinFromUid(uid: String): String =
        runCatching { converter?.invoke(uid).orEmpty() }.getOrDefault("")
}
