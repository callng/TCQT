package com.owo233.tcqt.host.service

import com.owo233.tcqt.host.service.api.parseGroupNumbers
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupNumberParserTest {

    @Test
    fun `half width comma`() {
        assertEquals(listOf("123456", "234567"), parseGroupNumbers("123456,234567"))
    }

    @Test
    fun `full width comma`() {
        assertEquals(listOf("123456", "234567"), parseGroupNumbers("123456，234567"))
    }

    @Test
    fun `mixed separators keep every group`() {
        assertEquals(
            listOf("123456", "234567", "345678", "456789"),
            parseGroupNumbers("123456,234567，345678、456789"),
        )
    }

    @Test
    fun `whitespace and newline`() {
        assertEquals(
            listOf("123456", "234567", "345678"),
            parseGroupNumbers("123456 234567\n345678\t"),
        )
    }

    @Test
    fun `full width space`() {
        assertEquals(listOf("123456", "234567"), parseGroupNumbers("123456\u3000234567"))
    }

    @Test
    fun `comment after group number`() {
        assertEquals(listOf("123456"), parseGroupNumbers("123456#测试群"))
    }

    @Test
    fun `duplicates collapse and invalid lengths drop`() {
        assertEquals(
            listOf("123456", "12345678"),
            parseGroupNumbers("123456,123456,1234,1234567890123,12345678"),
        )
    }

    @Test
    fun `blank input`() {
        assertEquals(emptyList(), parseGroupNumbers(""))
    }
}
