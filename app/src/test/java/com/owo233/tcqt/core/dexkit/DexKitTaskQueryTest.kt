package com.owo233.tcqt.core.dexkit

import org.luckypray.dexkit.query.FindClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DexKitTaskQueryTest {
    @Test
    fun onlyKeysDeclaredForCurrentHostAreQueried() {
        val task = object : DexKitTask {
            override fun getQueryMap() = mapOf("current" to FindClass(), "legacy" to FindClass())
            override fun getCacheKeys() = setOf("current")
        }
        assertEquals(setOf("current"), task.activeQueries().keys)
    }

    @Test
    fun hostWithNoDexKitDependenciesDoesNotBuildOrExecuteLegacyQueries() {
        val task = object : DexKitTask {
            override fun getCacheKeys() = emptySet<String>()
            override fun getQueryMap(): Nothing = error("Legacy queries must not be built for this host")
        }
        assertTrue(task.activeQueries().isEmpty())
    }

    @Test
    fun defaultTasksStillUseTheirEntireDeclaredQueryMap() {
        val task = object : DexKitTask {
            override fun getQueryMap() = mapOf("one" to FindClass(), "two" to FindClass())
        }
        assertEquals(setOf("one", "two"), task.activeQueries().keys)
    }
}
