package com.atlas.platform.common.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable monetary amount with explicit ISO currency.
 * Enforces Decision D-13: Fixed-point decimals, explicit currency, strict rounding.
 * Floating point operations are strictly prohibited.
 */
data class Money @JsonCreator constructor(
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("currency") val currency: String
) : Comparable<Money> {

    init {
        require(currency.isNotBlank()) { "Currency must not be blank" }
        require(currency.length == 3) { "Currency must be a 3-letter ISO code: $currency" }
    }

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount), currency).round()
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.subtract(other.amount), currency).round()
    }

    operator fun times(multiplicand: BigDecimal): Money {
        return Money(amount.multiply(multiplicand), currency).round()
    }

    operator fun times(multiplicand: Long): Money {
        return times(BigDecimal.valueOf(multiplicand))
    }

    operator fun div(divisor: BigDecimal): Money {
        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Cannot divide money by zero" }
        val scale = CurrencyRegistry.scaleFor(currency)
        return Money(amount.divide(divisor, scale, RoundingMode.HALF_UP), currency)
    }

    operator fun unaryMinus(): Money = Money(amount.negate(), currency).round()

    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0

    fun isPositive(): Boolean = amount.compareTo(BigDecimal.ZERO) > 0

    fun isNegative(): Boolean = amount.compareTo(BigDecimal.ZERO) < 0

    fun round(roundingMode: RoundingMode = RoundingMode.HALF_UP): Money {
        val scale = CurrencyRegistry.scaleFor(currency)
        return Money(amount.setScale(scale, roundingMode), currency)
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amount.compareTo(other.amount)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return currency.equals(other.currency, ignoreCase = true) &&
                amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int {
        var result = amount.stripTrailingZeros().hashCode()
        result = 31 * result + currency.uppercase().hashCode()
        return result
    }

    private fun requireSameCurrency(other: Money) {
        require(currency.equals(other.currency, ignoreCase = true)) {
            "Currency mismatch: $currency != ${other.currency}"
        }
    }

    companion object {
        fun zero(currency: String): Money = Money(BigDecimal.ZERO.setScale(CurrencyRegistry.scaleFor(currency)), currency)

        fun of(amount: String, currency: String): Money = Money(BigDecimal(amount), currency).round()

        fun of(amount: Long, currency: String): Money = Money(BigDecimal.valueOf(amount), currency).round()

        fun of(amount: BigDecimal, currency: String): Money = Money(amount, currency).round()
    }
}

object CurrencyRegistry {
    private val scales = mapOf(
        "USD" to 2,
        "EUR" to 2,
        "GBP" to 2,
        "CHF" to 2,
        "CAD" to 2,
        "AUD" to 2,
        "JPY" to 0,
        "BHD" to 3,
        "KWD" to 3
    )

    fun scaleFor(currency: String): Int = scales[currency.uppercase()] ?: 2
}
