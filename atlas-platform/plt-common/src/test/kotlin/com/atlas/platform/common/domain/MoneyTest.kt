package com.atlas.platform.common.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `should correctly add same currency`() {
        val m1 = Money.of("100.50", "USD")
        val m2 = Money.of("49.50", "USD")
        val result = m1 + m2
        assertEquals(Money.of("150.00", "USD"), result)
    }

    @Test
    fun `should forbid addition of differing currencies`() {
        val m1 = Money.of("100.00", "USD")
        val m2 = Money.of("100.00", "EUR")
        assertThrows<IllegalArgumentException> {
            val ignored = m1 + m2
        }
    }

    @Test
    fun `should multiply with BigDecimal correctly`() {
        val m = Money.of("100.00", "USD")
        val result = m * BigDecimal("1.20")
        assertEquals(Money.of("120.00", "USD"), result)
    }

    @Test
    fun `should enforce invariant check`() {
        assertDoesNotThrow {
            checkInvariant("FIN-INV-1", true) { "Debits equal credits" }
        }
        val ex = assertThrows<InvariantViolationException> {
            checkInvariant("FIN-INV-1", false) { "Debits (100) do not equal credits (90)" }
        }
        assertEquals("FIN-INV-1", ex.invariantId)
        assertTrue(ex.message!!.contains("Debits (100) do not equal credits (90)"))
    }
}
