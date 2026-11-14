package com.atlas.inventory.domain

import com.atlas.finance.accounting.api.AccountingDocumentRef
import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.platform.common.domain.InvariantViolationException
import com.atlas.platform.common.domain.Money
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class InventoryServiceTest {

    private val balanceRepository = mockk<StockBalanceRepository>(relaxed = true)
    private val movementRepository = mockk<StockMovementRepository>(relaxed = true)
    private val accountingGateway = mockk<AccountingGateway>()

    private lateinit var inventoryService: InventoryService

    @BeforeEach
    fun setUp() {
        inventoryService = InventoryService(balanceRepository, movementRepository, accountingGateway)
    }

    @Test
    fun `should post initial stock upload and atomically post journal entry`() {
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        val plantId = UUID.randomUUID()
        val slocId = UUID.randomUUID()

        every { balanceRepository.findByPlantIdAndStorageLocationIdAndMaterialCode(plantId, slocId, "RAW-MAT-1") } returns null
        every { balanceRepository.save(any()) } answers { firstArg() }
        every { movementRepository.save(any()) } answers { firstArg() }

        every { accountingGateway.post(any()) } returns AccountingDocumentRef(
            documentId = UUID.randomUUID(),
            documentNumber = "JE-0003",
            ledgerCode = "0L",
            postingDate = LocalDate.now(),
            contentHash = "hash789"
        )

        val movementId = inventoryService.postMovement(
            tenantId = tenantId,
            companyId = companyId,
            plantId = plantId,
            storageLocationId = slocId,
            movementType = "561",
            materialCode = "RAW-MAT-1",
            quantity = BigDecimal("100"),
            uom = "KG",
            valuation = Money.of("1000.00", "USD"),
            reference = "INITIAL-UPLOAD"
        )

        assertNotNull(movementId)
        verify(exactly = 1) { balanceRepository.save(match { it.quantityOnHand.compareTo(BigDecimal("100")) == 0 }) }
        verify(exactly = 1) { movementRepository.save(match { it.movementType == "561" && it.quantity.compareTo(BigDecimal("100")) == 0 }) }
        verify(exactly = 1) { accountingGateway.post(any()) }
    }

    @Test
    fun `should strictly reject negative stock and enforce invariant (INV-INV-1)`() {
        val tenantId = UUID.randomUUID()
        val companyId = UUID.randomUUID()
        val plantId = UUID.randomUUID()
        val slocId = UUID.randomUUID()

        val existingBalance = StockBalance(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            plantId = plantId,
            storageLocationId = slocId,
            materialCode = "RAW-MAT-1",
            quantityOnHand = BigDecimal("10"),
            uom = "KG"
        )

        every { balanceRepository.findByPlantIdAndStorageLocationIdAndMaterialCode(plantId, slocId, "RAW-MAT-1") } returns existingBalance

        val ex = assertThrows<InvariantViolationException> {
            inventoryService.postMovement(
                tenantId = tenantId,
                companyId = companyId,
                plantId = plantId,
                storageLocationId = slocId,
                movementType = "201",
                materialCode = "RAW-MAT-1",
                quantity = BigDecimal("-15"), // Attempt to take 15 from 10!
                uom = "KG"
            )
        }

        assertEquals("INV-INV-1", ex.invariantId)
        assertTrue(ex.message!!.contains("Stock quantity cannot be negative"))
    }
}
