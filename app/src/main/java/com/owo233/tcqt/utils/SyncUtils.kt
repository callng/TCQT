package com.owo233.tcqt.utils

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicReference

object SyncUtils {

    private val handlerRef = AtomicReference<Handler>()

    private fun getHandler(): Handler {
        return handlerRef.get() ?: synchronized(this) {
            handlerRef.get() ?: Handler(Looper.getMainLooper()).also {
                handlerRef.set(it)
            }
        }
    }

    fun postDelayed(r: Runnable, ms: Long) {
        try {
            getHandler().postDelayed(r, ms)
        } catch (e: Exception) {
            Log.e("SyncUtils postDelayed失败", e)
        }
    }

    fun post(r: Runnable) {
        postDelayed(r, 0L)
    }

    fun runOnUiThread(r: Runnable) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                r.run()
            } else {
                post(r)
            }
        } catch (e: Exception) {
            Log.e("SyncUtils runOnUiThread失败", e)
        }
    }

    fun runOnUiThread(block: () -> Unit) {
        runOnUiThread(Runnable { block() })
    }
}
