package com.atlas.platform.iam.domain

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CurrentPrincipalProvider {
    private val principalHolder = ThreadLocal<AtlasPrincipal>()
    private val tenantHolder = ThreadLocal<UUID>()

    fun get(): AtlasPrincipal {
        val base = principalHolder.get() ?: AtlasPrincipal(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            tenantId = tenantHolder.get() ?: UUID.fromString("00000000-0000-0000-0000-000000000001"),
            name = "system",
            type = PrincipalType.SERVICE,
            roles = setOf("ADMIN"),
            permissions = setOf("*")
        )
        val currentTenant = tenantHolder.get()
        return if (currentTenant != null && base.tenantId != currentTenant) {
            base.copy(tenantId = currentTenant)
        } else {
            base
        }
    }

    fun set(principal: AtlasPrincipal) {
        principalHolder.set(principal)
        tenantHolder.set(principal.tenantId)
    }

    fun setTenantId(tenantId: UUID) {
        tenantHolder.set(tenantId)
    }

    fun clear() {
        principalHolder.remove()
        tenantHolder.remove()
    }
}
