package com.atlas.platform.iam.domain

import jakarta.persistence.*
import org.hibernate.annotations.TenantId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PrincipalType {
    HUMAN,
    SERVICE,
    AGENT
}

/**
 * Representation of the caller identity in Atlas.
 * Enforces Principle P6: Agents are actors, not backdoors.
 */
data class AtlasPrincipal(
    val id: UUID,
    val tenantId: UUID,
    val name: String,
    val type: PrincipalType,
    val roles: Set<String>,
    val permissions: Set<String>,
    val amountLimit: BigDecimal? = null
) {
    fun canPostAmount(amount: BigDecimal): Boolean {
        if (amountLimit == null) return true
        return amount <= amountLimit
    }

    fun hasPermission(permission: String): Boolean =
        permissions.contains(permission) || permissions.contains("*")
}

@Entity
@Table(name = "users", schema = "plt_iam")
class UserAccount(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "username", nullable = false, unique = true, length = 50)
    val username: String,

    @Column(name = "full_name", nullable = false, length = 100)
    var fullName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 20)
    val principalType: PrincipalType,

    @Column(name = "amount_limit")
    var amountLimit: BigDecimal? = null,

    @Column(name = "active", nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "roles", schema = "plt_iam")
class Role(
    @Id
    val id: UUID,

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "name", nullable = false, length = 50)
    val name: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", schema = "plt_iam", joinColumns = [JoinColumn(name = "role_id")])
    @Column(name = "permission", nullable = false)
    val permissions: MutableSet<String> = mutableSetOf()
)

/**
 * Segregation of Duties (SoD) rule definition (PLT-IAM-003).
 */
data class SodRule(
    val code: String,
    val description: String,
    val conflictingPermissions: Set<String>
)

object SodValidator {
    private val rules = listOf(
        SodRule(
            code = "SOD-01",
            description = "Cannot both create supplier invoice and approve supplier payment",
            conflictingPermissions = setOf("prc:invoice:create", "fin:payment:approve")
        ),
        SodRule(
            code = "SOD-02",
            description = "Cannot both enter manual journal and approve period close",
            conflictingPermissions = setOf("fin:gl:post_manual", "fin:close:approve")
        )
    )

    fun checkConflicts(permissions: Set<String>): List<SodRule> {
        return rules.filter { rule ->
            permissions.containsAll(rule.conflictingPermissions)
        }
    }
}
