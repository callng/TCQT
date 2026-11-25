package com.owo233.tcqt.features.menu

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

internal class ReactionEmojiDrawable(unicode: Int, private val size: Int) : Drawable() {
    private val text = String(Character.toChars(unicode)) + if (unicode <= 0xffff) "\ufe0f" else ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun draw(canvas: Canvas) {
        paint.textSize = bounds.height() * 0.8f
        val width = paint.measureText(text)
        if (width > bounds.width()) paint.textSize *= bounds.width() / width
        val metrics = paint.fontMetrics
        canvas.drawText(
            text,
            bounds.exactCenterX(),
            bounds.exactCenterY() - (metrics.ascent + metrics.descent) / 2,
            paint,
        )
    }

    override fun getIntrinsicWidth(): Int = size
    override fun getIntrinsicHeight(): Int = size

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
