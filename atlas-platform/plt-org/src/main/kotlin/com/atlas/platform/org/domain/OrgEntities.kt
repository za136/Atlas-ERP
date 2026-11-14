package com.atlas.platform.org.domain

import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "companies", schema = "plt_org")
class Company(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "code", nullable = false, length = 10)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Column(name = "country", nullable = false, length = 2)
    val country: String,

    @Column(name = "fiscal_variant", nullable = false, length = 10)
    val fiscalVariant: String = "STANDARD",

    @Column(name = "valid_from", nullable = false)
    val validFrom: LocalDate = LocalDate.of(2000, 1, 1),

    @Column(name = "valid_to", nullable = false)
    val validTo: LocalDate = LocalDate.of(9999, 12, 31),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "plants", schema = "plt_org")
class Plant(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    val company: Company,

    @Column(name = "code", nullable = false, length = 10)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "country", nullable = false, length = 2)
    val country: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "storage_locations", schema = "plt_org")
class StorageLocation(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plant_id", nullable = false)
    val plant: Plant,

    @Column(name = "code", nullable = false, length = 10)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String
)

@Entity
@Table(name = "cost_centers", schema = "plt_org")
class CostCenter(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    val company: Company,

    @Column(name = "code", nullable = false, length = 20)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "profit_center_id")
    var profitCenterId: UUID? = null,

    @Column(name = "valid_from", nullable = false)
    val validFrom: LocalDate = LocalDate.of(2000, 1, 1),

    @Column(name = "valid_to", nullable = false)
    val validTo: LocalDate = LocalDate.of(9999, 12, 31)
)

@Entity
@Table(name = "profit_centers", schema = "plt_org")
class ProfitCenter(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    val company: Company,

    @Column(name = "code", nullable = false, length = 20)
    val code: String,

    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Column(name = "segment", length = 30)
    var segment: String? = null
)
