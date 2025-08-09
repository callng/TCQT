package com.owo233.tcqt.utils

import android.os.Handler
import android.os.Looper

object SyncUtil {

    private lateinit var sHandler: Handler

    fun postDelayed(r: Runnable, ms: Long) {
        if (!::sHandler.isInitialized) {
            sHandler = Handler(Looper.getMainLooper())
        }
        sHandler.postDelayed(r, ms)
    }

    fun post(r: Runnable) {
        postDelayed(r, 0L)
    }

    fun runOnUiThread(r: Runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run()
        } else {
            post(r)
        }
    }
}
