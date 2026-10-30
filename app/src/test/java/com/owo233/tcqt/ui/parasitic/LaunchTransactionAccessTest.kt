package com.owo233.tcqt.ui.parasitic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class LaunchTransactionAccessTest {

    class LegacyTransaction(private val callbacks: List<*>?, private val token: Any? = null) {
        fun getCallbacks(): List<*>? = callbacks
        fun getActivityToken(): Any? = token
    }

    class ModernTransaction(private val items: List<*>) {
        fun getTransactionItems(): List<*> = items
        fun getCallbacks(): Nothing = error("Modern transaction must use its items")
    }

    class TransitionalTransaction {
        fun getTransactionItems(): List<*>? = null
        fun getCallbacks(): List<*> = listOf("legacy")
    }

    class ModernItem(private val token: Any?) {
        fun getActivityToken(): Any? = token
    }

    class BrokenTransaction {
        fun getTransactionItems(): Nothing = throw IllegalStateException("broken transaction")
    }

    @Test
    fun legacyCallbacksAreUsedWhenModernMethodDoesNotExist() {
        val items = listOf(Any(), Any())
        assertSame(items, LaunchTransactionAccess.items(LegacyTransaction(items)))
    }

    @Test
    fun modernItemsAreUsedWithoutConsultingDeprecatedCallbacks() {
        val items = listOf(Any(), Any())
        assertSame(items, LaunchTransactionAccess.items(ModernTransaction(items)))
    }

    @Test
    fun emptyModernTransactionDoesNotConsultDeprecatedCallbacks() {
        assertEquals(emptyList<Any>(), LaunchTransactionAccess.items(ModernTransaction(emptyList<Any>())))
    }

    @Test
    fun nullModernListFallsBackToCallbacks() {
        assertEquals(listOf("legacy"), LaunchTransactionAccess.items(TransitionalTransaction()))
    }

    @Test
    fun missingLegacyCallbacksBecomeAnEmptyList() {
        assertEquals(emptyList<Any>(), LaunchTransactionAccess.items(LegacyTransaction(null)))
    }

    @Test
    fun eachItemUsesItsOwnTokenInMultiActivityTransactions() {
        val first = Any()
        val second = Any()
        val transaction = LegacyTransaction(null, first)
        assertSame(first, LaunchTransactionAccess.token(ModernItem(first), transaction))
        assertSame(second, LaunchTransactionAccess.token(ModernItem(second), transaction))
    }

    @Test
    fun legacyItemFallsBackToTransactionToken() {
        val token = Any()
        assertSame(token, LaunchTransactionAccess.token(Any(), LegacyTransaction(null, token)))
        assertSame(token, LaunchTransactionAccess.token(ModernItem(null), LegacyTransaction(null, token)))
        assertNull(LaunchTransactionAccess.token(Any(), LegacyTransaction(null)))
    }

    @Test
    fun invocationFailuresAreNotSilentlyTreatedAsOldAndroid() {
        assertFailsWith<IllegalStateException> { LaunchTransactionAccess.items(BrokenTransaction()) }
    }
}
