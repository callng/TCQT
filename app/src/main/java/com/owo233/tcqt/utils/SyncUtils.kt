package com.owo233.tcqt.utils

import android.os.Handler
import android.os.Looper
import com.owo233.tcqt.utils.log.Log

object SyncUtils {

    private val mainHandler: Handler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(Looper.getMainLooper())
    }

    fun post(runnable: Runnable): Boolean {
        return runCatching {
            mainHandler.post(runnable)
        }.getOrElse {
            Log.e("SyncUtils Func post Error", it)
            false
        }
    }

    fun post(block: () -> Unit): Boolean {
        return post(Runnable(block))
    }

    fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
        return runCatching {
            mainHandler.postDelayed(runnable, delayMillis)
        }.getOrElse {
            Log.e("SyncUtils Func postDelayed Error", it)
            false
        }
    }

    fun postDelayed(delayMillis: Long, block: () -> Unit): Boolean {
        return postDelayed(Runnable(block), delayMillis)
    }

    fun runOnUiThread(runnable: Runnable) {
        runCatching {
            if (Looper.myLooper() === Looper.getMainLooper()) {
                runnable.run()
            } else {
                mainHandler.post(runnable)
            }
        }.onFailure {
            Log.e("SyncUtils Func runOnUiThread Error", it)
        }
    }

    fun runOnUiThread(block: () -> Unit) {
        runOnUiThread(Runnable(block))
    }

    fun removeCallbacks(runnable: Runnable) {
        runCatching {
            mainHandler.removeCallbacks(runnable)
        }.onFailure {
            Log.e("SyncUtils Func removeCallbacks Error", it)
        }
    }

    fun removeAllCallbacksAndMessages() {
        runCatching {
            mainHandler.removeCallbacksAndMessages(null)
        }.onFailure {
            Log.e("SyncUtils Func removeAllCallbacksAndMessages Error", it)
        }
    }
}
