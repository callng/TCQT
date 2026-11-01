package com.owo233.tcqt.core.action

import com.owo233.tcqt.generated.GeneratedActionList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionDescriptorTest {
    private object NeverInitialize : ActionSpec {
        init { error("Metadata initialized a feature") }
        override val key = "trap"
        override val name = "trap"
        override fun onRun(app: android.app.Application, process: ActionProcess) = Unit
    }

    @Test fun `process filtering does not initialize singletons`() {
        val descriptor = ActionDescriptor(NeverInitialize::class.java, setOf(ActionProcess.MSF), ActionPriority.EARLY)
        assertTrue(descriptor.matches(ActionProcess.MSF))
        assertFalse(descriptor.matches(ActionProcess.MAIN))
    }

    @Test fun `all and other selection preserve semantics`() {
        val cls = NeverInitialize::class.java
        assertTrue(ActionProcess.entries.all { ActionDescriptor(cls, setOf(ActionProcess.ALL), ActionPriority.DEFERRED).matches(it) })
        val other = ActionDescriptor(cls, setOf(ActionProcess.OTHER), ActionPriority.DEFERRED)
        assertTrue(other.matches(ActionProcess.OTHER))
        assertFalse(other.matches(ActionProcess.MSF))
    }

    @Test fun `generated metadata preserves representative priorities and processes`() {
        val descriptors = GeneratedActionList.DESCRIPTORS
        assertEquals(GeneratedActionList.ACTIONS.toSet(), descriptors.map { it.actionClass }.toSet())
        val byName = descriptors.associateBy { it.actionClass.simpleName }
        assertEquals(ActionPriority.CRITICAL, byName.getValue("FileRecvRedirect").priority)
        assertEquals(ActionPriority.BACKGROUND, byName.getValue("MenuBuilder").priority)
        assertEquals(setOf(ActionProcess.MAIN, ActionProcess.OPENSDK), byName.getValue("SkipQRLoginWait").processes)
        assertEquals(setOf(ActionProcess.TOOL), byName.getValue("WebJsBridge").processes)
    }
}
