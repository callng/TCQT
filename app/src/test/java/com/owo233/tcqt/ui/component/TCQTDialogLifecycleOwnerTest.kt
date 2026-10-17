package com.owo233.tcqt.ui.component

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TCQTDialogLifecycleOwnerTest {
    @BeforeTest
    fun useTestThreadAsMainThread() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })
    }

    @AfterTest
    fun resetTaskExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun constructorRestoresSavedStateWithAnInitializedLifecycle() {
        val owner = TCQTDialogLifecycleOwner()

        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)
        assertTrue(owner.savedStateRegistry.isRestored)
        assertNull(owner.savedStateRegistry.consumeRestoredStateForKey("missing"))
    }

    @Test
    fun lifecycleReachesResumedThenDestroyedAndIgnoresLaterEvents() {
        val owner = TCQTDialogLifecycleOwner()
        val received = mutableListOf<Lifecycle.Event>()
        owner.lifecycle.addObserver(LifecycleEventObserver { _, event -> received += event })

        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertEquals(listOf(
            Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME,
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY,
        ), received)
    }
}
