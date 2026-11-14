package com.atlas.platform.aud.domain

import com.atlas.platform.iam.domain.PrincipalType
import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_events", schema = "plt_aud")
class AuditEvent(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "entity_type", nullable = false, length = 50)
    val entityType: String,

    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,

    @Column(name = "action", nullable = false, length = 30)
    val action: String,

    @Column(name = "actor_id", nullable = false)
    val actorId: UUID,

    @Column(name = "actor_name", nullable = false, length = 100)
    val actorName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    val actorType: PrincipalType,

    @Column(name = "payload_before", columnDefinition = "TEXT")
    val payloadBefore: String? = null,

    @Column(name = "payload_after", columnDefinition = "TEXT")
    val payloadAfter: String? = null,

    @Column(name = "previous_hash", nullable = false, length = 64)
    val previousHash: String,

    @Column(name = "entry_hash", nullable = false, length = 64)
    val entryHash: String,

    @Column(name = "timestamp", nullable = false)
    val timestamp: Instant = Instant.now()
)

@Repository
interface AuditEventRepository : JpaRepository<AuditEvent, UUID> {
    fun findTopByTenantIdOrderByTimestampDesc(tenantId: UUID): AuditEvent?
    fun findByEntityIdOrderByTimestampAsc(entityId: UUID): List<AuditEvent>
}

@Service
@Transactional
class AuditStoreService(
    private val auditEventRepository: AuditEventRepository
) {
    fun recordEvent(
        tenantId: UUID,
        entityType: String,
        entityId: UUID,
        action: String,
        actorId: UUID,
        actorName: String,
        actorType: PrincipalType,
        payloadBefore: String? = null,
        payloadAfter: String? = null
    ): AuditEvent {
        val lastEvent = auditEventRepository.findTopByTenantIdOrderByTimestampDesc(tenantId)
        val prevHash = lastEvent?.entryHash ?: "0000000000000000000000000000000000000000000000000000000000000000"

        val eventId = UUID.randomUUID()
        val now = Instant.now()

        val rawDataToHash = "$prevHash:$tenantId:$entityType:$entityId:$action:$actorId:$actorType:$payloadAfter:$now"
        val entryHash = sha256(rawDataToHash)

        val event = AuditEvent(
            id = eventId,
            tenantId = tenantId,
            entityType = entityType,
            entityId = entityId,
            action = action,
            actorId = actorId,
            actorName = actorName,
            actorType = actorType,
            payloadBefore = payloadBefore,
            payloadAfter = payloadAfter,
            previousHash = prevHash,
            entryHash = entryHash,
            timestamp = now
        )

        return auditEventRepository.save(event)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
