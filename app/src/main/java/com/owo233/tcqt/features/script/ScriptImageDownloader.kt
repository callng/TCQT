package com.owo233.tcqt.features.script

import com.owo233.tcqt.core.log.LogUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * `[pic=URL]` 的图片下载：落到脚本缓存目录，返回本地路径。
 *
 * 宿主发图片要的是本地文件路径，脚本里直接给网络地址发不出去，所以要先下载。
 * 下载失败返回 null，由调用方降级成一段提示文本（不发半个元素出去）。
 */
internal object ScriptImageDownloader {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 16 * 1024

    fun download(url: String): String? = runCatching {
        val fileName = "net_img_${System.currentTimeMillis()}_${url.hashCode()}.jpg"
        val dest = File(ScriptElementFactory.imageCacheDir, fileName)

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }

        try {
            if (conn.responseCode !in 200..299) {
                LogUtils.androidNoFilter.w("脚本引擎: 图片下载失败 HTTP ${conn.responseCode} $url")
                return null
            }
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = BUFFER_SIZE)
                }
            }
        } finally {
            runCatching { conn.disconnect() }
        }

        dest.takeIf { it.length() > 0L }?.absolutePath
    }.onFailure {
        LogUtils.androidNoFilter.w("脚本引擎: 图片下载异常 $url", it)
    }.getOrNull()
}
