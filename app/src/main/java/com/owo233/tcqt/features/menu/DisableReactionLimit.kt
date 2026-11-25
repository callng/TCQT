package com.owo233.tcqt.features.menu

import android.util.SparseIntArray
import androidx.core.util.forEach
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodReplace
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Modifier

@RegisterAction
object DisableReactionLimit : Feature(
    key = "disable_reaction_limit",
    name = "禁止过滤反应表情",
    desc = "显示 QQ 已有映射的全部反应表情（Emoji）。",
    requires = Requires(host = Requires.Host.QQOnly),
), DexKitTask {

    override fun install() {
        // ES has a separate curated catalog. Keep reaction panels on QQ's native
        // legacy path so one expanded catalog also controls click/send conversion.
        if (ES_SWITCH in getCacheKeys()) {
            requireMethod(ES_SWITCH).hookBefore { it.result = false }
        }

        load("com.tencent.mobileqq.guild.emoj.api.impl.QQGuildEmojiApiImpl")?.let {
            it.hookMethodReplace({
                name = "getFilterEmojiData"
            }) { null }
            it.hookMethodReplace({
                name = "getFilterSysData"
            }) { null }
        }

        requireMethod(REACTION_INTERVAL).hookBefore { it.result = 0L }

        val constants = "com.tencent.mobileqq.text.EmotcationConstants".toClass
        val codesField = constants.findField { name = "EMOJI_CODES" }
        val emojiMapField = constants.findField { name = "EMOJI_MAP" }
        val drawableCountField = constants.findField { name = "VALID_EMOJI_COUNT" }
        val emojiUtil = "com.tencent.mobileqq.emoticon.QQEmojiUtil".toClass
        val getOrder = emojiUtil.findMethod { name = "getOrderList"; paramCount = 0 }
        val resMgrClass = "com.tencent.mobileqq.emoticon.QQSysAndEmojiResMgr".toClass
        val getResMgr = resMgrClass.findMethod { name = "getInstance"; paramCount = 0 }
        val getResImpl = resMgrClass.findMethod { name = "getResImpl"; paramTypes(int) }
        val resInfoClass = "com.tencent.mobileqq.emoticon.QQSysAndEmojiResInfo".toClass
        val getFullCodes = resInfoClass.findMethod { name = "getFullFaceCodeList"; paramCount = 0 }
        val infoClass = "com.tencent.mobileqq.emoticonview.SystemAndEmojiEmoticonInfo".toClass
        val titleField = infoClass.findField { name = "title" }
        val emojiTitleField = infoClass.findField { name = "TITLE_EMOJI" }
        val getEmojiData = infoClass.findMethod {
            name = "getEmojiEmoticonPanelData"
            paramTypes(int, arrayList)
        }
        val controllerClass = "com.tencent.mobileqq.emoticonview.EmoticonPanelController".toClass
        val callbackField = controllerClass.findField { name = "callback" }
        val getPanels = controllerClass.findMethod { name = "getPanelDataList"; paramCount = 0 }
        val panelInfoClass = "com.tencent.mobileqq.emoticonview.EmotionPanelInfo".toClass
        val panelTypeField = panelInfoClass.findField { name = "type" }
        val columnField = "com.tencent.mobileqq.emoticonview.BaseEmotionAdapter".toClass
            .findField { name = "columnNum" }
        val adapterClass = "com.tencent.mobileqq.emoticonview.EmotionPanelViewPagerAdapter".toClass
        val controllerField = adapterClass.findField { name = "panelController" }
        val staticCatalog by lazy {
            ReactionEmojiCatalog(codesField.get(null) as IntArray, emptyMap(), emptyList())
        }
        var catalog: ReactionEmojiCatalog? = null

        val emojiResClass = "com.tencent.mobileqq.emoticon.QQEmojiResImpl".toClass
        val getServerId = emojiResClass.findMethod {
            name = "getServerId"
            paramTypes(int)
        }
        getServerId.hookAfter { param ->
            // Downloaded IDs are file offsets, not Unicode IDs. Use the same
            // mapping for the picker and sending, including configured entries.
            (catalog ?: staticCatalog).unicodeByLocalId[param.args[0] as Int]?.let {
                param.result = it
            }
        }

        emojiResClass.findMethod {
            name = "getLocalId"
            paramTypes(int)
        }.hookAfter { param ->
            val unicode = param.args[0] as Int
            if (param.result == unicode) {
                (catalog ?: staticCatalog).localIdByUnicode[unicode]?.let { param.result = it }
            }
        }
        emojiResClass.findMethod {
            name = "getDrawable"
            paramTypes(int)
        }.hookBefore { param ->
            val localId = param.args[0] as Int
            if (localId < drawableCountField.getInt(null)) return@hookBefore
            val unicode = (catalog ?: staticCatalog).unicodeByLocalId[localId] ?: return@hookBefore
            param.result = ReactionEmojiDrawable(
                unicode,
                (32 * hostApp.resources.displayMetrics.density).toInt(),
            )
        }
        emojiResClass.findMethod {
            name = "getDescription"
            paramTypes(int)
        }.hookAfter { param ->
            if ((param.result as? String).isNullOrEmpty()) {
                (catalog ?: staticCatalog).unicodeByLocalId[param.args[0] as Int]?.let {
                    param.result = String(Character.toChars(it))
                }
            }
        }

        fun expandEmojiData(originalData: List<*>, columns: Int): List<*> {
            val emojiMap = emojiMapField.get(null) as SparseIntArray
            val unicodeToLocal = buildMap {
                emojiMap.forEach { unicode, localId -> put(unicode, localId) }
            }
            val emojiRes = getResImpl.invoke(getResMgr.invoke(null), 2)
            @Suppress("UNCHECKED_CAST")
            val configuredIds = getFullCodes.invoke(emojiRes) as List<Int>
            val configuredMappings = configuredIds.associateWith {
                getServerId.invoke(emojiRes, it) as Int
            }
            @Suppress("UNCHECKED_CAST")
            val expanded = ReactionEmojiCatalog(
                codesField.get(null) as IntArray,
                unicodeToLocal,
                getOrder.invoke(null) as List<Int>,
                configuredMappings,
            )
            catalog = expanded
            val emojiData = getEmojiData.invoke(null, columns, expanded.localIds) as List<*>
            val emojiTitle = emojiTitleField.get(null)
            emojiData.forEach { titleField.set(it, emojiTitle) }
            val emojiStart = originalData.indexOfFirst {
                infoClass.isInstance(it) && titleField.get(it) == emojiTitle
            }
            return ArrayList<Any?>().apply {
                addAll(if (emojiStart >= 0) originalData.subList(0, emojiStart) else originalData)
                addAll(emojiData)
            }
        }

        // The floating reaction picker builds its own data without using the
        // main emoticon panel adapter. Expand after its reaction-only filter.
        val filter = requireMethod(MORE_PANEL_FILTER)
        require(filter.parameterTypes.toSet() == setOf(List::class.java, Int::class.javaPrimitiveType))
        // QQ 9.2.30–9.2.66 uses (int, List); other checked versions use (List, int).
        val columnsIndex = filter.parameterTypes.indexOf(Int::class.javaPrimitiveType)
        filter.hookAfter { param ->
            param.result = expandEmojiData(param.result as List<*>, param.args[columnsIndex] as Int)
        }

        adapterClass.findMethod { name = "getEmotionPanelData"; paramCount = 3 }.hookAfter { param ->
            val panelType = param.args[0] as Int
            // EmotionPanelInfo type 16 (reaction recents) is loaded as panel type 18.
            if (panelType !in 16..18) return@hookAfter
            val controller = controllerField.get(param.thisObject)
            val callback = callbackField.get(controller) ?: return@hookAfter
            if (!callback.javaClass.name.startsWith(
                    "com.tencent.qqnt.emotion.emoreply.AIOEmoReplyDialog\$"
                )) return@hookAfter

            val panels = getPanels.invoke(controller) as List<*>
            if (panelType != 17 && panels.any { panelTypeField.getInt(it) == 2 }) return@hookAfter
            param.result = expandEmojiData(param.result as List<*>, columnField.getInt(param.args[1]))
        }
    }

    override fun getCacheKeys(): Set<String> = getQueryMap().keys.let { keys ->
        if (load(ES_SWITCH_CLASS) == null) keys - ES_SWITCH else keys
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        ES_SWITCH to FindMethod.create().apply {
            matcher {
                declaredClass(ES_SWITCH_CLASS)
                paramCount(0)
                returnType("boolean")
            }
        },
        MORE_PANEL_FILTER to FindMethod.create().apply {
            searchPackages("com.tencent.qqnt.aio.menu.emoreply")
            matcher {
                paramCount(2)
                returnType("java.util.List")
                usingEqStrings("AIOEmoReplyMorePanel", "after filterEmotionData. filteredData size:")
            }
        },
        REACTION_INTERVAL to FindMethod.create().apply {
            matcher {
                declaredClass { usingEqStrings("AIOEmoReplyUtils") }
                modifiers(Modifier.PUBLIC or Modifier.STATIC or Modifier.FINAL)
                paramCount(0)
                returnType("long")
            }
        },
    )

    private const val ES_SWITCH_CLASS = "com.tencent.qqnt.aio.emoReply.AioEsReactionSwitch"
    private const val ES_SWITCH = "reaction_es_switch"
    private const val MORE_PANEL_FILTER = "reaction_more_panel_filter"
    private const val REACTION_INTERVAL = "reaction_interval"
}
