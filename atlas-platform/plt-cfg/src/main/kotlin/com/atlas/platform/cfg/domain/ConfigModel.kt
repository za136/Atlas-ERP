package com.atlas.platform.cfg.domain

import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "config_entries", schema = "plt_cfg")
class ConfigEntry(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "category", nullable = false, length = 50)
    val category: String,

    @Column(name = "config_key", nullable = false, length = 100)
    val configKey: String,

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    var configValue: String,

    @Column(name = "version", nullable = false)
    var version: Int = 1,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Repository
interface ConfigEntryRepository : JpaRepository<ConfigEntry, UUID> {
    fun findByCategoryAndConfigKey(category: String, configKey: String): ConfigEntry?
}

@Service
@Transactional
class ConfigurationService(
    private val repository: ConfigEntryRepository
) {
    fun get(category: String, key: String): String? =
        repository.findByCategoryAndConfigKey(category, key)?.configValue

    fun set(tenantId: UUID, category: String, key: String, value: String) {
        val existing = repository.findByCategoryAndConfigKey(category, key)
        if (existing != null) {
            existing.configValue = value
            existing.version += 1
            existing.updatedAt = Instant.now()
            repository.save(existing)
        } else {
            val entry = ConfigEntry(
                id = UUID.randomUUID(),
                tenantId = tenantId,
                category = category,
                configKey = key,
                configValue = value
            )
            repository.save(entry)
        }
    }
}
