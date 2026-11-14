package com.atlas.finance.accounting.api

import com.atlas.platform.common.domain.Money
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate
import java.util.UUID

enum class PostingSide {
    DEBIT,
    CREDIT
}

enum class AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE,
    RECONCILIATION_AP,
    RECONCILIATION_AR
}

data class JournalLineDraft @JsonCreator constructor(
    @JsonProperty("lineNumber") val lineNumber: Int,
    @JsonProperty("accountCode") val accountCode: String,
    @JsonProperty("side") val side: PostingSide,
    @JsonProperty("amount") val amount: Money,
    @JsonProperty("costCenterCode") val costCenterCode: String? = null,
    @JsonProperty("profitCenterCode") val profitCenterCode: String? = null,
    @JsonProperty("lineText") val lineText: String? = null,
    @JsonProperty("businessPartnerId") val businessPartnerId: UUID? = null
)

data class AccountingPostingRequest @JsonCreator constructor(
    @JsonProperty("tenantId") val tenantId: UUID,
    @JsonProperty("companyId") val companyId: UUID,
    @JsonProperty("ledgerCode") val ledgerCode: String = "0L",
    @JsonProperty("documentType") val documentType: String,
    @JsonProperty("postingDate") val postingDate: LocalDate,
    @JsonProperty("documentDate") val documentDate: LocalDate,
    @JsonProperty("reference") val reference: String,
    @JsonProperty("headerText") val headerText: String? = null,
    @JsonProperty("sourceModule") val sourceModule: String,
    @JsonProperty("sourceDocumentId") val sourceDocumentId: UUID,
    @JsonProperty("lines") val lines: List<JournalLineDraft>
)

data class AccountingDocumentRef @JsonCreator constructor(
    @JsonProperty("documentId") val documentId: UUID,
    @JsonProperty("documentNumber") val documentNumber: String,
    @JsonProperty("ledgerCode") val ledgerCode: String,
    @JsonProperty("postingDate") val postingDate: LocalDate,
    @JsonProperty("contentHash") val contentHash: String
)

interface AccountingGateway {
    /**
     * Executes atomic posting into the Universal Journal within the caller's active database transaction.
     * Enforces Invariants FIN-INV-1 through FIN-INV-5 and Rule ARC-004.
     */
    fun post(request: AccountingPostingRequest): AccountingDocumentRef

    /**
     * Reversal of an existing posted journal entry.
     * Reversals post a new immutable inverse document (P1, FIN-GL-007).
     */
    fun reverse(
        tenantId: UUID,
        documentId: UUID,
        reversalReason: String,
        reversalDate: LocalDate
    ): AccountingDocumentRef
}
