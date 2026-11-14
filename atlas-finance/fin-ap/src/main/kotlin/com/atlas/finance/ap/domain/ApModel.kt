package com.atlas.finance.ap.domain

import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.accounting.api.JournalLineDraft
import com.atlas.finance.accounting.api.PostingSide
import com.atlas.platform.common.domain.Money
import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "ap_open_items", schema = "fin")
class ApOpenItem(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "supplier_id", nullable = false)
    val supplierId: UUID,

    @Column(name = "document_id", nullable = false)
    val documentId: UUID,

    @Column(name = "invoice_number", nullable = false, length = 50)
    val invoiceNumber: String,

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Column(name = "due_date", nullable = false)
    val dueDate: LocalDate,

    @Column(name = "is_cleared", nullable = false)
    var isCleared: Boolean = false,

    @Column(name = "clearing_document_id")
    var clearingDocumentId: UUID? = null
)

@Repository
interface ApOpenItemRepository : JpaRepository<ApOpenItem, UUID> {
    fun findByCompanyIdAndIsClearedFalse(companyId: UUID): List<ApOpenItem>
    fun findBySupplierIdAndIsClearedFalse(supplierId: UUID): List<ApOpenItem>
}

@Service
@Transactional
class ApService(
    private val openItemRepository: ApOpenItemRepository,
    private val accountingGateway: AccountingGateway
) {
    /**
     * Posts a supplier invoice and registers an AP open item atomically (ARC-004).
     */
    fun postSupplierInvoice(
        tenantId: UUID,
        companyId: UUID,
        supplierId: UUID,
        invoiceNumber: String,
        amount: Money,
        expenseAccount: String,
        apReconciliationAccount: String = "211000",
        costCenterCode: String? = null,
        dueDate: LocalDate = LocalDate.now().plusDays(30)
    ): UUID {
        val invoiceId = UUID.randomUUID()

        val lines = listOf(
            JournalLineDraft(
                lineNumber = 1,
                accountCode = expenseAccount,
                side = PostingSide.DEBIT,
                amount = amount,
                costCenterCode = costCenterCode,
                businessPartnerId = supplierId,
                lineText = "Supplier invoice $invoiceNumber"
            ),
            JournalLineDraft(
                lineNumber = 2,
                accountCode = apReconciliationAccount,
                side = PostingSide.CREDIT,
                amount = amount,
                businessPartnerId = supplierId,
                lineText = "AP liability: $invoiceNumber"
            )
        )

        val postingReq = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = companyId,
            documentType = "AP_INVOICE",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = invoiceNumber,
            headerText = "Supplier Inv $invoiceNumber",
            sourceModule = "FIN_AP",
            sourceDocumentId = invoiceId,
            lines = lines
        )

        val accountingRef = accountingGateway.post(postingReq)

        // Register Open Item (Subledger for FIN-INV-2)
        val openItem = ApOpenItem(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            companyId = companyId,
            supplierId = supplierId,
            documentId = accountingRef.documentId,
            invoiceNumber = invoiceNumber,
            amount = amount.amount,
            currency = amount.currency,
            dueDate = dueDate
        )
        openItemRepository.save(openItem)

        return invoiceId
    }

    /**
     * Executes payment run clearing open items and posting bank payment entry (FIN-AP-003).
     */
    fun payOpenItem(
        tenantId: UUID,
        companyId: UUID,
        openItemId: UUID,
        bankAccount: String = "110000",
        apReconciliationAccount: String = "211000"
    ): UUID {
        val openItem = openItemRepository.findById(openItemId).orElseThrow {
            IllegalArgumentException("Open item $openItemId not found")
        }
        require(!openItem.isCleared) { "Open item already cleared" }

        val paymentId = UUID.randomUUID()
        val paymentAmount = Money(openItem.amount, openItem.currency)

        val lines = listOf(
            JournalLineDraft(
                lineNumber = 1,
                accountCode = apReconciliationAccount,
                side = PostingSide.DEBIT,
                amount = paymentAmount,
                businessPartnerId = openItem.supplierId,
                lineText = "Clear AP invoice ${openItem.invoiceNumber}"
            ),
            JournalLineDraft(
                lineNumber = 2,
                accountCode = bankAccount,
                side = PostingSide.CREDIT,
                amount = paymentAmount,
                businessPartnerId = openItem.supplierId,
                lineText = "Bank payment for ${openItem.invoiceNumber}"
            )
        )

        val postReq = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = companyId,
            documentType = "AP_PAYMENT",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = "PAY-${openItem.invoiceNumber}",
            sourceModule = "FIN_AP",
            sourceDocumentId = paymentId,
            lines = lines
        )

        val accountingRef = accountingGateway.post(postReq)

        openItem.isCleared = true
        openItem.clearingDocumentId = accountingRef.documentId
        openItemRepository.save(openItem)

        return paymentId
    }
}
