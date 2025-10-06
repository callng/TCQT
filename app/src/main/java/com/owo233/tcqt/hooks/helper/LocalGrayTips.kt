package com.owo233.tcqt.hooks.helper

import com.owo233.tcqt.ext.asJsonObject
import com.owo233.tcqt.ext.json
import com.owo233.tcqt.hooks.maple.MapleContact
import com.owo233.tcqt.utils.Log
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
    private val module by lazy {
        SerializersModule {
            polymorphic(GrayTipItem::class) {
                subclass(Text::class)
                subclass(MemberRef::class)
                subclass(Url::class)
                subclass(Image::class)
            }
        }
    }
    private val format by lazy {
        Json {
            serializersModule = module
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
            val json = Builder().apply(builder).build(align)
            val msgService = NTServiceFetcher.kernelService.wrapperSession?.msgService
            if (msgService == null) {
                Log.e("addLocalGrayTip failed, msgService is null")
            } else {
                when(contact) {
                    is MapleContact.Contact -> {
                        val element = JGE(busiId.toLong(), json.second.toString(), json.first, false, null)
                        msgService.addLocalJsonGrayTipMsg(contact.inner, element, true, true) { result, _ ->
                            if (result != 0) {
                                Log.e("addLocalJsonGrayTipMsg failed, result: $result")
                            }
                        }
                    }
                    is MapleContact.PublicContact -> {
                        val element = PJGE(busiId.toLong(), json.second.toString(), json.first, false, null)
                        msgService.addLocalJsonGrayTipMsg(contact.inner, element, true, true) { result, _ ->
                            if (result != 0) {
                                Log.e("addLocalJsonGrayTipMsg failed, result: $result")
                            }
                        }
                    }
                }
            }
        }.onFailure {
            Log.e("addLocalGrayTip failed", it)
        }
    }

    class Builder {
        private val items: ArrayList<GrayTipItem> = arrayListOf()
        private val showText = StringBuilder()

        fun text(string: String, col: String= "1"): Builder {
            items.add(Text(string, col))
            showText.append(string)
            return this
        }

        fun member(uid: String, uin: String, nick: String, col: String = "3"): Builder {
            items.add(MemberRef(uid, uid, uin, "0", nick, col))
            showText.append(nick)
            return this
        }

        fun msgRef(text: String, seq: Long, col: String = "3"): Builder {
            items.add(Url(
                text = text,
                jp = 58,
                param = mapOf(
                    "seq" to seq
                ).json.asJsonObject,
                col = col
            ))
            return this
        }

        fun imageJump(url: String, alt: String, jumpUrl: String = url, col: String = "3"): Builder {
            items.add(Image(
                src = url,
                alt = alt,
                jp = 58,
                param = mapOf(
                    "url" to jumpUrl
                ).json.asJsonObject,
                col = col
            ))
            showText.append(alt)
            return this
        }

        fun image(url: String, alt: String, col: String = "3"): Builder {
            items.add(Image(
                src = url,
                alt = alt,
                col = col
            ))
            showText.append(alt)
            return this
        }

        fun build(align: Align = Align.CENTER): Pair<String, JsonObject> {
            return showText.toString() to format.encodeToJsonElement(GrayTip(
                align = align,
                items = items
            )).asJsonObject
        }
    }

    @Serializable
    data class GrayTip(
        @SerialName("align") val align: Align,
        @SerialName("items") val items: List<GrayTipItem>
    )

    @Serializable
    sealed class GrayTipItem(
        @SerialName("type") val type: String
    )

    @SerialName("_text")
    @Serializable
    data class Text(
        @SerialName("txt") val text: String,
        @SerialName("col") val col: String = "1",
    ): GrayTipItem("nor")

    @SerialName("_member")
    @Serializable
    data class MemberRef(
        @SerialName("uid") val uid: String,
        @SerialName("jp") val jp: String, // UID
        @SerialName("uin") val uin: String,
        @SerialName("tp") val tp: String, // "0"
        @SerialName("nm") val nick: String,
        @SerialName("col") val col: String, // "3"
    ): GrayTipItem("qq")

    /**
     *  -> uid nick uin
     * 58 -> seq
     *  -> contact_chat_type contact_peer_id unread_cnt fetch_cnt first_unread_seq end_unread_seq
     *  -> url
     *  -> hippy_pay_url
     */
    @SerialName("_url")
    @Serializable
    data class Url(
        @SerialName("txt") val text: String,
        @SerialName("local_jp") val jp: Int,
        @SerialName("param") val param: JsonObject,
        @SerialName("col") val col: String,
    ): GrayTipItem("url")

    @SerialName("_img")
    @Serializable
    data class Image(
        @SerialName("src") val src: String,
        @SerialName("alt") val alt: String,
        @SerialName("local_jp") val jp: Int? = null,
        @SerialName("param") val param: JsonObject? = null,
        @SerialName("col") val col: String,
    ): GrayTipItem("img")

    @Serializable
    enum class Align {
        @SerialName("left") LEFT,
        @SerialName("center") CENTER,
        @SerialName("right") RIGHT,
        @SerialName("top") TOP,
        @SerialName("bottom") BOTTOM
    }
}
