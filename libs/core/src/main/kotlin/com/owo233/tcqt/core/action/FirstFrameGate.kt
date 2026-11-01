package com.owo233.tcqt.core.action

import java.util.concurrent.atomic.AtomicReference

/** One completion shared by draw, destruction/retry and timeout paths. */
class FirstFrameGate {
    enum class Reason { FRAME_COMMIT, DRAW_TRAVERSAL, TIMEOUT, UNAVAILABLE }
    data class Completion(val reason: Reason, val observedNanos: Long)
    private val completion = AtomicReference<Completion?>()
    val result: Completion? get() = completion.get()
    fun complete(reason: Reason, observedNanos: Long): Boolean =
        completion.compareAndSet(null, Completion(reason, observedNanos))
}
