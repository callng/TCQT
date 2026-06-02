package com.owo233.tcqt.hooks.func.notification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import androidx.core.graphics.drawable.IconCompat
import com.owo233.tcqt.HookEnv
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.reflect.callStaticMethod
import java.io.File

internal class QQAvatarHelper {

    private val avatarCache = LruCache<String, IconCompat>(50)

    private val md5Class by lazy(LazyThreadSafetyMode.NONE) {
        load("com.tencent.qphone.base.util.MD5")
    }

    private val avatarCachePath: String
        get() = File(
            HookEnv.application.getExternalFilesDir(null)?.parent,
            "Tencent/MobileQQ/head/_hd"
        ).absolutePath

    fun getAvatar(uin: String): IconCompat? {
        avatarCache[uin]?.let { return it }
        val bitmap = getAvatarFromFile(uin) ?: return null
        return IconCompat.createWithBitmap(bitmap).also { avatarCache.put(uin, it) }
    }

    private fun getAvatarFromFile(uin: String): Bitmap? {
        val first = toMd5(uin) ?: return null
        val second = toMd5(first + uin) ?: return null
        val md5 = toMd5(second + uin) ?: return null
        val file = File(avatarCachePath, "$md5.jpg_")
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)?.let(::getCroppedBitmap)
    }

    private fun toMd5(value: String): String? {
        return runCatching {
            md5Class?.callStaticMethod("toMD5", value) as? String
        }.getOrNull()
    }

    private fun getCroppedBitmap(bitmap: Bitmap): Bitmap {
        val radius = bitmap.width.coerceAtMost(bitmap.height)
        val output = Bitmap.createBitmap(radius, radius, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            color = -0xbdbdbe
        }
        val rect = Rect(0, 0, radius, radius)
        val rectF = RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(
            rectF.left + rectF.width() / 2,
            rectF.top + rectF.height() / 2,
            radius / 2f,
            paint
        )

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}
