# Atlas ERP — Agent Instructions

Please refer to the comprehensive [AGENTS.md](file:///home/zabedin/projects/Atlas/AGENTS.md) for full architectural guidelines, financial invariants, and spec-driven development standards.

### Quick Reference for Agents
- **Language**: Kotlin 2.0+ on Java 21 LTS
- **Framework**: Spring Boot 3.3.x + Spring Modulith 1.2.x
- **Core Invariant**: Debits equal credits per document and currency (`FIN-INV-1`)
- **Atomic Posting**: Operational document + accounting entry in a single transaction (`ARC-004`)
- **No Floating Point**: Use `Money` (wrapping `BigDecimal`) for all financial calculations (`D-13`)
- **Agent Governance**: Agents use `PrincipalType.AGENT` through the same APIs and audit logs as humans (`P6`)
