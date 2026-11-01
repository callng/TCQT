package com.owo233.tcqt.core.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigRestorePlanTest {
    private val schema = mapOf("enabled" to "BOOLEAN", "mask" to "INT_MULTI")
    private fun backup(items: String, version: Int = 1) =
        """{"marker":"TCQT_CONFIG_BACKUP","version":$version,"settings":[$items]}"""
    private val enabled = """{"key":"enabled","type":"BOOLEAN","value":"true"}"""

    @Test fun rejectsMalformedBackupsBeforeWriting() {
        val bad = listOf("{}", "[]", "{", backup(enabled, 0),
            backup(enabled.replace("true", "invalid")),
            backup(enabled.replace("BOOLEAN", "STRING")),
            backup("$enabled,$enabled"), backup(enabled.replace("BOOLEAN", "FUTURE")))
        bad.forEach { assertFailsWith<IllegalArgumentException> { ConfigRestorePlan.decode(it, schema) } }
        assertFailsWith<ConfigRestorePlan.VersionMismatch> { ConfigRestorePlan.decode(backup(enabled, 2), schema) }
    }

    @Test fun unknownOnlyBackupCannotEraseSettings() {
        assertFailsWith<IllegalArgumentException> {
            ConfigRestorePlan.decode(backup(enabled.replace("enabled", "future")), schema)
        }
        assertEquals(mapOf("enabled" to true), ConfigRestorePlan.decode(
            backup("$enabled,${enabled.replace("enabled", "future")}"), schema).values)
    }

    @Test fun validatesIntegerCompatibilityAndOverflow() {
        val item = """{"key":"mask","type":"INT","value":"7"}"""
        assertEquals(mapOf("mask" to 7), ConfigRestorePlan.decode(backup(item), schema).values)
        assertFailsWith<IllegalArgumentException> {
            ConfigRestorePlan.decode(backup(item.replace("7", "2147483648")), schema)
        }
    }

    @Test fun replacementPreservesLocalMetadataAndFailurePreservesAllValues() {
        val directory = Files.createTempDirectory("tcqt-restore").toFile()
        var data: Map<String, Any> = mapOf("enabled" to false, "mask" to 3, "backup_directory_uri" to "local")
        var fail = true
        val store = SettingsStore(directory, "test") { object : SettingsBackend {
            override fun read() = data
            override fun replace(values: Map<String, Any>) { check(!fail); data = values.toMap() }
            override fun close() = Unit
        } }
        try {
            val plan = ConfigRestorePlan.decode(backup(enabled), schema)
            assertFailsWith<IllegalStateException> { plan.applyTo(store) }
            assertEquals(mapOf("enabled" to false, "mask" to 3, "backup_directory_uri" to "local"), store.all())
            fail = false
            plan.applyTo(store)
            assertEquals(mapOf("enabled" to true, "backup_directory_uri" to "local"), store.all())
            ConfigRestorePlan.decode(backup(""), schema).applyTo(store)
            assertEquals(mapOf("backup_directory_uri" to "local"), store.all())
        } finally { directory.deleteRecursively() }
    }

    @Test fun limitsInputBeforeDecoding() {
        assertFailsWith<IllegalArgumentException> {
            ConfigRestorePlan.read(ByteArray(ConfigRestorePlan.MAX_BYTES + 1).inputStream())
        }
    }
}
