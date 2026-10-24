package com.owo233.tcqt.features.chat

import org.luckypray.dexkit.query.FindClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DetailedMessageCountQueryTest {
    @Test
    fun timWidgetClassDoesNotNeedBoxedOrLegacyOverloadsToMatch() {
        val query = DetailedMessageCount.getQueryMap()[DetailedMessageCount.KEY_CUSTOM_WIDGET_UTIL]
            as FindClass
        val requiredMethods = assertNotNull(query.matcher?.methodsMatcher?.methodsMatcher)
        // Multiple required methods would make the signatures an AND condition.
        assertEquals(1, requiredMethods.size)
        val alternatives = assertNotNull(requiredMethods.single().anyOfMatchers).map { method ->
            assertNotNull(method.paramsMatcher?.paramsMatcher).map { parameter ->
                assertNotNull(parameter?.typeMatcher?.classNameMatcher?.value)
            }
        }

        // DexSQL: TIM 4.1.0.4050 com.tencent.widget.b declares these primitive
        // overloads, but none of their boxed Integer/Boolean counterparts.
        val timSignatures = listOf(
            listOf(
                "com.tencent.mobileqq.quibadge.QUIBadge",
                "int", "int", "int", "java.lang.String",
            ),
            listOf("android.widget.TextView", "int", "int", "int"),
            listOf(
                "android.widget.TextView", "int", "int", "int", "int", "java.lang.String",
            ),
            listOf(
                "android.widget.TextView", "int", "int", "int", "int", "java.lang.String", "boolean",
            ),
        )
        assertTrue(timSignatures.any { it in alternatives })
        assertTrue(timSignatures.first() in alternatives)

        // Keep the existing QQ TextView variants as alternatives as well.
        assertTrue(timSignatures[2] in alternatives)
        assertTrue(timSignatures[3] in alternatives)
    }
}
