package com.atlas.app.api

import com.atlas.finance.accounting.api.AccountingDocumentRef
import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.accounting.domain.FinancialReportingService
import com.atlas.finance.accounting.domain.TrialBalanceReport
import com.atlas.finance.accounting.domain.UniversalJournalEntry
import com.atlas.finance.accounting.domain.UniversalJournalEntryRepository
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

data class ReversalApiRequest @JsonCreator constructor(
    @JsonProperty("tenantId") val tenantId: UUID,
    @JsonProperty("documentId") val documentId: UUID,
    @JsonProperty("reason") val reason: String,
    @JsonProperty("reversalDate") val reversalDate: LocalDate
)

@RestController
@RequestMapping("/api/v1/finance/journal")
@Tag(name = "Universal Journal & Ledger API", description = "Core single-ledger accounting operations (DAT-001, FIN-GL)")
class UniversalJournalController(
    private val accountingGateway: AccountingGateway,
    private val reportingService: FinancialReportingService,
    private val journalEntryRepository: UniversalJournalEntryRepository
) {
    @PostMapping("/post")
    @Operation(summary = "Post a balanced journal entry atomically into the Universal Journal")
    fun postJournalEntry(@RequestBody request: AccountingPostingRequest): ResponseEntity<AccountingDocumentRef> {
        val result = accountingGateway.post(request)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/reverse")
    @Operation(summary = "Post an immutable reversal document for an existing journal entry")
    fun reverseJournalEntry(@RequestBody request: ReversalApiRequest): ResponseEntity<AccountingDocumentRef> {
        val result = accountingGateway.reverse(
            tenantId = request.tenantId,
            documentId = request.documentId,
            reversalReason = request.reason,
            reversalDate = request.reversalDate
        )
        return ResponseEntity.ok(result)
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Generate a real-time Trial Balance report directly from Universal Journal items")
    fun getTrialBalance(
        @RequestParam companyId: UUID,
        @RequestParam(defaultValue = "0L") ledgerCode: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOfDate: LocalDate? = null
    ): ResponseEntity<TrialBalanceReport> {
        val effectiveDate = asOfDate ?: LocalDate.now()
        val report = reportingService.getTrialBalance(companyId, ledgerCode, effectiveDate)
        return ResponseEntity.ok(report)
    }

    @GetMapping("/entries")
    @Operation(summary = "Retrieve all posted universal journal entries")
    fun listJournalEntries(): ResponseEntity<List<UniversalJournalEntry>> {
        return ResponseEntity.ok(journalEntryRepository.findAll())
    }
}
