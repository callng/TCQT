/**
 * https://github.com/HdShare/NullAvatar
 */

package com.owo233.tcqt.utils.avatar

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

fun Bitmap.toStream(): ByteArrayOutputStream {
    return ByteArrayOutputStream().apply {
        compress(Bitmap.CompressFormat.PNG, 100, this)
    }
}

fun Bitmap.toByteArray(): ByteArray {
    return ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
}
