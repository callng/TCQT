package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.internals.QQInterfaces
import com.owo233.tcqt.utils.log.Log
import com.owo233.tcqt.utils.proto2json.asJsonObject
import com.owo233.tcqt.utils.proto2json.json
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayElement as JGE
import com.tencent.qqnt.kernelpublic.nativeinterface.JsonGrayElement as PJGE

object LocalGrayTips {
    private const val TYPE_NOR = "nor"
    private const val TYPE_QQ = "qq"
    private const val TYPE_URL = "url"
    private const val TYPE_IMG = "img"
    private const val DEFAULT_COL = "1"
    private const val LINK_COL = "3"

    private val module = SerializersModule {
        polymorphic(GrayTipItem::class) {
            subclass(Text::class)
            subclass(MemberRef::class)
            subclass(Url::class)
            subclass(Image::class)
        }
    }

    private val format by lazy {
        Json {
            serializersModule = module
            encodeDefaults = true
            ignoreUnknownKeys = true
            classDiscriminator = "_type"
        }
    }

    fun addLocalGrayTip(
        contact: MapleContact,
        busiId: Int = JsonGrayBusiId.AIO_ROBOT_SAFETY_TIP,
        align: Align = Align.CENTER,
        builder: Builder.() -> Unit
    ) {
        runCatching {
            val (showText, jsonObject) = Builder().apply(builder).build(align)
            val jsonString = jsonObject.toString()
            val msgService = QQInterfaces.msgService

            val busiIdLong = busiId.toLong()

            when (contact) {
                is MapleContact.Contact -> {
                    val element = JGE(busiIdLong, jsonString, showText, false, null)
                    msgService.addLocalJsonGrayTipMsg(contact.inner, element, true, true) { result, _ ->
                        if (result != 0) Log.e("addLocalJsonGrayTipMsg failed: $result")
                    }
                }
                is MapleContact.PublicContact -> {
                    val element = PJGE(busiIdLong, jsonString, showText, false, null)
                    msgService.addLocalJsonGrayTipMsg(contact.inner, element, true, true) { result, _ ->
                        if (result != 0) Log.e("addLocalJsonGrayTipMsg failed: $result")
                    }
                }
            }
        }.onFailure {
            Log.e("addLocalGrayTip fatal error", it)
        }
    }

    class Builder {
        private val items = ArrayList<GrayTipItem>()
        private val showText = StringBuilder()

        fun text(string: String, col: String = DEFAULT_COL) = apply {
            items.add(Text(string, col))
            showText.append(string)
        }

        fun member(uid: String, uin: String, nick: String, col: String = LINK_COL) = apply {
            items.add(MemberRef(uid, uid, uin, "0", nick, col))
            showText.append(nick)
        }

        fun msgRef(text: String, seq: Long, col: String = LINK_COL) = apply {
            val param = mapOf("seq" to seq).json.asJsonObject
            items.add(Url(text, 58, param, col))
            showText.append(text)
        }

        fun imageJump(url: String, alt: String, jumpUrl: String = url, col: String = LINK_COL) = apply {
            val param = mapOf("url" to jumpUrl).json.asJsonObject
            items.add(Image(url, alt, 58, param, col))
            showText.append(alt)
        }

        fun image(url: String, alt: String, col: String = LINK_COL) = apply {
            items.add(Image(url, alt, col = col))
            showText.append(alt)
        }

        fun build(align: Align): Pair<String, JsonObject> {
            val grayTip = GrayTip(align, items)
            return showText.toString() to format.encodeToJsonElement(grayTip).asJsonObject
        }
    }

    @Serializable
    data class GrayTip(
        @SerialName("align") val align: Align,
        @SerialName("items") val items: List<GrayTipItem>
    )

    @Serializable
    sealed class GrayTipItem

    @Serializable
    @SerialName("text_item")
    data class Text(
        @SerialName("txt") val text: String,
        @SerialName("col") val col: String = DEFAULT_COL,
        @SerialName("type") val type: String = TYPE_NOR
    ) : GrayTipItem()

    @Serializable
    @SerialName("member_item")
    data class MemberRef(
        @SerialName("uid") val uid: String,
        @SerialName("jp") val jp: String,
        @SerialName("uin") val uin: String,
        @SerialName("tp") val tp: String,
        @SerialName("nm") val nick: String,
        @SerialName("col") val col: String,
        @SerialName("type") val type: String = TYPE_QQ
    ) : GrayTipItem()

    @Serializable
    @SerialName("url_item")
    data class Url(
        @SerialName("txt") val text: String,
        @SerialName("local_jp") val jp: Int,
        @SerialName("param") val param: JsonObject,
        @SerialName("col") val col: String,
        @SerialName("type") val type: String = TYPE_URL
    ) : GrayTipItem()

    @Serializable
    @SerialName("img_item")
    data class Image(
        @SerialName("src") val src: String,
        @SerialName("alt") val alt: String,
        @SerialName("local_jp") val jp: Int? = null,
        @SerialName("param") val param: JsonObject? = null,
        @SerialName("col") val col: String,
        @SerialName("type") val type: String = TYPE_IMG
    ) : GrayTipItem()

    @Serializable
    enum class Align {
        @SerialName("left") LEFT,
        @SerialName("center") CENTER,
        @SerialName("right") RIGHT,
        @SerialName("top") TOP,
        @SerialName("bottom") BOTTOM
    }
}
