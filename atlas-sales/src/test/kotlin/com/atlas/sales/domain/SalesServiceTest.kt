package com.atlas.sales.domain

import com.atlas.finance.accounting.api.AccountingDocumentRef
import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.ar.domain.ArService
import com.atlas.platform.common.domain.Money
import com.atlas.platform.common.domain.Quantity
import com.atlas.platform.doc.domain.DocumentFrameworkService
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class SalesServiceTest {

    private val salesOrderRepository = mockk<SalesOrderRepository>(relaxed = true)
    private val accountingGateway = mockk<AccountingGateway>()
    private val arService = mockk<ArService>()
    private val documentFramework = mockk<DocumentFrameworkService>()

    private lateinit var salesService: SalesService

    @BeforeEach
    fun setUp() {
        salesService = SalesService(salesOrderRepository, accountingGateway, arService, documentFramework)
    }

    @Test
    fun `should create confirmed sales order`() {
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        val customerId = UUID.randomUUID()

        every { documentFramework.generateBusinessNumber(tenantId, companyId, "SO", "SO") } returns "SO-000001"
        every { salesOrderRepository.save(any()) } answers { firstArg() }

        val items = listOf(
            "FINISHED-PROD-1" to (Quantity.of(5, "EA") to Money.of("150.00", "USD"))
        )

        val so = salesService.createSalesOrder(tenantId, companyId, customerId, items)

        assertNotNull(so)
        assertEquals("SO-000001", so.orderNumber)
        assertEquals(SalesOrderStatus.CONFIRMED, so.status)
        assertEquals(1, so.items.size)
        assertEquals("FINISHED-PROD-1", so.items[0].materialCode)
        assertEquals(BigDecimal("150.00"), so.items[0].unitPrice)
    }

    @Test
    fun `should post goods issue with atomic COGS and stock reduction (SLS-DL-001, ARC-004)`() {
        val orderId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        val customerId = UUID.randomUUID()

        val so = SalesOrder(
            id = orderId,
            tenantId = tenantId,
            companyId = companyId,
            orderNumber = "SO-000001",
            customerId = customerId,
            status = SalesOrderStatus.CONFIRMED
        )
        val item = SalesOrderItem(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            salesOrder = so,
            lineNumber = 1,
            materialCode = "FINISHED-PROD-1",
            orderedQuantity = BigDecimal("5"),
            uom = "EA",
            unitPrice = BigDecimal("150.00"),
            currency = "USD"
        )
        so.items.add(item)

        every { salesOrderRepository.findById(orderId) } returns Optional.of(so)
        every { salesOrderRepository.save(any()) } answers { firstArg() }

        val accountingRefSlot = slot<AccountingPostingRequest>()
        every { accountingGateway.post(capture(accountingRefSlot)) } returns AccountingDocumentRef(
            documentId = UUID.randomUUID(),
            documentNumber = "JE-0002",
            ledgerCode = "0L",
            postingDate = LocalDate.now(),
            contentHash = "hash456"
        )

        val deliveryId = salesService.postGoodsIssue(
            orderId = orderId,
            lineNumber = 1,
            issuedQuantity = BigDecimal("5"),
            valuationCostPerUnit = Money.of("80.00", "USD")
        )
        assertNotNull(deliveryId)

        assertEquals(SalesOrderStatus.DELIVERED, so.status)
        assertEquals(BigDecimal("5"), item.deliveredQuantity)

        // Verify COGS (510000) Debit and Stock (130000) Credit
        verify(exactly = 1) { accountingGateway.post(any()) }
        val captured = accountingRefSlot.captured
        assertEquals("GOODS_ISSUE", captured.documentType)
        assertEquals("510000", captured.lines[0].accountCode) // COGS
        assertEquals("130000", captured.lines[1].accountCode) // Stock Credit
        assertEquals(BigDecimal("400.00"), captured.lines[0].amount.amount) // 5 * 80.00
    }
}
