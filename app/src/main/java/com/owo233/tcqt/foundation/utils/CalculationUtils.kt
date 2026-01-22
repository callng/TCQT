package com.owo233.tcqt.foundation.utils

import com.owo233.tcqt.foundation.extensions.toHexString
import java.security.MessageDigest
import java.util.Locale

internal object CalculationUtils {

    private const val CSRF_TOKEN_END_STR = "tencentQQVIP123443safde&!%^%1282"

    fun getBkn(sKey: String): Int {
        var base = 5381
        for (element in sKey) {
            base += (base shl 5) + element.code
        }
        return base and 2147483647
    }

    fun getPsToken(pskey: String): Int {
        var base = 5381
        for (element in pskey) {
            base += (base shl 5) + element.code
        }
        return base and 2147483647
    }

    fun getCSRFToken(sKey: String): String {
        var cnt = 5381
        val stringBuilder = StringBuilder()
        stringBuilder.append(cnt shl 5)
        for (element in sKey) {
            stringBuilder.append((cnt shl 5) + element.code)
            cnt = element.code
        }
        stringBuilder.append(CSRF_TOKEN_END_STR)
        return getMd5ByString(stringBuilder.toString())!!.lowercase(Locale.getDefault())
    }

    fun getSuperToken(superKey: String): String {
        val md5Bytes = superKey.toByteArray(Charsets.UTF_8).md5()
        val n = ((md5Bytes[md5Bytes.size - 4].toInt() and 0xFF) shl 24) or
                ((md5Bytes[md5Bytes.size - 3].toInt() and 0xFF) shl 16) or
                ((md5Bytes[md5Bytes.size - 2].toInt() and 0xFF) shl 8) or
                (md5Bytes[md5Bytes.size - 1].toInt() and 0xFF)

        // 以无符号 32 位形式输出
        return n.toLong().and(0xFFFFFFFFL).toString()
    }

    fun getAuthToken(key: String): String {
        var hash = 0L
        for (c in key) {
            hash = hash * 33 + c.code
        }

        // 取无符号 32 位
        return (hash and 0xFFFFFFFFL).toString()
    }

    private fun getMd5ByString(str: String): String? {
        return try {
            val bytes = str.toByteArray()
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(bytes, 0, bytes.size)
            messageDigest.digest().toHexString(false)
        } catch (_: Exception) {
            null
        }
    }

    private fun ByteArray.md5(): ByteArray =
        MessageDigest.getInstance("MD5").digest(this)
}
