package com.atlas.platform.common.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Interface for decoupled time resolution (Decision D-14).
 * UTC instants for events, business dates for postings.
 */
interface TimeProvider {
    fun now(): Instant
    fun today(zone: ZoneOffset = ZoneOffset.UTC): LocalDate

    companion object {
        fun utc(): TimeProvider = DefaultUtcTimeProvider
        fun fixed(fixedInstant: Instant): TimeProvider = FixedTimeProvider(fixedInstant)
    }
}

private object DefaultUtcTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun today(zone: ZoneOffset): LocalDate = LocalDate.now(Clock.system(zone))
}

private class FixedTimeProvider(private val instant: Instant) : TimeProvider {
    override fun now(): Instant = instant
    override fun today(zone: ZoneOffset): LocalDate = LocalDate.ofInstant(instant, zone)
}

/**
 * Custom domain exception for violated business or accounting invariants.
 */
class InvariantViolationException(
    val invariantId: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException("[$invariantId] Invariant violated: $message", cause)

inline fun checkInvariant(invariantId: String, condition: Boolean, lazyMessage: () -> String) {
    if (!condition) {
        throw InvariantViolationException(invariantId, lazyMessage())
    }
}
