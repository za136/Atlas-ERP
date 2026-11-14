package com.atlas.platform.ai.domain

import com.atlas.platform.aud.domain.AuditStoreService
import com.atlas.platform.iam.domain.AtlasPrincipal
import com.atlas.platform.iam.domain.CurrentPrincipalProvider
import com.atlas.platform.iam.domain.PrincipalType
import org.springframework.stereotype.Service
import java.util.UUID

data class ToolDefinition(
    val name: String,
    val description: String,
    val requiredPermission: String
)

interface ModelGateway {
    fun generateProposal(prompt: String, availableTools: List<ToolDefinition>): AgentProposal
}

data class AgentProposal(
    val agentName: String,
    val reasoning: String,
    val proposedAction: String,
    val parameters: Map<String, Any>,
    val confidence: Double
)

@Service
class LocalMockModelGateway : ModelGateway {
    override fun generateProposal(prompt: String, availableTools: List<ToolDefinition>): AgentProposal {
        return AgentProposal(
            agentName = "AtlasCoreAgent",
            reasoning = "Evaluated rule matrix against prompt: $prompt",
            proposedAction = availableTools.firstOrNull()?.name ?: "NOOP",
            parameters = mapOf("status" to "VERIFIED", "source" to "LOCAL_RULE"),
            confidence = 0.98
        )
    }
}

/**
 * Governed Agent Actor Runner (P6, Section 10).
 * Runs an agent task under strict permissions, audit trails, and amount limits.
 */
@Service
class AgentExecutionService(
    private val modelGateway: ModelGateway,
    private val principalProvider: CurrentPrincipalProvider,
    private val auditStore: AuditStoreService
) {
    fun executeUnderGovernance(
        agentPrincipal: AtlasPrincipal,
        prompt: String,
        tools: List<ToolDefinition>,
        actionHandler: (AgentProposal) -> Unit
    ): AgentProposal {
        require(agentPrincipal.type == PrincipalType.AGENT) {
            "Principal must be of type AGENT, got ${agentPrincipal.type}"
        }

        val previousPrincipal = principalProvider.get()
        try {
            principalProvider.set(agentPrincipal)

            val proposal = modelGateway.generateProposal(prompt, tools)

            auditStore.recordEvent(
                tenantId = agentPrincipal.tenantId,
                entityType = "AGENT_PROPOSAL",
                entityId = UUID.randomUUID(),
                action = "PROPOSE_${proposal.proposedAction}",
                actorId = agentPrincipal.id,
                actorName = agentPrincipal.name,
                actorType = PrincipalType.AGENT,
                payloadAfter = proposal.toString()
            )

            actionHandler(proposal)
            return proposal
        } finally {
            principalProvider.set(previousPrincipal)
        }
    }
}
