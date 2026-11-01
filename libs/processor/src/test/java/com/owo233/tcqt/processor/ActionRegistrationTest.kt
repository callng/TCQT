package com.owo233.tcqt.processor

import kotlin.test.*

class ActionRegistrationTest {

    private fun constraint(kind: String = "OBJECT", hidden: Boolean = false, inner: Boolean = false,
                           generic: Boolean = false, abstract: Boolean = false, companion: Boolean = false,
                           noArg: Boolean = false) = constructionError(kind, hidden, inner, generic, abstract, companion, noArg)
    @Test fun objectAndNoArgClass() { assertNull(constraint()); assertNull(constraint("CLASS", noArg = true)) }
    @Test fun constructorRequired() { assertNotNull(constraint("CLASS")); assertNotNull(constraint("INTERFACE")) }
    @Test fun rejectsHiddenAndInner() { assertNotNull(constraint(hidden = true)); assertNotNull(constraint(inner = true)) }
    @Test fun rejectsGenericAbstractAndCompanion() {
        assertNotNull(constraint(generic = true)); assertNotNull(constraint(abstract = true)); assertNotNull(constraint(companion = true))
    }
    @Test fun stableOrderAndRepeatedRounds() {
        val table = RegistrationTable()
        val z = ActionRegistration("fixture.Z", listOf("MAIN"), "EARLY")
        val a = z.copy(name = "fixture.A")
        assertTrue(table.add("z.kt", z)); assertTrue(table.add("a.kt", a)); assertTrue(table.add("z.kt", z))
        assertEquals(listOf(a, z), table.values())
    }
    @Test fun rejectsConflictingRegistration() {
        val table = RegistrationTable()
        val a = ActionRegistration("fixture.A", listOf("MAIN"), "EARLY")
        assertTrue(table.add("a.kt", a)); assertFalse(table.add("b.kt", a)); assertFalse(table.add("a.kt", a.copy(priority = "BACKGROUND")))
        assertEquals(listOf(a), table.values())
    }
    @Test fun emptyRegistryIsValid() { assertTrue(RegistrationTable().values().isEmpty()) }
}
