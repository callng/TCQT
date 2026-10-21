package com.owo233.tcqt.features.chat

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.callMethod
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import kotlin.math.min
import kotlin.math.roundToInt

internal object RepeatMessageSwipe {

    fun install(canRepeat: (MsgRecord) -> Boolean, repeat: (MsgRecord) -> Unit) {
        // if (!HookEnv.isQQ()) return

        val holder = loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB")
        val msgItem = loadOrThrow("com.tencent.mobileqq.aio.msg.AIOMsgItem")
        val helper = loadOrThrow(
            "com.tencent.mobileqq.aio.msglist.holder.component.leftswipearea.AIOContentLeftSwipeHelper"
        )
        val messageField = fieldOf(holder, msgItem)
        // 不能转为模块的 kotlin.Lazy 宿主和模块可能来自不同的 ClassLoader
        val helperField = holder.declaredFields.single { it.type.name == "kotlin.Lazy" }
            .apply { isAccessible = true }
        val containerField = fieldOf(helper, ViewGroup::class.java)
        val contentField = fieldOf(helper, View::class.java)
        val recordMethod = msgItem.getDeclaredMethod("getMsgRecord").apply { isAccessible = true }
        val sessions = WeakHashMap<Any, SwipeSession>()

        fun currentMessage(instance: Any): MsgRecord? = messageField.get(instance)?.let {
            recordMethod.invoke(it) as? MsgRecord
        }

        val intercept = holder.getDeclaredMethod("onInterceptTouchEvent", MotionEvent::class.java)
        intercept.hookBefore { param ->
            val event = param.args[0] as MotionEvent
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                sessions.remove(param.thisObject)?.reset()
            }
        }
        intercept.hookAfter { param ->
            val instance = param.thisObject
            val event = param.args[0] as MotionEvent
            val session = sessions[instance]
            if (event.actionMasked == MotionEvent.ACTION_DOWN && param.result == false) {
                val msg = currentMessage(instance) ?: return@hookAfter
                if (!canRepeat(msg)) return@hookAfter
                val nativeHelper = helperField.get(instance)?.callMethod("getValue")
                    ?: return@hookAfter
                val container = containerField.get(nativeHelper) as? ViewGroup ?: return@hookAfter
                val content = contentField.get(nativeHelper) as? View ?: return@hookAfter
                if (container.scrollX != 0 || !content.isShown) return@hookAfter
                if (event.x !in content.x..(content.x + content.width) ||
                    event.y !in content.y..(content.y + content.height)) return@hookAfter
                sessions[instance] = SwipeSession(container, content, msg).also { it.begin(event) }
            } else if (session != null) {
                if (session.captured) {
                    if (event.pointerCount != 1) session.cancelDrag()
                    else if (event.actionMasked == MotionEvent.ACTION_MOVE) session.move(event)
                    param.result = true
                } else if (event.pointerCount != 1 || param.result == true) {
                    session.reset()
                } else if (event.actionMasked == MotionEvent.ACTION_MOVE && session.move(event)) {
                    param.result = true
                } else if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
                    event.actionMasked == MotionEvent.ACTION_UP) {
                    session.reset()
                }
            }
        }
        holder.getDeclaredMethod("onTouchEvent", MotionEvent::class.java).hookBefore { param ->
            val session = sessions[param.thisObject] ?: return@hookBefore
            if (!session.captured) return@hookBefore
            val event = param.args[0] as MotionEvent
            // 一旦接管就消费到 UP/CANCEL 防止宿主把松手当作点击或回复
            param.result = true
            when {
                event.actionMasked == MotionEvent.ACTION_CANCEL ->
                    session.finish(false)
                event.pointerCount != 1 || event.actionMasked == MotionEvent.ACTION_POINTER_UP ->
                    session.cancelDrag()
                event.actionMasked == MotionEvent.ACTION_UP -> {
                    session.move(event)
                    val msg = currentMessage(param.thisObject)
                    val send = msg != null && session.matches(msg) && canRepeat(msg)
                    if (session.finish(send) && msg != null) repeat(msg)
                }
                event.actionMasked == MotionEvent.ACTION_MOVE -> session.move(event)
            }
        }
        // 公开的单 AIOMsgItem 参数方法是 holder 的绑定入口 复用前清理回弹和图标
        holder.declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) &&
                it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(msgItem))
        }.forEach { method ->
            method.hookBefore { param -> sessions.remove(param.thisObject)?.reset() }
        }
    }

    private fun fieldOf(owner: Class<*>, type: Class<*>): Field = owner.declaredFields.single {
        it.type == type && !Modifier.isStatic(it.modifiers)
    }.apply { isAccessible = true }

    private class SwipeSession(container: ViewGroup, content: View, msg: MsgRecord) :
        View.OnAttachStateChangeListener {
        // WeakHashMap 的 value 不能经 View / listener 间接强持有 holder
        private val containerRef = WeakReference(container)
        private val contentRef = WeakReference(content)
        private val msgId = msg.msgId
        private val peerUid = msg.peerUid
        private val chatType = msg.chatType
        private val density = container.resources.displayMetrics.density
        private val threshold = 64f * density
        private val gesture = SwipeRepeatGesture(
            ViewConfiguration.get(container.context).scaledTouchSlop.toFloat(),
            threshold,
            ViewConfiguration.getLongPressTimeout().toLong(),
        )
        private var overlayRef: WeakReference<ViewGroup>? = null
        private val badge = RepeatBadge(density, HookEnv.isNightMode())
        private var animator: ValueAnimator? = null
        private var wasReady = false
        private var ownsScroll = false
        var captured = false
            private set

        fun matches(msg: MsgRecord) = msgId == msg.msgId && peerUid == msg.peerUid && chatType == msg.chatType

        fun begin(event: MotionEvent) {
            containerRef.get()?.addOnAttachStateChangeListener(this)
            gesture.begin(event.x, event.y, event.eventTime)
        }

        fun move(event: MotionEvent): Boolean {
            if (!gesture.move(event.x, event.y, event.eventTime)) return false
            val container = containerRef.get() ?: return false
            if (!ownsScroll) {
                ownsScroll = true
                captured = true
                container.cancelLongPress()
                contentRef.get()?.cancelLongPress()
                (container.parent as? ViewGroup)?.let { parent ->
                    overlayRef = WeakReference(parent)
                    parent.overlay.add(badge)
                }
            }
            container.parent?.requestDisallowInterceptTouchEvent(true)
            val distance = gesture.distance
            val displayed = if (distance <= threshold) distance else
                min(threshold + (distance - threshold) * 0.35f, 104f * density)
            render(displayed, gesture.ready)
            if (gesture.ready && !wasReady) {
                container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            wasReady = gesture.ready
            return true
        }

        fun finish(allowRepeat: Boolean): Boolean {
            captured = false
            val send = gesture.release() && allowRepeat
            containerRef.get()?.parent?.requestDisallowInterceptTouchEvent(false)
            val start = -(containerRef.get()?.scrollX ?: 0).toFloat()
            animator = ValueAnimator.ofFloat(start, 0f).apply {
                duration = 160L
                interpolator = DecelerateInterpolator()
                addUpdateListener { render(it.animatedValue as Float, false) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = reset()
                })
                start()
            }
            return send
        }

        fun cancelDrag() {
            gesture.cancel()
            render(0f, false)
        }

        private fun render(distance: Float, ready: Boolean) {
            val container = containerRef.get() ?: return
            val parent = overlayRef?.get() ?: return
            if (container.parent !== parent) {
                reset()
                return
            }
            container.scrollTo(-distance.roundToInt(), 0)
            val content = contentRef.get() ?: return
            val rect = Rect(0, 0, content.width, content.height)
            parent.offsetDescendantRectToMyCoords(content, rect)
            val root = Rect(0, 0, container.width, container.height)
            parent.offsetDescendantRectToMyCoords(container, root)
            val radius = 20f * density
            // offsetDescendantRectToMyCoords 会减去容器自身 scrollX；overlay 的锚点需抵消它
            val cx = root.left + container.scrollX + min(distance / 2f, 30f * density)
            val cy = rect.exactCenterY()
            badge.setBounds((cx - radius).roundToInt(), (cy - radius).roundToInt(),
                (cx + radius).roundToInt(), (cy + radius).roundToInt())
            badge.progress = (distance / threshold).coerceIn(0f, 1f)
            badge.ready = ready
            badge.invalidateSelf()
        }

        fun reset() {
            captured = false
            gesture.cancel()
            animator?.let {
                animator = null
                it.removeAllListeners()
                it.cancel()
            }
            overlayRef?.get()?.overlay?.remove(badge)
            overlayRef = null
            containerRef.get()?.let {
                if (ownsScroll) {
                    it.scrollTo(0, 0)
                    it.parent?.requestDisallowInterceptTouchEvent(false)
                }
                it.removeOnAttachStateChangeListener(this)
            }
            ownsScroll = false
        }

        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = reset()
    }

    private class RepeatBadge(density: Float, dark: Boolean) : Drawable() {
        private val darkMode = dark
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 16f * density
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        var progress = 0f
        var ready = false

        override fun draw(canvas: Canvas) {
            val alpha = (progress * 255).roundToInt()
            paint.color = if (darkMode) Color.rgb(59, 80, 98) else Color.rgb(211, 226, 237)
            paint.alpha = alpha
            val radius = bounds.width() / 2f * (0.8f + progress * 0.2f)
            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
            paint.color = if (darkMode) Color.rgb(192, 218, 240) else Color.rgb(82, 116, 146)
            paint.alpha = if (ready) alpha else (alpha * 0.7f).roundToInt()
            val baseline = bounds.exactCenterY() - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText("+1", bounds.exactCenterX(), baseline, paint)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Deprecated in Android",
            ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
        )
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
