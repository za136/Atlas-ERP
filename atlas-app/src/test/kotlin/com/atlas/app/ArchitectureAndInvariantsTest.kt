package com.atlas.app

import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.accounting.api.JournalLineDraft
import com.atlas.finance.accounting.api.PostingSide
import com.atlas.finance.accounting.domain.UniversalJournalEntryRepository
import com.atlas.platform.common.domain.InvariantViolationException
import com.atlas.platform.common.domain.Money
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class ArchitectureAndInvariantsTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var accountingGateway: AccountingGateway

    @Autowired
    private lateinit var journalRepository: UniversalJournalEntryRepository

    @Autowired
    private lateinit var principalProvider: com.atlas.platform.iam.domain.CurrentPrincipalProvider

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        principalProvider.clear()
    }

    @Test
    fun `verify spring application context loads and module beans are cleanly wired`() {
        assertNotNull(applicationContext)
        assertNotNull(accountingGateway)
        assertNotNull(journalRepository)
    }

    @Test
    fun `should successfully post balanced journal entry and compute SHA-256 hash (FIN-INV-1 and FIN-INV-4)`() {
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        val docId = UUID.randomUUID()

        val request = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = companyId,
            ledgerCode = "0L",
            documentType = "GL_MANUAL",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = "TEST-REF-001",
            headerText = "Balanced Entry Test",
            sourceModule = "FIN_GL",
            sourceDocumentId = docId,
            lines = listOf(
                JournalLineDraft(
                    lineNumber = 1,
                    accountCode = "110000",
                    side = PostingSide.DEBIT,
                    amount = Money.of("500.00", "USD"),
                    lineText = "Cash received"
                ),
                JournalLineDraft(
                    lineNumber = 2,
                    accountCode = "410000",
                    side = PostingSide.CREDIT,
                    amount = Money.of("500.00", "USD"),
                    lineText = "Revenue recorded"
                )
            )
        )

        val ref = accountingGateway.post(request)
        assertNotNull(ref)
        assertNotNull(ref.documentId)
        assertTrue(ref.contentHash.length == 64, "SHA-256 hash must be 64 hex characters")

        val stored = journalRepository.findById(ref.documentId).orElse(null)
        assertNotNull(stored)
        assertEquals(2, stored.lines.size)
        assertEquals("TEST-REF-001", stored.reference)
        assertEquals(ref.contentHash, stored.contentHash)
    }

    @Test
    fun `should strictly reject unbalanced journal entry and throw InvariantViolationException (FIN-INV-1)`() {
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()

        val unbalancedRequest = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = companyId,
            ledgerCode = "0L",
            documentType = "GL_MANUAL",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = "UNBALANCED-001",
            sourceModule = "FIN_GL",
            sourceDocumentId = UUID.randomUUID(),
            lines = listOf(
                JournalLineDraft(
                    lineNumber = 1,
                    accountCode = "110000",
                    side = PostingSide.DEBIT,
                    amount = Money.of("500.00", "USD")
                ),
                JournalLineDraft(
                    lineNumber = 2,
                    accountCode = "410000",
                    side = PostingSide.CREDIT,
                    amount = Money.of("450.00", "USD") // Discrepancy of 50.00!
                )
            )
        )

        val ex = assertThrows<InvariantViolationException> {
            accountingGateway.post(unbalancedRequest)
        }
        assertEquals("FIN-INV-1", ex.invariantId)
        assertTrue(ex.message!!.contains("Debits (500.00 USD) do not equal Credits (450.00 USD)"))
    }

    @Test
    fun `should reverse posted journal entry by generating inverse document (FIN-GL-007, P1)`() {
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()

        val request = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = companyId,
            documentType = "GL_MANUAL",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = "ORIGINAL-DOC",
            sourceModule = "FIN_GL",
            sourceDocumentId = UUID.randomUUID(),
            lines = listOf(
                JournalLineDraft(1, "110000", PostingSide.DEBIT, Money.of("250.00", "USD")),
                JournalLineDraft(2, "410000", PostingSide.CREDIT, Money.of("250.00", "USD"))
            )
        )

        val origRef = accountingGateway.post(request)
        val revRef = accountingGateway.reverse(tenantId, origRef.documentId, "Correction of error", LocalDate.now())

        assertNotNull(revRef)
        assertNotEquals(origRef.documentId, revRef.documentId)

        val original = journalRepository.findById(origRef.documentId).get()
        assertTrue(original.isReversed)
        assertEquals(revRef.documentId, original.reversalDocumentId)

        val reversal = journalRepository.findById(revRef.documentId).get()
        val revDebit = reversal.lines.first { it.postingSide == PostingSide.DEBIT }
        val revCredit = reversal.lines.first { it.postingSide == PostingSide.CREDIT }

        assertEquals("410000", revDebit.accountCode) // Was Credit in orig
        assertEquals("110000", revCredit.accountCode) // Was Debit in orig
        assertEquals(0, BigDecimal("250.00").compareTo(revDebit.amount))
    }
}
