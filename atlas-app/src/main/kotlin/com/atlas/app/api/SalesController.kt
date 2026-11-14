package com.atlas.app.api

import com.atlas.platform.common.domain.Money
import com.atlas.platform.common.domain.Quantity
import com.atlas.sales.domain.SalesOrder
import com.atlas.sales.domain.SalesOrderRepository
import com.atlas.sales.domain.SalesService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.util.UUID

data class CreateSoItemDto(
    val materialCode: String,
    val quantity: BigDecimal,
    val uom: String,
    val unitPrice: BigDecimal,
    val currency: String
)

data class CreateSoRequestDto(
    val tenantId: UUID,
    val companyId: UUID,
    val customerId: UUID,
    val items: List<CreateSoItemDto>
)

data class PostGiRequestDto(
    val lineNumber: Int,
    val issuedQuantity: BigDecimal,
    val valuationCostPerUnit: BigDecimal,
    val currency: String
)

data class PostBillingRequestDto(
    val lineNumber: Int,
    val billedQuantity: BigDecimal
)

@RestController
@RequestMapping("/api/v1/sales")
@Tag(name = "Sales & Order-to-Cash API", description = "Order-to-Cash operational flows (SLS)")
class SalesController(
    private val salesService: SalesService,
    private val salesOrderRepository: SalesOrderRepository
) {
    @GetMapping("/orders")
    @Operation(summary = "List all Sales Orders")
    fun listSalesOrders(): ResponseEntity<List<SalesOrder>> {
        return ResponseEntity.ok(salesOrderRepository.findAll())
    }

    @PostMapping("/orders")
    @Operation(summary = "Create and confirm a Sales Order")
    fun createSalesOrder(@RequestBody dto: CreateSoRequestDto): ResponseEntity<SalesOrder> {
        val items = dto.items.map {
            it.materialCode to (Quantity.of(it.quantity, it.uom) to Money.of(it.unitPrice, it.currency))
        }
        val order = salesService.createSalesOrder(dto.tenantId, dto.companyId, dto.customerId, items)
        return ResponseEntity.ok(order)
    }

    @PostMapping("/orders/{id}/delivery")
    @Operation(summary = "Post Delivery & Goods Issue with atomic COGS and stock reduction")
    fun postGoodsIssue(
        @PathVariable id: UUID,
        @RequestBody dto: PostGiRequestDto
    ): ResponseEntity<Map<String, Any>> {
        val deliveryId = salesService.postGoodsIssue(
            orderId = id,
            lineNumber = dto.lineNumber,
            issuedQuantity = dto.issuedQuantity,
            valuationCostPerUnit = Money.of(dto.valuationCostPerUnit, dto.currency)
        )
        return ResponseEntity.ok(mapOf("deliveryId" to deliveryId, "status" to "DELIVERED"))
    }

    @PostMapping("/orders/{id}/billing")
    @Operation(summary = "Post Billing Document with atomic revenue recognition and AR entry")
    fun postBilling(
        @PathVariable id: UUID,
        @RequestBody dto: PostBillingRequestDto
    ): ResponseEntity<Map<String, Any>> {
        val invoiceId = salesService.postBillingDocument(
            orderId = id,
            lineNumber = dto.lineNumber,
            billedQuantity = dto.billedQuantity
        )
        return ResponseEntity.ok(mapOf("billingDocumentId" to invoiceId, "status" to "BILLED"))
    }
}
