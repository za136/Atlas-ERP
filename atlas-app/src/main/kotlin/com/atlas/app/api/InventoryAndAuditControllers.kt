package com.atlas.app.api

import com.atlas.inventory.domain.InventoryService
import com.atlas.inventory.domain.StockBalance
import com.atlas.inventory.domain.StockBalanceRepository
import com.atlas.inventory.domain.StockMovement
import com.atlas.inventory.domain.StockMovementRepository
import com.atlas.platform.aud.domain.AuditEvent
import com.atlas.platform.aud.domain.AuditEventRepository
import com.atlas.platform.common.domain.Money
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class PostStockMovementDto(
    val tenantId: UUID,
    val companyId: UUID,
    val plantId: UUID,
    val storageLocationId: UUID,
    val movementType: String,
    val materialCode: String,
    val quantity: BigDecimal,
    val uom: String,
    val valuationAmount: BigDecimal? = null,
    val currency: String? = null,
    val reference: String? = null
)

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory & Stock Movement API", description = "Stock ledger and valuation operations (INV)")
class InventoryController(
    private val inventoryService: InventoryService,
    private val balanceRepository: StockBalanceRepository,
    private val movementRepository: StockMovementRepository
) {
    @GetMapping("/balances")
    @Operation(summary = "List all stock balances")
    fun listBalances(): ResponseEntity<List<StockBalance>> {
        return ResponseEntity.ok(balanceRepository.findAll())
    }

    @GetMapping("/movements")
    @Operation(summary = "List all stock movements")
    fun listMovements(): ResponseEntity<List<StockMovement>> {
        return ResponseEntity.ok(movementRepository.findAll())
    }

    @PostMapping("/movements")
    @Operation(summary = "Post a stock movement (e.g. 561 upload, 201 consumption) with atomic accounting")
    fun postMovement(@RequestBody dto: PostStockMovementDto): ResponseEntity<Map<String, Any>> {
        val valuationMoney = if (dto.valuationAmount != null && dto.currency != null) {
            Money.of(dto.valuationAmount, dto.currency)
        } else null

        val movementId = inventoryService.postMovement(
            tenantId = dto.tenantId,
            companyId = dto.companyId,
            plantId = dto.plantId,
            storageLocationId = dto.storageLocationId,
            movementType = dto.movementType,
            materialCode = dto.materialCode,
            quantity = dto.quantity,
            uom = dto.uom,
            valuation = valuationMoney,
            reference = dto.reference
        )
        return ResponseEntity.ok(mapOf("movementId" to movementId, "status" to "POSTED"))
    }
}

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit & Tamper-Evident Trail API", description = "Audit trail queries (PLT-AUD)")
class AuditController(
    private val auditEventRepository: AuditEventRepository
) {
    @GetMapping("/events/{entityId}")
    @Operation(summary = "Retrieve tamper-evident audit history for a given business entity")
    fun getEntityAuditTrail(@PathVariable entityId: UUID): ResponseEntity<List<AuditEvent>> {
        val events = auditEventRepository.findByEntityIdOrderByTimestampAsc(entityId)
        return ResponseEntity.ok(events)
    }
}
