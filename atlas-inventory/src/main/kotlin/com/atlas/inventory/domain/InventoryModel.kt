package com.atlas.inventory.domain

import com.atlas.finance.accounting.api.AccountingGateway
import com.atlas.finance.accounting.api.AccountingPostingRequest
import com.atlas.finance.accounting.api.JournalLineDraft
import com.atlas.finance.accounting.api.PostingSide
import com.atlas.platform.common.domain.Money
import com.atlas.platform.common.domain.checkInvariant
import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "stock_balances", schema = "inv")
class StockBalance(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "plant_id", nullable = false)
    val plantId: UUID,

    @Column(name = "storage_location_id", nullable = false)
    val storageLocationId: UUID,

    @Column(name = "material_code", nullable = false, length = 40)
    val materialCode: String,

    @Column(name = "quantity_on_hand", nullable = false, precision = 19, scale = 4)
    var quantityOnHand: BigDecimal = BigDecimal.ZERO,

    @Column(name = "uom", nullable = false, length = 10)
    val uom: String,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "stock_movements", schema = "inv")
class StockMovement(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "plant_id", nullable = false)
    val plantId: UUID,

    @Column(name = "storage_location_id", nullable = false)
    val storageLocationId: UUID,

    @Column(name = "movement_type", nullable = false, length = 10)
    val movementType: String,

    @Column(name = "material_code", nullable = false, length = 40)
    val materialCode: String,

    @Column(name = "quantity", nullable = false, precision = 19, scale = 4)
    val quantity: BigDecimal,

    @Column(name = "uom", nullable = false, length = 10)
    val uom: String,

    @Column(name = "valuation_amount", precision = 19, scale = 4)
    val valuationAmount: BigDecimal? = null,

    @Column(name = "currency", length = 3)
    val currency: String? = null,

    @Column(name = "reference", length = 50)
    val reference: String? = null,

    @Column(name = "timestamp", nullable = false)
    val timestamp: Instant = Instant.now()
)

@Repository
interface StockBalanceRepository : JpaRepository<StockBalance, UUID> {
    fun findByPlantIdAndStorageLocationIdAndMaterialCode(
        plantId: UUID,
        storageLocationId: UUID,
        materialCode: String
    ): StockBalance?
}

@Repository
interface StockMovementRepository : JpaRepository<StockMovement, UUID> {
    fun findByMaterialCode(materialCode: String): List<StockMovement>
}

@Service
@Transactional
class InventoryService(
    private val balanceRepository: StockBalanceRepository,
    private val movementRepository: StockMovementRepository,
    private val accountingGateway: AccountingGateway
) {
    /**
     * Posts a stock movement and updates on-hand balance (INV-MOV-001).
     * Enforces non-negative stock invariant (unless negative stock allowed).
     * Atomically posts to Universal Journal if valued (ARC-004).
     */
    fun postMovement(
        tenantId: UUID,
        companyId: UUID,
        plantId: UUID,
        storageLocationId: UUID,
        movementType: String, // e.g. 561 (Initial upload), 201 (Goods issue to cost center)
        materialCode: String,
        quantity: BigDecimal,
        uom: String,
        valuation: Money? = null,
        stockAccount: String = "130000",
        offsettingAccount: String = "520000",
        costCenterCode: String? = null,
        reference: String? = null
    ): UUID {
        var balance = balanceRepository.findByPlantIdAndStorageLocationIdAndMaterialCode(
            plantId, storageLocationId, materialCode
        )

        if (balance == null) {
            balance = StockBalance(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                plantId = plantId,
                storageLocationId = storageLocationId,
                materialCode = materialCode,
                quantityOnHand = BigDecimal.ZERO,
                uom = uom
            )
        }

        // Determine stock direction (positive or negative)
        val newQuantity = balance.quantityOnHand.add(quantity)
        checkInvariant("INV-INV-1", newQuantity >= BigDecimal.ZERO) {
            "Stock quantity cannot be negative for $materialCode in plant $plantId. Attempted: $newQuantity"
        }

        balance.quantityOnHand = newQuantity
        balance.updatedAt = Instant.now()
        balanceRepository.save(balance)

        val movementId = UUID.randomUUID()
        val movement = StockMovement(
            id = movementId,
            tenantId = tenantId,
            plantId = plantId,
            storageLocationId = storageLocationId,
            movementType = movementType,
            materialCode = materialCode,
            quantity = quantity,
            uom = uom,
            valuationAmount = valuation?.amount,
            currency = valuation?.currency,
            reference = reference
        )
        movementRepository.save(movement)

        // Post accounting if valued
        if (valuation != null && !valuation.isZero()) {
            val isDebitToStock = quantity > BigDecimal.ZERO
            val lines = listOf(
                JournalLineDraft(
                    lineNumber = 1,
                    accountCode = stockAccount,
                    side = if (isDebitToStock) PostingSide.DEBIT else PostingSide.CREDIT,
                    amount = valuation,
                    costCenterCode = if (!isDebitToStock) costCenterCode else null,
                    lineText = "Stock movement $movementType: $materialCode"
                ),
                JournalLineDraft(
                    lineNumber = 2,
                    accountCode = offsettingAccount,
                    side = if (isDebitToStock) PostingSide.CREDIT else PostingSide.DEBIT,
                    amount = valuation,
                    costCenterCode = if (isDebitToStock) null else costCenterCode,
                    lineText = "Offsetting entry for movement $movementType"
                )
            )

            val postReq = AccountingPostingRequest(
                tenantId = tenantId,
                companyId = companyId,
                documentType = "STOCK_MOVEMENT",
                postingDate = LocalDate.now(),
                documentDate = LocalDate.now(),
                reference = reference ?: "MVMT-$movementType",
                sourceModule = "INV_MOV",
                sourceDocumentId = movementId,
                lines = lines
            )
            accountingGateway.post(postReq)
        }

        return movementId
    }
}
