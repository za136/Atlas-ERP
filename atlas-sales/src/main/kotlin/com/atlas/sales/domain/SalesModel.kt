package com.atlas.sales.domain

import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.accounting.api.JournalLineDraft
import com.atlas.finance.accounting.api.PostingSide
import com.atlas.finance.ar.domain.ArService
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

enum class SalesOrderStatus {
    DRAFT,
    CONFIRMED,
    DELIVERED,
    INVOICED,
    CLOSED
}

@Entity
@Table(name = "sales_orders", schema = "sls")
class SalesOrder(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "order_number", nullable = false, length = 30)
    val orderNumber: String,

    @Column(name = "customer_id", nullable = false)
    val customerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: SalesOrderStatus = SalesOrderStatus.DRAFT,

    @Column(name = "order_date", nullable = false)
    val orderDate: LocalDate = LocalDate.now(),

    @OneToMany(mappedBy = "salesOrder", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val items: MutableList<SalesOrderItem> = mutableListOf()
)

@Entity
@Table(name = "sales_order_items", schema = "sls")
class SalesOrderItem(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val salesOrder: SalesOrder,

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
interface SalesOrderRepository : JpaRepository<SalesOrder, UUID> {
    fun findByOrderNumber(orderNumber: String): SalesOrder?
}

@Service
@Transactional
class SalesService(
    private val salesOrderRepository: SalesOrderRepository,
    private val accountingGateway: AccountingGateway,
    private val arService: ArService,
    private val documentFramework: DocumentFrameworkService
) {
    fun createSalesOrder(
        tenantId: UUID,
        companyId: UUID,
        customerId: UUID,
        items: List<Pair<String, Pair<Quantity, Money>>>
    ): SalesOrder {
        val orderId = UUID.randomUUID()
        val orderNumber = documentFramework.generateBusinessNumber(tenantId, companyId, "SO", "SO")

        val order = SalesOrder(
            id = orderId,
            tenantId = tenantId,
            companyId = companyId,
            orderNumber = orderNumber,
            customerId = customerId,
            status = SalesOrderStatus.CONFIRMED
        )

        items.forEachIndexed { idx, item ->
            val materialCode = item.first
            val qty = item.second.first
            val price = item.second.second
            order.items.add(
                SalesOrderItem(
                    id = UUID.randomUUID(),
                    tenantId = tenantId,
                    salesOrder = order,
                    lineNumber = idx + 1,
                    materialCode = materialCode,
                    orderedQuantity = qty.value,
                    uom = qty.uom,
                    unitPrice = price.amount,
                    currency = price.currency
                )
            )
        }

        return salesOrderRepository.save(order)
    }

    /**
     * Executes Delivery & Goods Issue with atomic accounting posting (SLS-DL-001, ARC-004).
     * Debit: Cost of Goods Sold (COGS e.g. 510000)
     * Credit: Inventory Stock (e.g. 130000)
     */
    fun postGoodsIssue(
        orderId: UUID,
        lineNumber: Int,
        issuedQuantity: BigDecimal,
        valuationCostPerUnit: Money,
        cogsAccount: String = "510000",
        stockAccount: String = "130000"
    ): UUID {
        val order = salesOrderRepository.findById(orderId).orElseThrow {
            IllegalArgumentException("Sales order $orderId not found")
        }
        val item = order.items.find { it.lineNumber == lineNumber }
            ?: throw IllegalArgumentException("Item $lineNumber not found in order $orderId")

        item.deliveredQuantity = item.deliveredQuantity.add(issuedQuantity)
        order.status = SalesOrderStatus.DELIVERED
        salesOrderRepository.save(order)

        val deliveryId = UUID.randomUUID()
        val totalCost = valuationCostPerUnit.amount.multiply(issuedQuantity)
        val cogsMoney = Money(totalCost, valuationCostPerUnit.currency)

        val lines = listOf(
            JournalLineDraft(
                lineNumber = 1,
                accountCode = cogsAccount,
                side = PostingSide.DEBIT,
                amount = cogsMoney,
                businessPartnerId = order.customerId,
                lineText = "COGS for Delivery on Order ${order.orderNumber}"
            ),
            JournalLineDraft(
                lineNumber = 2,
                accountCode = stockAccount,
                side = PostingSide.CREDIT,
                amount = cogsMoney,
                businessPartnerId = order.customerId,
                lineText = "Stock reduction on Delivery for ${order.orderNumber}"
            )
        )

        val postReq = AccountingPostingRequest(
            tenantId = order.tenantId,
            companyId = order.companyId,
            documentType = "GOODS_ISSUE",
            postingDate = LocalDate.now(),
            documentDate = LocalDate.now(),
            reference = "GI-${order.orderNumber}",
            sourceModule = "SLS_GI",
            sourceDocumentId = deliveryId,
            lines = lines
        )

        accountingGateway.post(postReq)
        return deliveryId
    }

    /**
     * Executes Customer Billing and atomic revenue accounting (SLS-BIL-001, ARC-004).
     * Debit: Accounts Receivable (112000)
     * Credit: Revenue (410000)
     */
    fun postBillingDocument(
        orderId: UUID,
        lineNumber: Int,
        billedQuantity: BigDecimal,
        revenueAccount: String = "410000",
        arAccount: String = "112000"
    ): UUID {
        val order = salesOrderRepository.findById(orderId).orElseThrow {
            IllegalArgumentException("Sales order $orderId not found")
        }
        val item = order.items.find { it.lineNumber == lineNumber }
            ?: throw IllegalArgumentException("Item $lineNumber not found in order $orderId")

        require(billedQuantity <= item.deliveredQuantity) {
            "Invoiced quantity ($billedQuantity) cannot exceed delivered quantity (${item.deliveredQuantity})"
        }

        item.invoicedQuantity = item.invoicedQuantity.add(billedQuantity)
        order.status = SalesOrderStatus.INVOICED
        salesOrderRepository.save(order)

        val billingNumber = documentFramework.generateBusinessNumber(order.tenantId, order.companyId, "INV", "INV")
        val totalAmount = item.unitPrice.multiply(billedQuantity)
        val invoiceMoney = Money(totalAmount, item.currency)

        return arService.postCustomerInvoice(
            tenantId = order.tenantId,
            companyId = order.companyId,
            customerId = order.customerId,
            invoiceNumber = billingNumber,
            amount = invoiceMoney,
            revenueAccount = revenueAccount,
            arReconciliationAccount = arAccount
        )
    }
}
