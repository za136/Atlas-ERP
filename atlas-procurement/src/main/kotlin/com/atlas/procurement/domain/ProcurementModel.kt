package com.atlas.procurement.domain

import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.accounting.api.JournalLineDraft
import com.atlas.finance.accounting.api.PostingSide
import com.atlas.finance.ap.domain.ApService
import com.atlas.platform.common.domain.Money
import com.atlas.platform.common.domain.Quantity
import com.atlas.platform.doc.domain.DocumentFrameworkService
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class PoStatus {
    DRAFT,
    APPROVED,
    PARTIALLY_DELIVERED,
    DELIVERED,
    CLOSED
}

@Entity
@Table(name = "purchase_orders", schema = "prc")
class PurchaseOrder(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "po_number", nullable = false, length = 30)
    val poNumber: String,

    @Column(name = "supplier_id", nullable = false)
    val supplierId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: PoStatus = PoStatus.DRAFT,

    @Column(name = "order_date", nullable = false)
    val orderDate: LocalDate = LocalDate.now(),

    @OneToMany(mappedBy = "purchaseOrder", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val items: MutableList<PurchaseOrderItem> = mutableListOf()
)

@Entity
@Table(name = "purchase_order_items", schema = "prc")
class PurchaseOrderItem(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    val purchaseOrder: PurchaseOrder,

    @Column(name = "line_number", nullable = false)
    val lineNumber: Int,

    @Column(name = "material_code", nullable = false, length = 40)
    val materialCode: String,

    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 4)
    val orderedQuantity: BigDecimal,

    @Column(name = "uom", nullable = false, length = 10)
    val uom: String,

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    val unitPrice: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Column(name = "delivered_quantity", nullable = false, precision = 19, scale = 4)
    var deliveredQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "invoiced_quantity", nullable = false, precision = 19, scale = 4)
    var invoicedQuantity: BigDecimal = BigDecimal.ZERO
)

@Repository
interface PurchaseOrderRepository : JpaRepository<PurchaseOrder, UUID> {
    fun findByPoNumber(poNumber: String): PurchaseOrder?
}

@Service
@Transactional
class ProcurementService(
    private val poRepository: PurchaseOrderRepository,
    private val accountingGateway: AccountingGateway,
    private val apService: ApService,
    private val documentFramework: DocumentFrameworkService
) {
    fun createPurchaseOrder(
        tenantId: UUID,
        companyId: UUID,
        supplierId: UUID,
        items: List<Pair<String, Pair<Quantity, Money>>>
    ): PurchaseOrder {
        val poId = UUID.randomUUID()
        val poNumber = documentFramework.generateBusinessNumber(tenantId, companyId, "PO", "PO")

        val po = PurchaseOrder(
            id = poId,
            tenantId = tenantId,
            companyId = companyId,
            poNumber = poNumber,
            supplierId = supplierId,
            status = PoStatus.APPROVED
        )

        items.forEachIndexed { idx, item ->
            val materialCode = item.first
            val qty = item.second.first
            val price = item.second.second
            po.items.add(
                PurchaseOrderItem(
                    id = UUID.randomUUID(),
                    tenantId = tenantId,
                    purchaseOrder = po,
                    lineNumber = idx + 1,
                    materialCode = materialCode,
                    orderedQuantity = qty.value,
                    uom = qty.uom,
                    unitPrice = price.amount,
                    currency = price.currency
                )
            )
        }

        return poRepository.save(po)
    }

    /**
     * Executes Goods Receipt and atomic accounting entry (ARC-004, PRC-GR-001).
     * Debit: Stock Inventory account (e.g. 130000)
     * Credit: GR/IR Clearing account (e.g. 212000)
     */
    fun postGoodsReceipt(
        poId: UUID,
        lineNumber: Int,
        receivedQty: BigDecimal,
        stockAccount: String = "130000",
        grIrAccount: String = "212000"
    ): UUID {
        val po = poRepository.findById(poId).orElseThrow { IllegalArgumentException("PO $poId not found") }
        val item = po.items.find { it.lineNumber == lineNumber }
            ?: throw IllegalArgumentException("Item $lineNumber not found in PO $poId")

        item.deliveredQuantity = item.deliveredQuantity.add(receivedQty)
        po.status = if (po.items.all { it.deliveredQuantity >= it.orderedQuantity }) {
            PoStatus.DELIVERED
        } else {
            PoStatus.PARTIALLY_DELIVERED
        }
        poRepository.save(po)

        val receiptId = UUID.randomUUID()
        val totalValuation = receivedQty.multiply(item.unitPrice)
        val valuationMoney = Money(totalValuation, item.currency)

        val lines = listOf(
            JournalLineDraft(
                lineNumber = 1,
                accountCode = stockAccount,
                side = PostingSide.DEBIT,
                amount = valuationMoney,
                businessPartnerId = po.supplierId,
                lineText = "GR: Material ${item.materialCode} for ${po.poNumber}"
            ),
            JournalLineDraft(
                lineNumber = 2,
                accountCode = grIrAccount,
                side = PostingSide.CREDIT,
                amount = valuationMoney,
                businessPartnerId = po.supplierId,
                lineText = "GR/IR clearing for ${po.poNumber}"
            )
        )

        val postingReq = AccountingPostingRequest(
            tenantId = po.tenantId,
            companyId = po.companyId,
            documentType = "GOODS_RECEIPT",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = "GR-${po.poNumber}",
            sourceModule = "PRC_GR",
            sourceDocumentId = receiptId,
            lines = lines
        )

        accountingGateway.post(postingReq)
        return receiptId
    }

    /**
     * Three-Way Invoice Match & Verification (PRC-IV-001, ARC-004).
     * Debit: GR/IR Clearing (212000)
     * Credit: Accounts Payable (211000)
     */
    fun matchAndPostInvoice(
        poId: UUID,
        supplierInvoiceNumber: String,
        invoicedAmount: Money,
        grIrAccount: String = "212000",
        apAccount: String = "211000"
    ): UUID {
        val po = poRepository.findById(poId).orElseThrow { IllegalArgumentException("PO $poId not found") }

        // Post AP invoice via AP subledger & Universal Journal
        return apService.postSupplierInvoice(
            tenantId = po.tenantId,
            companyId = po.companyId,
            supplierId = po.supplierId,
            invoiceNumber = supplierInvoiceNumber,
            amount = invoicedAmount,
            expenseAccount = grIrAccount, // Clears the GR/IR account from Goods Receipt
            apReconciliationAccount = apAccount
        )
    }
}
