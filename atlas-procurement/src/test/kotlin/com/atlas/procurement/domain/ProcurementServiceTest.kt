package com.atlas.procurement.domain

import com.atlas.finance.accounting.api.AccountingDocumentRef
import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.ap.domain.ApService
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

class ProcurementServiceTest {

    private val poRepository = mockk<PurchaseOrderRepository>(relaxed = true)
    private val accountingGateway = mockk<AccountingGateway>()
    private val apService = mockk<ApService>()
    private val documentFramework = mockk<DocumentFrameworkService>()

    private lateinit var procurementService: ProcurementService

    @BeforeEach
    fun setUp() {
        procurementService = ProcurementService(poRepository, accountingGateway, apService, documentFramework)
    }

    @Test
    fun `should create approved purchase order`() {
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        val supplierId = UUID.randomUUID()

        every { documentFramework.generateBusinessNumber(tenantId, companyId, "PO", "PO") } returns "PO-000001"
        every { poRepository.save(any()) } answers { firstArg() }

        val items = listOf(
            "MAT-100" to (Quantity.of(10, "EA") to Money.of("50.00", "USD"))
        )

        val po = procurementService.createPurchaseOrder(tenantId, companyId, supplierId, items)

        assertNotNull(po)
        assertEquals("PO-000001", po.poNumber)
        assertEquals(PoStatus.APPROVED, po.status)
        assertEquals(1, po.items.size)
        assertEquals("MAT-100", po.items[0].materialCode)
        assertEquals(BigDecimal("10"), po.items[0].orderedQuantity)
    }

    @Test
    fun `should post goods receipt and invoke accounting gateway atomically (ARC-004)`() {
        val poId = UUID.randomUUID()
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        val supplierId = UUID.randomUUID()

        val po = PurchaseOrder(
            id = poId,
            tenantId = tenantId,
            companyId = companyId,
            poNumber = "PO-000001",
            supplierId = supplierId,
            status = PoStatus.APPROVED
        )
        val item = PurchaseOrderItem(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            purchaseOrder = po,
            lineNumber = 1,
            materialCode = "MAT-100",
            orderedQuantity = BigDecimal("10"),
            uom = "EA",
            unitPrice = BigDecimal("50.00"),
            currency = "USD"
        )
        po.items.add(item)

        every { poRepository.findById(poId) } returns Optional.of(po)
        every { poRepository.save(any()) } answers { firstArg() }

        val accountingRefSlot = slot<AccountingPostingRequest>()
        every { accountingGateway.post(capture(accountingRefSlot)) } returns AccountingDocumentRef(
            documentId = UUID.randomUUID(),
            documentNumber = "JE-0001",
            ledgerCode = "0L",
            postingDate = LocalDate.now(),
            contentHash = "hash123"
        )

        val receiptId = procurementService.postGoodsReceipt(poId, 1, BigDecimal("10"))
        assertNotNull(receiptId)

        // Verify status updated
        assertEquals(PoStatus.DELIVERED, po.status)
        assertEquals(BigDecimal("10"), item.deliveredQuantity)

        // Verify atomic accounting call (Debit Stock 130000, Credit GR/IR 212000)
        verify(exactly = 1) { accountingGateway.post(any()) }
        val captured = accountingRefSlot.captured
        assertEquals("GOODS_RECEIPT", captured.documentType)
        assertEquals(2, captured.lines.size)
        assertEquals("130000", captured.lines[0].accountCode) // Stock Debit
        assertEquals("212000", captured.lines[1].accountCode) // GR/IR Credit
        assertEquals(BigDecimal("500.00"), captured.lines[0].amount.amount)
    }
}
