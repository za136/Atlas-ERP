package com.atlas.finance.accounting.domain

import com.atlas.finance.accounting.api.*
import com.atlas.platform.aud.domain.AuditStoreService
import com.atlas.platform.common.domain.Money
import com.atlas.platform.common.domain.checkInvariant
import com.atlas.platform.doc.domain.DocumentFrameworkService
import com.atlas.platform.iam.domain.CurrentPrincipalProvider
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface UniversalJournalEntryRepository : JpaRepository<UniversalJournalEntry, UUID> {
    fun findByDocumentNumber(documentNumber: String): UniversalJournalEntry?
}

@Service
class AccountingGatewayImpl(
    private val txService: TransactionalAccountingPostingService,
    private val principalProvider: CurrentPrincipalProvider
) : AccountingGateway {

    override fun post(request: AccountingPostingRequest): AccountingDocumentRef {
        principalProvider.setTenantId(request.tenantId)
        return txService.postInTransaction(request)
    }

    override fun reverse(
        tenantId: UUID,
        documentId: UUID,
        reversalReason: String,
        reversalDate: LocalDate
    ): AccountingDocumentRef {
        principalProvider.setTenantId(tenantId)
        return txService.reverseInTransaction(tenantId, documentId, reversalReason, reversalDate)
    }
}

@Service
@Transactional
class TransactionalAccountingPostingService(
    private val journalRepository: UniversalJournalEntryRepository,
    private val documentFramework: DocumentFrameworkService,
    private val auditStore: AuditStoreService,
    private val principalProvider: CurrentPrincipalProvider
) {

    fun postInTransaction(request: AccountingPostingRequest): AccountingDocumentRef {
        // Validation: At least 2 lines required (FIN-GL-001)
        require(request.lines.size >= 2) {
            "A journal entry must contain at least two line items. Found: ${request.lines.size}"
        }

        // Invariant FIN-INV-1: Debits equal credits per document and currency
        val linesByCurrency = request.lines.groupBy { it.amount.currency }
        for ((currency, lines) in linesByCurrency) {
            val totalDebits = lines.filter { it.side == PostingSide.DEBIT }
                .fold(BigDecimal.ZERO) { acc, line -> acc.add(line.amount.amount) }
            val totalCredits = lines.filter { it.side == PostingSide.CREDIT }
                .fold(BigDecimal.ZERO) { acc, line -> acc.add(line.amount.amount) }

            checkInvariant("FIN-INV-1", totalDebits.compareTo(totalCredits) == 0) {
                "Debits ($totalDebits $currency) do not equal Credits ($totalCredits $currency) for document: ${request.reference}"
            }
        }

        val entryId = UUID.randomUUID()
        val docNumber = documentFramework.generateBusinessNumber(
            tenantId = request.tenantId,
            companyId = request.companyId,
            docType = request.documentType,
            defaultPrefix = "JE"
        )

        // Compute content hash (FIN-INV-4)
        val contentHash = computeHash(request, docNumber)

        val entry = UniversalJournalEntry(
            id = entryId,
            tenantId = request.tenantId,
            companyId = request.companyId,
            ledgerCode = request.ledgerCode,
            documentNumber = docNumber,
            documentType = request.documentType,
            fiscalYear = request.postingDate.year,
            fiscalPeriod = request.postingDate.monthValue,
            postingDate = request.postingDate,
            documentDate = request.documentDate,
            entryTimestamp = Instant.now(),
            reference = request.reference,
            headerText = request.headerText,
            sourceModule = request.sourceModule,
            sourceDocumentId = request.sourceDocumentId,
            contentHash = contentHash
        )

        request.lines.forEach { lineDraft ->
            val item = UniversalJournalItem(
                id = UUID.randomUUID(),
                tenantId = request.tenantId,
                journalEntry = entry,
                lineNumber = lineDraft.lineNumber,
                accountCode = lineDraft.accountCode,
                postingSide = lineDraft.side,
                amount = lineDraft.amount.amount,
                currency = lineDraft.amount.currency,
                costCenterCode = lineDraft.costCenterCode,
                profitCenterCode = lineDraft.profitCenterCode,
                businessPartnerId = lineDraft.businessPartnerId,
                lineText = lineDraft.lineText
            )
            entry.lines.add(item)
        }

        journalRepository.save(entry)

        // Link document flow
        documentFramework.linkDocuments(
            tenantId = request.tenantId,
            predType = request.sourceModule,
            predId = request.sourceDocumentId,
            succType = "ACCOUNTING_ENTRY",
            succId = entryId
        )

        // Audit Trail
        val principal = principalProvider.get()
        auditStore.recordEvent(
            tenantId = request.tenantId,
            entityType = "JOURNAL_ENTRY",
            entityId = entryId,
            action = "POST",
            actorId = principal.id,
            actorName = principal.name,
            actorType = principal.type,
            payloadAfter = "DocNo: $docNumber, Ref: ${request.reference}, Hash: $contentHash"
        )

        return AccountingDocumentRef(
            documentId = entryId,
            documentNumber = docNumber,
            ledgerCode = request.ledgerCode,
            postingDate = request.postingDate,
            contentHash = contentHash
        )
    }

    fun reverseInTransaction(
        tenantId: UUID,
        documentId: UUID,
        reversalReason: String,
        reversalDate: LocalDate
    ): AccountingDocumentRef {
        val original = journalRepository.findById(documentId).orElseThrow {
            IllegalArgumentException("Journal entry not found: $documentId")
        }

        require(!original.isReversed) {
            "Journal entry ${original.documentNumber} has already been reversed by ${original.reversalDocumentId}"
        }

        // Invert posting sides (FIN-GL-007, P1)
        val invertedLines = original.lines.map { line ->
            JournalLineDraft(
                lineNumber = line.lineNumber,
                accountCode = line.accountCode,
                side = if (line.postingSide == PostingSide.DEBIT) PostingSide.CREDIT else PostingSide.DEBIT,
                amount = Money(line.amount, line.currency),
                costCenterCode = line.costCenterCode,
                profitCenterCode = line.profitCenterCode,
                businessPartnerId = line.businessPartnerId,
                lineText = "Reversal: $reversalReason (Orig: ${original.documentNumber})"
            )
        }

        val reversalRequest = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = original.companyId,
            ledgerCode = original.ledgerCode,
            documentType = "REVERSAL",
            postingDate = reversalDate,
            documentDate = reversalDate,
            reference = "REV-${original.documentNumber}",
            headerText = "Reversal of ${original.documentNumber}: $reversalReason",
            sourceModule = "FIN_GL",
            sourceDocumentId = original.id,
            lines = invertedLines
        )

        val reversalRef = postInTransaction(reversalRequest)

        original.isReversed = true
        original.reversalDocumentId = reversalRef.documentId
        journalRepository.save(original)

        return reversalRef
    }

    private fun computeHash(request: AccountingPostingRequest, docNumber: String): String {
        val raw = StringBuilder()
        raw.append("${request.companyId}:${request.ledgerCode}:$docNumber:${request.postingDate}:${request.reference};")
        request.lines.sortedBy { it.lineNumber }.forEach { line ->
            raw.append("${line.lineNumber}:${line.accountCode}:${line.side}:${line.amount.amount}:${line.amount.currency};")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toString().toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
