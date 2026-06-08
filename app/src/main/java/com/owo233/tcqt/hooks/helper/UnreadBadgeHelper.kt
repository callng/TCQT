package com.owo233.tcqt.hooks.helper

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.hooks.base.load
import com.owo233.tcqt.utils.hook.hookAfter
import com.owo233.tcqt.utils.hook.hookBefore
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

object UnreadBadgeHelper {

    private const val QUI_BADGE_CLASS = "com.tencent.mobileqq.quibadge.QUIBadge"
    private const val RED_TOUCH_CLASS = "com.tencent.mobileqq.tianshu.ui.RedTouch"
    private const val TIANSHU_RED_TOUCH_CLASS = "com.tencent.mobileqq.tianshu.ui.TianshuRedTouch"
    private const val TAB_FRAME_CONTROLLER_IMPL_CLASS =
        "com.tencent.mobileqq.activity.home.impl.TabFrameControllerImpl"
    private const val FRAME_CONTROLLER_INJECT_IMPL_CLASS =
        "com.tencent.mobileqq.activity.framebusiness.controllerinject.FrameControllerInjectImpl"
    private val INT_TYPE = Int::class.javaPrimitiveType!!
    private val BOOLEAN_TYPE = Boolean::class.javaPrimitiveType!!

    private val quiBadgeHooked = AtomicBoolean(false)
    private val redTouchHooked = AtomicBoolean(false)
    private val tabRedBadgeHooked = AtomicBoolean(false)
    private val tianshuRedTouchHooked = AtomicBoolean(false)
    private val frameControllerBadgeHooked = AtomicBoolean(false)

    @Suppress("UNUSED_PARAMETER")
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
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun hookRedTouchExactCount(tag: String): Boolean {
        val tabRedBadgeHooked = hookTabRedBadgeExactCount()
        val oldRedTouchHooked = hookLegacyRedTouchExactCount()
        val newRedTouchHooked = hookTianshuRedTouchExactCount()
        return tabRedBadgeHooked || oldRedTouchHooked || newRedTouchHooked
    }

    @Suppress("UNUSED_PARAMETER")
    fun hookFrameControllerBadgeExactCount(tag: String): Boolean {
        if (!frameControllerBadgeHooked.compareAndSet(false, true)) return true

        val controllerClass = load(FRAME_CONTROLLER_INJECT_IMPL_CLASS) ?: return false.also {
            frameControllerBadgeHooked.set(false)
        }
        val quiBadgeClass = load(QUI_BADGE_CLASS) ?: return false.also {
            frameControllerBadgeHooked.set(false)
        }

        val updateBadge = controllerClass.declaredMethods.firstOrNull { method ->
            method.name == "b" &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(
                    arrayOf(
                        quiBadgeClass,
                        INT_TYPE,
                        String::class.java,
                        INT_TYPE,
                        INT_TYPE
                    )
                )
        }?.apply { isAccessible = true } ?: return false.also {
            frameControllerBadgeHooked.set(false)
        }

        updateBadge.hookBefore { param ->
            param.args[4] = Int.MAX_VALUE
        }
        updateBadge.hookAfter { param ->
            val badge = param.args.getOrNull(0) as? View ?: return@hookAfter
            val count = param.args.getOrNull(1) as? Int ?: return@hookAfter
            val style = param.args.getOrNull(3) as? Int ?: return@hookAfter
            if (count <= 99 || style !in FRAME_CONTROLLER_NUMBER_BADGE_STYLES) return@hookAfter
            applyQuiBadgeExactCount(badge, count)
        }
        return true
    }

    private fun hookTabRedBadgeExactCount(): Boolean {
        if (!tabRedBadgeHooked.compareAndSet(false, true)) return true

        val controllerClass = load(TAB_FRAME_CONTROLLER_IMPL_CLASS) ?: return false.also {
            tabRedBadgeHooked.set(false)
        }
        val quiBadgeClass = load(QUI_BADGE_CLASS) ?: return false.also {
            tabRedBadgeHooked.set(false)
        }
        val redTouchClass = load(TIANSHU_RED_TOUCH_CLASS) ?: return false.also {
            tabRedBadgeHooked.set(false)
        }
        val redTypeInfoClass = load("$TIANSHU_PB_PACKAGE.BusinessInfoCheckUpdate\$RedTypeInfo")
            ?: return false.also { tabRedBadgeHooked.set(false) }

        val updateTabInfo = controllerClass.declaredMethods.firstOrNull { method ->
            method.name == "updateTabInfo" &&
                method.parameterTypes.contentEquals(arrayOf(redTouchClass, redTypeInfoClass)) &&
                method.returnType == Void.TYPE
        }?.apply { isAccessible = true } ?: return false.also {
            tabRedBadgeHooked.set(false)
        }

        updateTabInfo.hookAfter { param ->
            val tianshuRedTouch = param.args.getOrNull(0) as? View ?: return@hookAfter
            val count = param.args.getOrNull(1)?.redContent() ?: return@hookAfter
            val badge = (tianshuRedTouch as? ViewGroup)
                ?.findFirstChildByClass(quiBadgeClass)
                ?: return@hookAfter
            applyQuiBadgeExactCount(badge, count.toIntOrNull() ?: return@hookAfter)
        }
        return true
    }

    private fun hookLegacyRedTouchExactCount(): Boolean {
        if (!redTouchHooked.compareAndSet(false, true)) return true

        val redTouchClass = load(RED_TOUCH_CLASS) ?: return false.also {
            redTouchHooked.set(false)
        }
        val getTextRedPoint = runCatching {
            redTouchClass.getDeclaredMethod(
                "getTextRedPoint",
                String::class.java,
                INT_TYPE,
                INT_TYPE,
                INT_TYPE
            )
                .apply { isAccessible = true }
        }.getOrNull() ?: return false.also { redTouchHooked.set(false) }

        getTextRedPoint.hookAfter { param ->
            val count = (param.args.getOrNull(0) as? String)
                ?.toIntOrNull()
                ?.takeIf { it > 99 }
                ?: return@hookAfter
            when (val result = param.result) {
                is TextView -> showExactCount(result, count)
                is ViewGroup -> result.findFirstTextView()?.let { showExactCount(it, count) }
            }
        }
        return true
    }

    private fun hookTianshuRedTouchExactCount(): Boolean {
        if (!tianshuRedTouchHooked.compareAndSet(false, true)) return true

        val redTouchClass = load(TIANSHU_RED_TOUCH_CLASS) ?: return false.also {
            tianshuRedTouchHooked.set(false)
        }

        val methods = redTouchClass.declaredMethods.filter { method ->
            (method.returnType == redTouchClass && method.parameterTypes.size <= 2) ||
                method.returnType == Void.TYPE && method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].name.startsWith(TIANSHU_PB_PACKAGE)
        }
        if (methods.isEmpty()) {
            tianshuRedTouchHooked.set(false)
            return false
        }

        methods.forEach { method ->
            method.isAccessible = true
            method.hookAfter { param ->
                (param.thisObject as? ViewGroup)?.applyExactRedContent()
            }
        }
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

    private fun ViewGroup.applyExactRedContent() {
        val content = exactRedContent() ?: return
        replaceCollapsedBadgeText(content)
        post { replaceCollapsedBadgeText(content) }
    }

    private fun ViewGroup.replaceCollapsedBadgeText(content: String) {
        findAllTextViews().forEach { textView ->
            val text = textView.text?.toString() ?: return@forEach
            if (text == "99+" || COLLAPSED_BADGE_TEXT.matches(text)) {
                showExactCount(textView, content.toIntOrNull() ?: return@forEach)
            }
        }
    }

    private fun applyQuiBadgeExactCount(badge: View, count: Int) {
        val badgeClass = badge::class.java
        badgeClass.findFieldByNameOrType("mNum", Int::class.java)
            ?.apply { isAccessible = true }
            ?.setInt(badge, count)
        badgeClass.findFieldByNameOrType("mText", String::class.java)
            ?.apply { isAccessible = true }
            ?.set(badge, count.toString())
        badge.requestLayout()
        badge.invalidate()
    }

    private fun Any.exactRedContent(): String? {
        val redTypeInfo = this::class.java.allFields()
            .firstOrNull { it.type.name == "$TIANSHU_PB_PACKAGE.BusinessInfoCheckUpdate\$RedTypeInfo" }
            ?.getValue(this)
            ?.redContent()
        if (redTypeInfo != null) return redTypeInfo

        return this::class.java.allFields()
            .asSequence()
            .filter { it.type.name == "$TIANSHU_PB_PACKAGE.BusinessInfoCheckUpdate\$AppInfo" }
            .mapNotNull { field -> field.getValue(this)?.appInfoRedContent() }
            .firstOrNull()
    }

    private fun Any.appInfoRedContent(): String? {
        val redDisplayInfo = getFieldValue("red_display_info")?.callGet() ?: return null
        val redTypeInfos = redDisplayInfo.getFieldValue("red_type_info")
            ?.callGet() as? Iterable<*>
            ?: return null
        return redTypeInfos
            .mapNotNull { it?.redContent() }
            .firstOrNull()
    }

    private fun Any.redContent(): String? {
        val content = getFieldValue("red_content")?.callGet()?.toString()
            ?.takeIf { it.toIntOrNull()?.let { count -> count > 99 } == true }
            ?: return null
        val type = getFieldValue("red_type")?.callGet() as? Int ?: return content
        return if (type in NUMBER_RED_TYPES) content else null
    }

    private fun Any.findFirstTextViewHolder(): TextView? {
        return this::class.java.allFields()
            .firstNotNullOfOrNull { field ->
                runCatching {
                    field.isAccessible = true
                    val value = field.get(this)
                    when (value) {
                        null -> null
                        is TextView -> value
                        is ViewGroup -> value.findFirstTextView()
                        is View -> (value as? ViewGroup)?.findFirstTextView()
                        else -> null
                    }
                }.getOrNull()
            }
    }

    private fun ViewGroup.findAllTextViews(): List<TextView> {
        val views = mutableListOf<TextView>()
        for (index in 0 until childCount) {
            when (val child = getChildAt(index)) {
                is TextView -> views += child
                is ViewGroup -> views += child.findAllTextViews()
            }
        }
        return views
    }

    private fun ViewGroup.findFirstChildByClass(clazz: Class<*>): View? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (clazz.isInstance(child)) return child
            if (child is ViewGroup) {
                child.findFirstChildByClass(clazz)?.let { return it }
            }
        }
        return null
    }

    private fun Any.getFieldValue(name: String): Any? {
        return this::class.java.allFields()
            .firstOrNull { it.name == name }
            ?.getValue(this)
    }

    private fun Field.getValue(instance: Any): Any? {
        return runCatching {
            isAccessible = true
            get(instance)
        }.getOrNull()
    }

    private fun Any.callGet(): Any? {
        return runCatching {
            val method = this::class.java.noArgMethod("get") ?: return null
            method.invoke(this)
        }.getOrNull()
    }

    private fun Class<*>.noArgMethod(name: String): Method? {
        return methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            ?: declaredMethods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
                ?.apply { isAccessible = true }
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

    private const val TIANSHU_PB_PACKAGE = "com.tencent.mobileqq.tianshu.pb"
    private val COLLAPSED_BADGE_TEXT = Regex("\\d+\\+")
    private val NUMBER_RED_TYPES = setOf(4, 5, 8, 98, -100)
    private val FRAME_CONTROLLER_NUMBER_BADGE_STYLES = setOf(3, 4, 7, 9)

}
