package com.atlas.app.api

import com.atlas.platform.common.domain.Money
import com.atlas.platform.common.domain.Quantity
import com.atlas.procurement.domain.ProcurementService
import com.atlas.procurement.domain.PurchaseOrder
import com.atlas.procurement.domain.PurchaseOrderRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class CreatePoItemDto(
    val materialCode: String,
    val quantity: BigDecimal,
    val uom: String,
    val unitPrice: BigDecimal,
    val currency: String
)

data class CreatePoRequestDto(
    val tenantId: UUID,
    val companyId: UUID,
    val supplierId: UUID,
    val items: List<CreatePoItemDto>
)

data class PostGrRequestDto(
    val lineNumber: Int,
    val receivedQuantity: BigDecimal
)

data class MatchInvoiceRequestDto(
    val supplierInvoiceNumber: String,
    val amount: BigDecimal,
    val currency: String
)

@RestController
@RequestMapping("/api/v1/procurement")
@Tag(name = "Procurement API", description = "Source-to-Pay operational flows (PRC)")
class ProcurementController(
    private val procurementService: ProcurementService,
    private val purchaseOrderRepository: PurchaseOrderRepository
) {
    @GetMapping("/orders")
    @Operation(summary = "List all Purchase Orders")
    fun listPurchaseOrders(): ResponseEntity<List<PurchaseOrder>> {
        return ResponseEntity.ok(purchaseOrderRepository.findAll())
    }

    @PostMapping("/orders")
    @Operation(summary = "Create an approved Purchase Order")
    fun createPurchaseOrder(@RequestBody dto: CreatePoRequestDto): ResponseEntity<PurchaseOrder> {
        val items = dto.items.map {
            it.materialCode to (Quantity.of(it.quantity, it.uom) to Money.of(it.unitPrice, it.currency))
        }
        val po = procurementService.createPurchaseOrder(dto.tenantId, dto.companyId, dto.supplierId, items)
        return ResponseEntity.ok(po)
    }

    @PostMapping("/orders/{id}/receipt")
    @Operation(summary = "Post Goods Receipt with atomic stock and GR/IR accounting")
    fun postGoodsReceipt(
        @PathVariable id: UUID,
        @RequestBody dto: PostGrRequestDto
    ): ResponseEntity<Map<String, Any>> {
        val receiptId = procurementService.postGoodsReceipt(id, dto.lineNumber, dto.receivedQuantity)
        return ResponseEntity.ok(mapOf("goodsReceiptId" to receiptId, "status" to "POSTED"))
    }

    @PostMapping("/orders/{id}/invoice")
    @Operation(summary = "Execute 3-way invoice match, clearing GR/IR and posting to AP")
    fun matchInvoice(
        @PathVariable id: UUID,
        @RequestBody dto: MatchInvoiceRequestDto
    ): ResponseEntity<Map<String, Any>> {
        val invoiceId = procurementService.matchAndPostInvoice(
            id,
            dto.supplierInvoiceNumber,
            Money.of(dto.amount, dto.currency)
        )
        return ResponseEntity.ok(mapOf("invoiceId" to invoiceId, "status" to "MATCHED_AND_POSTED"))
    }
}
