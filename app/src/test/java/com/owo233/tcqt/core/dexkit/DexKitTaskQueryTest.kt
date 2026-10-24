package com.owo233.tcqt.core.dexkit

import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import org.luckypray.dexkit.query.FindClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DexKitTaskQueryTest {
    @Test
    fun unavailableFeaturesAreSkippedBeforeCollectingTheirKeysOrQueries() {
        val unavailable = object : Feature(
            key = "unavailable",
            name = "不支持当前宿主的功能",
            requires = Requires(extraCondition = { false }),
        ), DexKitTask {
            override fun install() = Unit
            override fun getCacheKeys(): Nothing = error("Unavailable keys must not be collected")
            override fun getQueryMap(): Nothing = error("Unavailable queries must not be built")
        }

        assertTrue(selectDexKitTasks(listOf(unavailable)).isEmpty())
    }

    @Test
    fun sharedKeysKeepOnlyAvailableOwnersAndIgnoreTheEnableSwitch() {
        val unavailable = object : Feature(
            key = "unavailable",
            name = "不支持的功能",
            requires = Requires(extraCondition = { false }),
        ), DexKitTask {
            override fun install() = Unit
            override fun getCacheKeys() = setOf("shared", "unavailableOnly")
        }
        val available = object : Feature(
            key = "available",
            name = "支持但未启用的功能",
            defaultEnabled = false,
        ), DexKitTask {
            override fun install() = Unit
            override fun canRun(): Nothing = error("Scan eligibility must not check the switch")
            override fun getCacheKeys() = setOf("shared")
        }

        val selected = selectDexKitTasks(listOf(unavailable, available))
        assertEquals(listOf(available), selected)
        assertEquals(setOf("shared"), selected.flatMap { it.getCacheKeys() }.toSet())
    }

    @Test
    fun featureRequirementsAreReevaluatedOnEachSelection() {
        var allowed = false
        val task = object : Feature(
            key = "conditional",
            name = "条件功能",
            requires = Requires(extraCondition = { allowed }),
        ), DexKitTask {
            override fun install() = Unit
            override fun getCacheKeys() = setOf("conditional")
        }

        assertTrue(selectDexKitTasks(listOf(task)).isEmpty())
        allowed = true
        assertEquals(listOf(task), selectDexKitTasks(listOf(task)))
        allowed = false
        assertTrue(selectDexKitTasks(listOf(task)).isEmpty())
    }

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
