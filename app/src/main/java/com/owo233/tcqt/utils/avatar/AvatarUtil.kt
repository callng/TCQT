/**
 * https://github.com/HdShare/NullAvatar
 */

package com.owo233.tcqt.utils.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.graphics.createBitmap
import java.io.File

object AvatarUtil {

    private const val CUSTOM_NAME = "NullAvatar.png"

    private fun getCustomBitmap(ctx: Context): Bitmap? {
        val file = File(ctx.filesDir, CUSTOM_NAME)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    private fun getDefaultBitmap(): Bitmap {
        return createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }
    }

    fun getBitmap(ctx: Context): Bitmap {
        return getCustomBitmap(ctx) ?: getDefaultBitmap()
    }
}
