package com.owo233.tcqt.utils

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
        val md5Bytes = MessageDigest.getInstance("MD5")
            .digest(superKey.toByteArray(Charsets.UTF_8))
        val last4 = md5Bytes.takeLast(4)
        val value = (
                ((last4[0].toLong() and 0xFF) shl 24) or
                ((last4[1].toLong() and 0xFF) shl 16) or
                ((last4[2].toLong() and 0xFF) shl 8) or
                (last4[3].toLong() and 0xFF)).toInt()
        return value.toString()
    }

    fun getAuthToken(key: String): String {
        var hash: Long = 0L
        for (char in key) {
            hash = (hash * 33 + char.code) and 0xFFFFFFFFL
        }
        return hash.toString()
    }

    private fun getMd5ByString(str: String): String? {
        return try {
            val bytes = str.toByteArray()
            val messageDigest = MessageDigest.getInstance("MD5")
            messageDigest.update(bytes, 0, bytes.size)
            toHexString(messageDigest.digest())
        } catch (_: Exception) {
            null
        }
    }

    private fun toHexString(bArr: ByteArray): String = bArr.joinToString("") { "%02x".format(it) }
}
