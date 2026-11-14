package com.atlas.finance.accounting.domain

import com.atlas.finance.accounting.api.PostingSide
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "universal_journal_entries", schema = "fin")
class UniversalJournalEntry(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "ledger_code", nullable = false, length = 10)
    val ledgerCode: String,

    @Column(name = "document_number", nullable = false, length = 30)
    val documentNumber: String,

    @Column(name = "document_type", nullable = false, length = 30)
    val documentType: String,

    @Column(name = "fiscal_year", nullable = false)
    val fiscalYear: Int,

    @Column(name = "fiscal_period", nullable = false)
    val fiscalPeriod: Int,

    @Column(name = "posting_date", nullable = false)
    val postingDate: LocalDate,

    @Column(name = "document_date", nullable = false)
    val documentDate: LocalDate,

    @Column(name = "entry_timestamp", nullable = false)
    val entryTimestamp: Instant = Instant.now(),

    @Column(name = "reference", nullable = false, length = 50)
    val reference: String,

    @Column(name = "header_text", length = 100)
    val headerText: String? = null,

    @Column(name = "source_module", nullable = false, length = 20)
    val sourceModule: String,

    @Column(name = "source_document_id", nullable = false)
    val sourceDocumentId: UUID,

    @Column(name = "content_hash", nullable = false, length = 64)
    val contentHash: String,

    @Column(name = "is_reversed", nullable = false)
    var isReversed: Boolean = false,

    @Column(name = "reversal_document_id")
    var reversalDocumentId: UUID? = null,

    @Column(name = "reversed_document_id")
    val reversedDocumentId: UUID? = null,

    @OneToMany(mappedBy = "journalEntry", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val lines: MutableList<UniversalJournalItem> = mutableListOf()
)

@Entity
@Table(name = "universal_journal_items", schema = "fin")
class UniversalJournalItem(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    val journalEntry: UniversalJournalEntry,

    @Column(name = "line_number", nullable = false)
    val lineNumber: Int,

    @Column(name = "account_code", nullable = false, length = 30)
    val accountCode: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "posting_side", nullable = false, length = 10)
    val postingSide: PostingSide,

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Column(name = "cost_center_code", length = 30)
    val costCenterCode: String? = null,

    @Column(name = "profit_center_code", length = 30)
    val profitCenterCode: String? = null,

    @Column(name = "business_partner_id")
    val businessPartnerId: UUID? = null,

    @Column(name = "line_text", length = 150)
    val lineText: String? = null
)
