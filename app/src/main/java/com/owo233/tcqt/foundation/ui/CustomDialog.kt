package com.owo233.tcqt.foundation.ui

import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.owo233.tcqt.foundation.ui.CommonContextWrapper.Companion.toCompatibleContext

class CustomDialog {

    private var mFailsafeDialog: AlertDialog? = null
    private lateinit var mBuilder: AlertDialog.Builder
    private var isMessageSelectable: Boolean = false

    fun setMessageSelectable(selectable: Boolean): CustomDialog = apply {
        this.isMessageSelectable = selectable
    }

    fun setCancelable(flag: Boolean): CustomDialog = apply {
        mBuilder.setCancelable(flag)
    }

    fun setTitle(title: String): CustomDialog = apply {
        mFailsafeDialog?.setTitle(title) ?: mBuilder.setTitle(title)
    }

    fun setMessage(msg: CharSequence): CustomDialog = apply {
        mFailsafeDialog?.setMessage(msg) ?: mBuilder.setMessage(msg)
    }

    val context: Context
        get() = mFailsafeDialog?.context ?: mBuilder.context

    fun setView(v: View): CustomDialog = apply {
        mFailsafeDialog?.setView(v) ?: mBuilder.setView(v)
    }

    fun setPositiveButton(textId: Int, listener: DialogInterface.OnClickListener?): CustomDialog {
        return setPositiveButton(context.getString(textId), listener)
    }

    fun setNegativeButton(textId: Int, listener: DialogInterface.OnClickListener?): CustomDialog {
        return setNegativeButton(context.getString(textId), listener)
    }

    fun ok(): CustomDialog = apply {
        setPositiveButton(android.R.string.ok, null)
    }

    fun setPositiveButton(text: String, listener: DialogInterface.OnClickListener?): CustomDialog = apply {
        mBuilder.setPositiveButton(text, listener)
    }

    fun setNeutralButton(text: String, listener: DialogInterface.OnClickListener?): CustomDialog = apply {
        mBuilder.setNeutralButton(text, listener)
    }

    fun setNeutralButton(textId: Int, listener: DialogInterface.OnClickListener?): CustomDialog = apply {
        mBuilder.setNeutralButton(textId, listener)
    }

    fun setNegativeButton(text: String, listener: DialogInterface.OnClickListener?): CustomDialog = apply {
        mBuilder.setNegativeButton(text, listener)
    }

    fun create(): AlertDialog {
        if (mFailsafeDialog == null) {
            mFailsafeDialog = mBuilder.create()
        }
        return mFailsafeDialog!!
    }

    fun show(): AlertDialog {
        val dialog = create()
        dialog.show()
        applyCustomDecor()
        return dialog
    }

    fun dismiss() {
        mFailsafeDialog?.dismiss()
    }

    val isShowing: Boolean
        get() = mFailsafeDialog?.isShowing == true

    private fun applyCustomDecor() {
        val dialog = mFailsafeDialog ?: return

        if (isMessageSelectable) {
            dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
        }
    }

    companion object {
        @JvmStatic
        fun create(ctx: Context): CustomDialog {
            val ref = CustomDialog()
            ref.mBuilder = AlertDialog.Builder(ctx.toCompatibleContext())
            return ref
        }
    }
}
