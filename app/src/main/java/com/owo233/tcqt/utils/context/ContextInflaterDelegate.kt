package com.owo233.tcqt.utils.context

import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater

class ContextInflaterDelegate(
    private val wrapper: Context
) {

    private var inflater: LayoutInflater? = null

    fun getInflater(): LayoutInflater {
        if (inflater == null) {
            inflater = LayoutInflater
                .from(findContextImpl(wrapper))
                .cloneInContext(wrapper)
        }
        return inflater!!
    }

    private fun findContextImpl(context: Context): Context {
        var ctx = context
        while (ctx is ContextWrapper) {
            ctx = ctx.baseContext ?: break
        }
        return ctx
    }
}
