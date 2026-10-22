package com.owo233.tcqt.host.service.funbox

import android.content.Context
import com.owo233.tcqt.core.log.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.util.Calendar
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * FunBox 分享后端客户端（照搬自 WeKit FunBoxServiceClient，OkHttp → HttpURLConnection）。
 *
 * 服务发现：阿里 DoH 查 resolve.fpfast.top 的 TXT 记录 → Base64 → JSON 得到 API/对象存储候选，
 * 逐个探活后缓存；请求失败（404/408/5xx/IO）时作废缓存换下一个候选重试一次。
 *
 * 协议：POST {host}/funbox/api/req2
 *   envelope = BinaryWriter{ int(op), bytes(TEA(sessionKey,payload)), bytes(SM2(sessionKey)),
 *                            long(ts), string(rand8), long(payloadSize) } → gzip
 *   header "ph" = 反滥用证明；响应 = Reader{ bytes(加密数据), int(status) } → TEA 解密。
 */
object FunBoxServiceClient {
    private const val TAG = "FunBoxClient"
    private const val RESOLVER_NAME = "resolve.fpfast.top"
    private const val DOH_URL = "https://223.5.5.5/resolve"
    private const val OBJECT_SECURITY_HEADER = "Sec"
    private const val CACHE_API_HOST = "funbox_panel_api_host"
    private const val CACHE_OBJECT_HOST = "funbox_panel_object_host"
    private const val CACHE_CLIENT_ID = "funbox_panel_client_id"
    private const val PREFS_NAME = "tcqt_funbox"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    // 业务操作码（照搬 WeKit）
    const val OP_PROBE = 100
    const val OP_VOICE_PACKS = 20
    const val OP_VOICE_MY_PACKS = 23
    const val OP_VOICE_PACK_ITEMS = 21
    const val OP_VOICE_SEARCH = 29

    private val random = java.security.SecureRandom()

    /** 弱身份：首次使用时生成随机 ID 存本地（WeKit 用 wxId，这里不依赖微信身份）。 */
    fun clientId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(CACHE_CLIENT_ID, null)?.let { return it }
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val id = buildString(32) { repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) } }
        prefs.edit().putString(CACHE_CLIENT_ID, id).apply()
        return id
    }

    fun <T> call(
        context: Context,
        operation: Int,
        requestPayload: ByteArray,
        decode: (FunBoxBinaryReader) -> T,
    ): T {
        val host = apiHost(context)
        try {
            return callOnHost(host, operation, requestPayload, decode)
        } catch (error: Throwable) {
            if (!isRetryableHostFailure(error)) throw error
            Log.w("$TAG: op=$operation 在 $host 失败，换候选重试", error)
            invalidateApiHost(context, host)
            val replacement = apiHost(context, excludedHost = host)
            Log.i("$TAG: op=$operation 重试 host=$replacement")
            return callOnHost(replacement, operation, requestPayload, decode)
        }
    }

    private fun <T> callOnHost(
        host: String,
        operation: Int,
        requestPayload: ByteArray,
        decode: (FunBoxBinaryReader) -> T,
    ): T {
        val sessionKey = randomText(32)
        val encryptedPayload = FunBoxCrypto.teaEncrypt(sessionKey, requestPayload)
        val envelope = FunBoxBinaryWriter().apply {
            int(operation)
            bytes(encryptedPayload)
            bytes(FunBoxCrypto.sm2Encrypt(sessionKey.toByteArray()))
            long(System.currentTimeMillis())
            string(randomText(8))
            long(requestPayload.size.toLong())
        }.build().gzip()
        val bytes = postForBytes(
            url = host.trimEnd('/') + "/funbox/api/req2",
            body = envelope,
            headers = mapOf("ph" to requestProof()),
        )
        require(bytes.isNotEmpty()) { "FunBox 服务器返回空响应" }
        val responseEnvelope = FunBoxBinaryReader(bytes)
        val encrypted = responseEnvelope.bytes()
        val status = responseEnvelope.int()
        check(status == 0) { "FunBox 服务器错误码: $status" }
        val decoded = FunBoxCrypto.teaDecrypt(sessionKey, encrypted)
        return decode(FunBoxBinaryReader(decoded))
    }

    /** 对象存储直链。type: thumb/image/voice/vfun。 */
    fun objectUrl(context: Context, type: String, objectId: String): String {
        val host = objectHost(context)
        return host.trimEnd('/') + "/vfile/$type/$objectId"
    }

    /** 下载对象存储文件；响应带 Sec 头时用该 key 做 AES/CBC 解密（key 兼做 IV）。 */
    fun downloadObject(context: Context, type: String, objectId: String): ByteArray {
        val host = objectHost(context)
        return try {
            downloadBlocking(host.trimEnd('/') + "/vfile/$type/$objectId")
        } catch (error: Throwable) {
            if (!isRetryableHostFailure(error)) throw error
            Log.w("$TAG: 对象下载失败 type=$type host=$host，换候选重试", error)
            invalidateObjectHost(context, host)
            val replacement = objectHost(context, excludedHost = host)
            downloadBlocking(replacement.trimEnd('/') + "/vfile/$type/$objectId")
        }
    }

    private fun downloadBlocking(url: String): ByteArray {
        val connection = openConnection(url, timeoutMs = 60_000)
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code, connection.responseMessage ?: "")
            val body = connection.inputStream.use { it.readBytes() }
            val securityKey = connection.getHeaderField(OBJECT_SECURITY_HEADER)
            if (securityKey.isNullOrEmpty()) body else FunBoxCrypto.decryptObject(body, securityKey)
        } finally {
            connection.disconnect()
        }
    }

    // ---------- 服务发现与探活 ----------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun cachedHost(context: Context, key: String): String =
        prefs(context).getString(key, null).orEmpty()

    private fun saveHost(context: Context, key: String, host: String) {
        prefs(context).edit().putString(key, host).apply()
    }

    private fun apiHost(context: Context, excludedHost: String? = null): String {
        cachedHost(context, CACHE_API_HOST).takeIf { it.isNotBlank() && it != excludedHost }?.let { return it }
        val (apiCandidates, _) = resolveCandidates()
        val host = apiCandidates.firstOrNull { it != excludedHost && probeApi(it) }
            ?: error("FunBox API 服务不可达")
        saveHost(context, CACHE_API_HOST, host)
        Log.i("$TAG: 选中 API host=$host")
        return host
    }

    private fun objectHost(context: Context, excludedHost: String? = null): String {
        cachedHost(context, CACHE_OBJECT_HOST).takeIf { it.isNotBlank() && it != excludedHost }?.let { return it }
        val (_, objectCandidates) = resolveCandidates()
        val host = objectCandidates.firstOrNull { it != excludedHost && probeObject(it) }
            ?: error("FunBox 对象存储服务不可达")
        saveHost(context, CACHE_OBJECT_HOST, host)
        Log.i("$TAG: 选中对象存储 host=$host")
        return host
    }

    /** 阿里 DoH 查 TXT → 多段引号拼接 → Base64 → {"vapi":[...],"vraw":[...]}。 */
    private fun resolveCandidates(): Pair<List<String>, List<String>> {
        val url = DOH_URL + "?name=" + URLEncoder.encode(RESOLVER_NAME, "UTF-8") + "&type=TXT"
        val body = try {
            val connection = openConnection(url, timeoutMs = CONNECT_TIMEOUT)
            try {
                if (connection.responseCode !in 200..299) throw IOException("DoH HTTP ${connection.responseCode}")
                connection.inputStream.use { String(it.readBytes()) }
            } finally {
                connection.disconnect()
            }
        } catch (error: IOException) {
            throw RuntimeException("FunBox DoH 解析失败", error)
        }
        val answer = JSONObject(body).optJSONArray("Answer")
            ?.let { array -> (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.optString("data")?.takeIf(String::isNotBlank)
            } }
            ?.map(::joinTxtFragments)
            ?.filter(String::isNotBlank)
            ?.joinToString("")
            ?.takeIf(String::isNotBlank)
            ?: error("FunBox DoH 无 TXT 记录")
        val decoded = String(Base64.getDecoder().decode(answer))
        val json = JSONObject(decoded)
        fun arr(key: String): List<String> =
            json.optJSONArray(key)?.let { array -> List(array.length()) { i -> array.getString(i) } } ?: emptyList()
        val api = arr("vapi")
        val objects = arr("vraw")
        Log.i("$TAG: DoH 解析 api=${api.size} obj=${objects.size}")
        return api to objects
    }

    /** TXT 记录可能被切成多段带引号的字符串（dnsjava TXT#getStrings 语义），需拼接。 */
    private fun joinTxtFragments(value: String): String {
        val fragments = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
            .findAll(value)
            .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
            .toList()
        return if (fragments.isNotEmpty()) fragments.joinToString("") else value.trim()
    }

    /** API 探活：发一个 OP=100 的空载荷请求，能解出响应即视为可用。 */
    private fun probeApi(host: String): Boolean = runCatching {
        val payload = FunBoxBinaryWriter().apply { long(0L) }.build()
        val sessionKey = randomText(32)
        val envelope = FunBoxBinaryWriter().apply {
            int(OP_PROBE)
            bytes(FunBoxCrypto.teaEncrypt(sessionKey, payload))
            bytes(FunBoxCrypto.sm2Encrypt(sessionKey.toByteArray()))
            long(System.currentTimeMillis())
            string(randomText(8))
            long(payload.size.toLong())
        }.build().gzip()
        val bytes = postForBytes(
            host.trimEnd('/') + "/funbox/api/req2",
            envelope,
            mapOf("ph" to requestProof()),
        )
        if (bytes.isEmpty()) return@runCatching false
        val reader = FunBoxBinaryReader(bytes)
        val encrypted = reader.bytes()
        val status = reader.int()
        status == 0 && encrypted.isNotEmpty()
    }.onFailure { Log.w("$TAG: API 探活失败 host=$host: ${it.message}") }.getOrDefault(false)

    /** 对象存储探活：GET /vfile/vfun/vtest 应返回 path 本身或 "success"。 */
    private fun probeObject(host: String): Boolean = runCatching {
        val testPath = "/vfile/vfun/vtest"
        val connection = openConnection(host.trimEnd('/') + testPath, timeoutMs = CONNECT_TIMEOUT)
        try {
            if (connection.responseCode !in 200..299) return@runCatching false
            val body = connection.inputStream.use { String(it.readBytes()) }
            body == testPath || body == "success"
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w("$TAG: 对象探活失败 host=$host: ${it.message}") }.getOrDefault(false)

    private fun invalidateApiHost(context: Context, host: String) {
        if (cachedHost(context, CACHE_API_HOST) == host) saveHost(context, CACHE_API_HOST, "")
    }

    private fun invalidateObjectHost(context: Context, host: String) {
        if (cachedHost(context, CACHE_OBJECT_HOST) == host) saveHost(context, CACHE_OBJECT_HOST, "")
    }

    // ---------- HTTP 基础 ----------

    private fun postForBytes(url: String, body: ByteArray, headers: Map<String, String>): ByteArray {
        val connection = openConnection(url, timeoutMs = READ_TIMEOUT, method = "POST")
        return try {
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            connection.setDoOutput(true)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) throw HttpStatusException(code, connection.responseMessage ?: "")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, timeoutMs: Int, method: String = "GET"): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 15)")
        connection.setRequestProperty("Accept-Encoding", "identity")
        return connection
    }

    /** 反滥用证明：(秒级时间戳 × 当天小时数 + 一年中第几天)。 */
    private fun requestProof(): String {
        val calendar = Calendar.getInstance()
        return (System.currentTimeMillis() / 1000L * calendar.get(Calendar.HOUR_OF_DAY) +
                calendar.get(Calendar.DAY_OF_YEAR)).toString()
    }

    private fun isRetryableHostFailure(error: Throwable): Boolean = when (error) {
        is HttpStatusException -> error.code == 404 || error.code == 408 || error.code >= 500
        is IOException -> true
        else -> false
    }

    private fun randomText(length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return buildString(length) { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    private fun ByteArray.gzip(): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(this) }
        output.toByteArray()
    }

    private fun ByteArray.ungzip(): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(inputStream()).use { it.copyTo(output) }
        return output.toByteArray()
    }

    private class HttpStatusException(val code: Int, message: String) :
        IOException("HTTP $code: $message")
}
