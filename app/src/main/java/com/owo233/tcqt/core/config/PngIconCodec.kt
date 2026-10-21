package com.owo233.tcqt.core.config

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.owo233.tcqt.core.log.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import kotlin.math.roundToInt
import androidx.core.graphics.scale

/** Self-contained PNG data also survives config backup and source-file deletion. */
internal object PngIconCodec {

    private const val MAX_ENCODED_LENGTH = 512 * 1024

    fun importPng(input: InputStream): String {
        val bytes = PngIconInput.read(input)
        val (width, height) = PngIconInput.dimensions(bytes)
        var sample = 1
        while (maxOf(width, height) / sample > PngIconInput.ICON_SIZE * 2) sample *= 2
        val decoded = requireNotNull(
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        ) { "无法读取 PNG 图片，请重新选择" }
        try {
            val scale = minOf(1f, PngIconInput.ICON_SIZE.toFloat() / maxOf(decoded.width, decoded.height))
            val icon = if (scale < 1f) decoded.scale(
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
            ) else decoded
            try {
                val output = ByteArrayOutputStream()
                check(icon.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG 图片保存失败" }
                return Base64.getEncoder().encodeToString(output.toByteArray()).also {
                    check(it.length <= MAX_ENCODED_LENGTH) { "PNG 图片保存失败" }
                }
            } finally {
                if (icon !== decoded) icon.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    fun decodeOrNull(value: String): Bitmap? {
        if (value.isEmpty()) return null
        return runCatching {
            require(value.length <= MAX_ENCODED_LENGTH)
            val bytes = Base64.getDecoder().decode(value)
            val (width, height) = PngIconInput.dimensions(bytes)
            require(width <= PngIconInput.ICON_SIZE && height <= PngIconInput.ICON_SIZE)
            requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        }.onFailure { Log.w("Failed to decode custom PNG icon; using host icon", it) }.getOrNull()
    }
}
