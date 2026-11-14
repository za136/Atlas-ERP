package com.atlas.platform.common.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable quantity with explicit Unit of Measure (UOM).
 * Enforces Decision D-13: Fixed-point decimals with unit of measure.
 */
data class Quantity(
    val value: BigDecimal,
    val uom: String
) : Comparable<Quantity> {

    init {
        require(uom.isNotBlank()) { "Unit of Measure (UOM) must not be blank" }
    }

    operator fun plus(other: Quantity): Quantity {
        requireSameUom(other)
        return Quantity(value.add(other.value), uom)
    }

    operator fun minus(other: Quantity): Quantity {
        requireSameUom(other)
        return Quantity(value.subtract(other.value), uom)
    }

    operator fun times(factor: BigDecimal): Quantity = Quantity(value.multiply(factor), uom)

    operator fun div(divisor: BigDecimal): Quantity {
        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Cannot divide quantity by zero" }
        return Quantity(value.divide(divisor, 4, RoundingMode.HALF_UP), uom)
    }

    fun isZero(): Boolean = value.compareTo(BigDecimal.ZERO) == 0

    fun isPositive(): Boolean = value.compareTo(BigDecimal.ZERO) > 0

    fun isNegative(): Boolean = value.compareTo(BigDecimal.ZERO) < 0

    override fun compareTo(other: Quantity): Int {
        requireSameUom(other)
        return value.compareTo(other.value)
    }

    private fun requireSameUom(other: Quantity) {
        require(uom.equals(other.uom, ignoreCase = true)) {
            "UOM mismatch: $uom != ${other.uom}"
        }
    }

    companion object {
        fun zero(uom: String): Quantity = Quantity(BigDecimal.ZERO.setScale(4), uom)

        fun of(value: String, uom: String): Quantity = Quantity(BigDecimal(value), uom)

        fun of(value: Long, uom: String): Quantity = Quantity(BigDecimal.valueOf(value), uom)

        fun of(value: BigDecimal, uom: String): Quantity = Quantity(value, uom)
    }
}
