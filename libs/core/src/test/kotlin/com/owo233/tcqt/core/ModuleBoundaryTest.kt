package com.owo233.tcqt.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ModuleBoundaryTest {
    @Test fun sourceHasNoPlatformHostOrApplicationDependencies() {
        val files = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty())
        val forbidden = Regex("(?:android\\.|androidx\\.|de\\.robv\\.|io\\.github\\.libxposed\\.|com\\.tencent\\.|com\\.owo233\\.tcqt\\.(?:api|host|loader|features|ui)\\.)")
        val violations = files.filter { forbidden.containsMatchIn(it.readText()) }.map { it.name }
        assertTrue(violations.isEmpty(), "JVM core depends on app or host: $violations")
    }
}
