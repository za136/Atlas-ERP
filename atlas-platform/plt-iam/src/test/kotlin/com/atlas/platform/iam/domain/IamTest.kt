package com.atlas.platform.iam.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class IamTest {

    @Test
    fun `agent principal should enforce amount limit and permission checks (P6, PLT-IAM-005)`() {
        val agent = AtlasPrincipal(
            id = UUID.randomUUID(),
            tenantId = UUID.randomUUID(),
            name = "AutonomousInvoiceMatcher",
            type = PrincipalType.AGENT,
            roles = setOf("INVOICE_PROCESSOR"),
            permissions = setOf("prc:invoice:match", "fin:journal:post"),
            amountLimit = BigDecimal("5000.00")
        )

        assertEquals(PrincipalType.AGENT, agent.type)
        assertTrue(agent.hasPermission("prc:invoice:match"))
        assertFalse(agent.hasPermission("fin:payment:approve"))

        assertTrue(agent.canPostAmount(BigDecimal("4999.99")))
        assertTrue(agent.canPostAmount(BigDecimal("5000.00")))
        assertFalse(agent.canPostAmount(BigDecimal("5000.01")), "Agent should not exceed spending limit")
    }

    @Test
    fun `sod validator should detect conflicting permissions (PLT-IAM-003)`() {
        val conflictingPermissions = setOf("prc:invoice:create", "fin:payment:approve", "prc:read")
        val conflicts = SodValidator.checkConflicts(conflictingPermissions)

        assertEquals(1, conflicts.size)
        assertEquals("SOD-01", conflicts[0].code)
        assertTrue(conflicts[0].description.contains("Cannot both create supplier invoice and approve supplier payment"))
    }
}
