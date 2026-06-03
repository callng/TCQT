package com.owo233.tcqt.hooks.helper

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import com.owo233.tcqt.utils.log.Log
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

object UnreadBadgeHelper {

    private const val QUI_BADGE_CLASS = "com.tencent.mobileqq.quibadge.QUIBadge"
    private val INT_TYPE = Int::class.javaPrimitiveType!!
    private val BOOLEAN_TYPE = Boolean::class.javaPrimitiveType!!

    private val quiBadgeHooked = AtomicBoolean(false)

    fun hookQuiBadgeExactCount(tag: String): Boolean {
        if (!quiBadgeHooked.compareAndSet(false, true)) return true

        val badgeClass = load(QUI_BADGE_CLASS) ?: return false
        val updateNum = badgeClass.declaredMethods
            .firstOrNull { method ->
                method.name == "updateNum" &&
                    method.parameterTypes.contentEquals(arrayOf(INT_TYPE)) &&
                    method.returnType == Void.TYPE
            }
            ?.apply { isAccessible = true }
            ?: return false.also { quiBadgeHooked.set(false) }

        val numField = badgeClass.findFieldByNameOrType("mNum", Int::class.java)
            ?.apply { isAccessible = true }
            ?: return false.also { quiBadgeHooked.set(false) }
        val textField = badgeClass.findFieldByNameOrType("mText", String::class.java)
            ?.apply { isAccessible = true }
            ?: return false.also { quiBadgeHooked.set(false) }

        updateNum.hookBefore { param ->
            val count = param.args.getOrNull(0) as? Int ?: return@hookBefore
            numField.setInt(param.thisObject, count)
            textField.set(param.thisObject, count.toString())
            param.result = null
        }
        Log.i("$tag: QUIBadge 精确数字 Hook 已启用")
        return true
    }

    fun hookUnreadTextViewCount(
        className: String,
        tag: String
    ): Boolean {
        val clazz = load(className) ?: return false
        val method = runCatching {
            clazz.getDeclaredMethod(
                "updateUnreadCount",
                INT_TYPE,
                BOOLEAN_TYPE
            )
                .apply { isAccessible = true }
        }.getOrNull() ?: return false

        method.hookAfter { param ->
            val count = param.args.getOrNull(0) as? Int ?: return@hookAfter
            val textView = param.thisObject.findFirstTextViewHolder()
                ?: return@hookAfter
            showExactCount(textView, count)
        }
        Log.i("$tag: $className 精确未读数 Hook 已启用")
        return true
    }

    fun showExactCount(textView: TextView, count: Int) {
        textView.text = count.toString()
        textView.maxWidth = Int.MAX_VALUE
        textView.layoutParams?.let { params ->
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            textView.layoutParams = params
        }
        textView.requestLayout()
    }

    private fun Any.findFirstTextViewHolder(): TextView? {
        return this::class.java.allFields()
            .firstNotNullOfOrNull { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(this)
                    when (value) {
                        is TextView -> value
                        is ViewGroup -> value.findFirstTextView()
                        is View -> (value as? ViewGroup)?.findFirstTextView()
                        else -> null
                    }
                }.getOrNull()
            }
    }

    private fun ViewGroup.findFirstTextView(): TextView? {
        for (index in 0 until childCount) {
            when (val child = getChildAt(index)) {
                is TextView -> return child
                is ViewGroup -> child.findFirstTextView()?.let { return it }
            }
        }
        return null
    }

    private fun Class<*>.findFieldByNameOrType(name: String, type: Class<*>): Field? {
        return allFields().firstOrNull { it.name == name }
            ?: allFields().firstOrNull { it.type == type }
    }

    private fun Class<*>.allFields(): List<Field> {
        val fields = mutableListOf<Field>()
        var clazz: Class<*>? = this
        while (clazz != null && clazz != Any::class.java) {
            fields += clazz.declaredFields
            clazz = clazz.superclass
        }
        return fields
    }

}
