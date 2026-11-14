package com.atlas.platform.common.domain

import java.io.Serializable
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * Strongly typed Tenant identifier.
 */
@JvmInline
value class TenantId(val value: UUID) : Serializable {
    companion object {
        fun of(uuidString: String): TenantId = TenantId(UUID.fromString(uuidString))
        fun random(): TenantId = TenantId(UUIDv7.generate())
    }
}

/**
 * Generator for time-ordered UUIDv7 identifiers (Decision D-15).
 */
object UUIDv7 {
    private val random = SecureRandom()

    fun generate(timestamp: Instant = Instant.now()): UUID {
        val timeEpochMs = timestamp.toEpochMilli()
        val randomBytes = ByteArray(10)
        random.nextBytes(randomBytes)

        val msb = (timeEpochMs shl 16) or (0x7000L or ((randomBytes[0].toLong() and 0x0F) shl 8) or (randomBytes[1].toLong() and 0xFF))
        val lsb = (0x8000000000000000UL.toLong() or
                ((randomBytes[2].toLong() and 0x3F) shl 56) or
                ((randomBytes[3].toLong() and 0xFF) shl 48) or
                ((randomBytes[4].toLong() and 0xFF) shl 40) or
                ((randomBytes[5].toLong() and 0xFF) shl 32) or
                ((randomBytes[6].toLong() and 0xFF) shl 24) or
                ((randomBytes[7].toLong() and 0xFF) shl 16) or
                ((randomBytes[8].toLong() and 0xFF) shl 8) or
                (randomBytes[9].toLong() and 0xFF))

        return UUID(msb, lsb)
    }
}
