package com.atlas.platform.doc.domain

import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

enum class DocumentLifecycleState {
    DRAFT,
    RELEASED,
    POSTED,
    CLEARED,
    REVERSED,
    CANCELLED
}

@Entity
@Table(name = "document_flow_links", schema = "plt_doc")
class DocumentFlowLink(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "predecessor_type", nullable = false, length = 30)
    val predecessorType: String,

    @Column(name = "predecessor_id", nullable = false)
    val predecessorId: UUID,

    @Column(name = "successor_type", nullable = false, length = 30)
    val successorType: String,

    @Column(name = "successor_id", nullable = false)
    val successorId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "idempotency_records", schema = "plt_doc")
class IdempotencyRecord(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    val idempotencyKey: String,

    @Column(name = "resource_type", nullable = false, length = 50)
    val resourceType: String,

    @Column(name = "resource_id", nullable = false)
    val resourceId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "number_ranges", schema = "plt_doc")
class NumberRange(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "company_id", nullable = false)
    val companyId: UUID,

    @Column(name = "document_type", nullable = false, length = 20)
    val documentType: String,

    @Column(name = "prefix", nullable = false, length = 10)
    val prefix: String,

    @Column(name = "current_number", nullable = false)
    var currentNumber: Long = 0,

    @Version
    var version: Long = 0
)

@Repository
interface DocumentFlowLinkRepository : JpaRepository<DocumentFlowLink, UUID> {
    fun findByPredecessorId(predecessorId: UUID): List<DocumentFlowLink>
    fun findBySuccessorId(successorId: UUID): List<DocumentFlowLink>
}

@Repository
interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, UUID> {
    fun findByIdempotencyKey(key: String): IdempotencyRecord?
}

@Repository
interface NumberRangeRepository : JpaRepository<NumberRange, UUID> {
    fun findByCompanyIdAndDocumentType(companyId: UUID, documentType: String): NumberRange?
}

@Service
@Transactional
class DocumentFrameworkService(
    private val flowLinkRepository: DocumentFlowLinkRepository,
    private val numberRangeRepository: NumberRangeRepository,
    private val idempotencyRepository: IdempotencyRecordRepository
) {
    fun linkDocuments(tenantId: UUID, predType: String, predId: UUID, succType: String, succId: UUID) {
        val link = DocumentFlowLink(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            predecessorType = predType,
            predecessorId = predId,
            successorType = succType,
            successorId = succId
        )
        flowLinkRepository.save(link)
    }

    fun generateBusinessNumber(tenantId: UUID, companyId: UUID, docType: String, defaultPrefix: String): String {
        val range = numberRangeRepository.findByCompanyIdAndDocumentType(companyId, docType)
            ?: NumberRange(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                companyId = companyId,
                documentType = docType,
                prefix = defaultPrefix,
                currentNumber = 0
            )

        range.currentNumber += 1
        numberRangeRepository.save(range)
        return "${range.prefix}-${range.currentNumber.toString().padStart(6, '0')}"
    }

    fun checkIdempotency(key: String): IdempotencyRecord? =
        idempotencyRepository.findByIdempotencyKey(key)

    fun recordIdempotency(tenantId: UUID, key: String, resourceType: String, resourceId: UUID) {
        val record = IdempotencyRecord(
            id = UUID.randomUUID(),
            tenantId = tenantId,
            idempotencyKey = key,
            resourceType = resourceType,
            resourceId = resourceId
        )
        idempotencyRepository.save(record)
    }
}
