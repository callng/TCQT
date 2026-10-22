package com.owo233.tcqt.host.service.funbox

import android.content.Context
import com.owo233.tcqt.core.log.Log
import java.io.File
import java.security.MessageDigest

/**
 * FunBox 语音分享仓库（照搬自 WeKit FunBoxVoiceRepository，仅保留浏览/搜索/下载）。
 *
 * OP 码：packs=20, my=23, items=21, search=29。
 * 语音对象下载：/vfile/voice/{objectId}（可能带 Sec 头 AES 加密，Client 内自动解密）。
 */
object FunBoxVoiceRepository {
    private const val TAG = "FunBoxVoice"

    /** 语音包（分享列表里的容器）。 */
    data class VoicePack(
        val id: String,
        val title: String,
        val itemCount: Int,
    )

    /** 语音条目（包内的单个音频）。 */
    data class VoiceItem(
        val id: String,
        val objectId: String,
        val title: String,
        val packId: String,
    )

    private fun writer(context: Context, vararg fields: String): ByteArray =
        FunBoxBinaryWriter().apply { fields.forEach { string(it) } }.build()

    fun listSharedPacks(context: Context): List<VoicePack> {
        val packs = FunBoxServiceClient.call(
            context,
            FunBoxServiceClient.OP_VOICE_PACKS,
            writer(context, FunBoxServiceClient.clientId(context)),
        ) { response ->
            response.objects { pack ->
                val id = pack.string()
                val title = pack.string()
                pack.long() // 未知字段（上传时间）
                pack.long() // 未知字段
                val count = pack.int()
                VoicePack(id, title, count)
            }
        }
        Log.i("$TAG: 分享语音包 ${packs.size} 个")
        return packs
    }

    fun loadSharedPack(context: Context, packId: String): List<VoiceItem> {
        val items = FunBoxServiceClient.call(
            context,
            FunBoxServiceClient.OP_VOICE_PACK_ITEMS,
            writer(context, FunBoxServiceClient.clientId(context), packId),
        ) { response ->
            val status = response.int()
            val message = response.string()
            val list = response.objects(::decodeSharedVoice)
            check(status == 0) { message.ifBlank { "语音包内容加载失败" } }
            list
        }
        Log.i("$TAG: 语音包 $packId 共 ${items.size} 条")
        return items
    }

    fun searchSharedVoices(context: Context, query: String): List<VoiceItem> {
        require(query.isNotBlank()) { "搜索内容不能为空" }
        val items = FunBoxServiceClient.call(
            context,
            FunBoxServiceClient.OP_VOICE_SEARCH,
            writer(context, FunBoxServiceClient.clientId(context), query.trim()),
        ) { response ->
            response.objects(::decodeSharedVoice)
        }
        Log.i("$TAG: 搜索「${query.trim()}」命中 ${items.size} 条")
        return items
    }

    private fun decodeSharedVoice(item: FunBoxBinaryReader): VoiceItem {
        val id = item.string()
        val objectId = item.string()
        val title = item.string()
        val packId = item.string()
        return VoiceItem(id, objectId, title, packId)
    }

    /**
     * 下载语音到本地缓存文件，返回文件路径。
     * 下载后做两层嗅探（照搬 WeKit）：
     * 1. 前 256 字节若是 JSON（以 { 开头），视为服务器错误响应，提取 msg 报错；
     * 2. 魔数嗅探真实格式（不信任 objectId/扩展名），据此决定缓存文件扩展名。
     */
    fun downloadVoice(context: Context, item: VoiceItem): File {
        val bytes = FunBoxServiceClient.downloadObject(context, "voice", item.objectId)
        require(bytes.isNotEmpty()) { "语音内容为空" }
        val prefix = bytes.copyOfRange(0, minOf(bytes.size, 256)).toString(Charsets.UTF_8).trimStart()
        if (prefix.startsWith("{")) {
            val message = Regex("\"msg\"\\s*:\\s*\"([^\"]+)\"").find(prefix)?.groupValues?.getOrNull(1)
            error(message ?: "服务器返回的不是音频数据")
        }
        val ext = sniffAudioExtension(bytes) ?: error("不支持的音频格式")
        val dir = File(context.cacheDir, "funbox_voice").apply { mkdirs() }
        val safeName = item.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)
        val file = File(dir, "$safeName.$ext")
        file.writeBytes(bytes)
        return file
    }

    /** 魔数嗅探：返回缓存用的扩展名（silk/amr 直接复用，其余交给转码）。 */
    fun sniffAudioExtension(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        // 腾讯 silk：0x02 + "#!SILK_V3"（10 字节）或标准 9 字节 "#!SILK_V3"
        if (bytes[0].toInt() == 0x02 && bytes.copyOfRange(1, 9).decodeToString() == "#!SILK_V3") return "slk"
        if (bytes.copyOfRange(0, 9).decodeToString() == "#!SILK_V3") return "slk"
        // AMR: "#!AMR\n"
        if (bytes.copyOfRange(0, 6).decodeToString() == "#!AMR\n") return "amr"
        // MP3: ID3 或 0xFFEx 帧
        if (bytes.copyOfRange(0, 3).decodeToString() == "ID3") return "mp3"
        if ((bytes[0].toInt() and 0xff) == 0xff && (bytes[1].toInt() and 0xe0) == 0xe0) return "mp3"
        // FLAC / OGG / WAV
        if (bytes.copyOfRange(0, 4).decodeToString() == "fLaC") return "flac"
        if (bytes.copyOfRange(0, 4).decodeToString() == "OggS") return "ogg"
        if (bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WAVE"
        ) return "wav"
        // M4A: ftyp box
        if (bytes.copyOfRange(4, 8).decodeToString() == "ftyp") return "m4a"
        return null
    }

    /** 本地缓存命中检查（同一语音不重复下载）。 */
    fun cachedVoiceFile(context: Context, item: VoiceItem): File? {
        val dir = File(context.cacheDir, "funbox_voice")
        if (!dir.isDirectory) return null
        val safeName = item.id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(64)
        val candidates = dir.listFiles { f -> f.name.startsWith("$safeName.") } ?: return null
        return candidates.firstOrNull { it.length() > 0 }
    }

    private fun md5Hex(file: File): String = runCatching {
        MessageDigest.getInstance("MD5").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
    }.getOrDefault("")
}
