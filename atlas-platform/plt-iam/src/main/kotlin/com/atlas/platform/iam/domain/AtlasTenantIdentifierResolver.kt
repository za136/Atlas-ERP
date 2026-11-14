package com.atlas.platform.iam.domain

import org.hibernate.cfg.AvailableSettings
import org.hibernate.context.spi.CurrentTenantIdentifierResolver
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AtlasTenantIdentifierResolver(
    private val principalProvider: CurrentPrincipalProvider
) : CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    override fun resolveCurrentTenantIdentifier(): UUID {
        return principalProvider.get().tenantId
    }

    override fun validateExistingCurrentSessions(): Boolean {
        return true
    }

    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        hibernateProperties[AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER] = this
    }
}
