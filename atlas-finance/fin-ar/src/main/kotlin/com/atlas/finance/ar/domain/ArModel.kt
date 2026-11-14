package com.atlas.finance.ar.domain

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
@Table(name = "ar_open_items", schema = "fin")
class ArOpenItem(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

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
interface ArOpenItemRepository : JpaRepository<ArOpenItem, UUID> {
    fun findByCompanyIdAndIsClearedFalse(companyId: UUID): List<ArOpenItem>
    fun findByCustomerIdAndIsClearedFalse(customerId: UUID): List<ArOpenItem>
}

@Service
@Transactional
class ArService(
    private val openItemRepository: ArOpenItemRepository,
    private val accountingGateway: AccountingGateway
) {
    /**
     * Posts a customer billing invoice and registers an AR open item atomically (ARC-004).
     */
    fun postCustomerInvoice(
        tenantId: UUID,
        companyId: UUID,
        customerId: UUID,
        invoiceNumber: String,
        amount: Money,
        revenueAccount: String = "410000",
        arReconciliationAccount: String = "112000",
        profitCenterCode: String? = null,
        dueDate: LocalDate = LocalDate.now().plusDays(30)
    ): UUID {
        val invoiceId = UUID.randomUUID()

        val lines = listOf(
            JournalLineDraft(
                lineNumber = 1,
                accountCode = arReconciliationAccount,
                side = PostingSide.DEBIT,
                amount = amount,
                businessPartnerId = customerId,
                lineText = "Customer receivable $invoiceNumber"
            ),
            JournalLineDraft(
                lineNumber = 2,
                accountCode = revenueAccount,
                side = PostingSide.CREDIT,
                amount = amount,
                profitCenterCode = profitCenterCode,
                businessPartnerId = customerId,
                lineText = "Revenue for $invoiceNumber"
            )
        )

        val postingReq = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = companyId,
            documentType = "AR_INVOICE",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = invoiceNumber,
            headerText = "Customer Inv $invoiceNumber",
            sourceModule = "FIN_AR",
            sourceDocumentId = invoiceId,
            lines = lines
        )

        val accountingRef = accountingGateway.post(postingReq)

        val openItem = ArOpenItem(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            companyId = companyId,
            customerId = customerId,
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
     * Clears an AR open item upon receiving incoming cash (FIN-AR-001).
     */
    fun receiveCustomerPayment(
        tenantId: UUID,
        companyId: UUID,
        openItemId: UUID,
        bankAccount: String = "110000",
        arReconciliationAccount: String = "112000"
    ): UUID {
        val openItem = openItemRepository.findById(openItemId).orElseThrow {
            IllegalArgumentException("AR Open item $openItemId not found")
        }
        require(!openItem.isCleared) { "AR item already cleared" }

        val paymentId = UUID.randomUUID()
        val paymentAmount = Money(openItem.amount, openItem.currency)

        val lines = listOf(
            JournalLineDraft(
                lineNumber = 1,
                accountCode = bankAccount,
                side = PostingSide.DEBIT,
                amount = paymentAmount,
                businessPartnerId = openItem.customerId,
                lineText = "Cash receipt for ${openItem.invoiceNumber}"
            ),
            JournalLineDraft(
                lineNumber = 2,
                accountCode = arReconciliationAccount,
                side = PostingSide.CREDIT,
                amount = paymentAmount,
                businessPartnerId = openItem.customerId,
                lineText = "Clear AR invoice ${openItem.invoiceNumber}"
            )
        )

        val postReq = AccountingPostingRequest(
            tenantId = tenantId,
            companyId = companyId,
            documentType = "AR_PAYMENT",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = "RCPT-${openItem.invoiceNumber}",
            sourceModule = "FIN_AR",
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
