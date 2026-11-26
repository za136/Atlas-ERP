# P02 · Atlas ERP: Handoff

> Factory: F1 · Status: PRD written · paused (ERPNext is the pilot ERP) · Last updated: 2026-11-26 by Claude

## Summary
Modular ERP for small and mid-sized businesses: finance (GL, AR, AP), inventory, procurement and sales as Spring
Modulith modules on a shared platform layer (IAM, audit, config, documents, AI), with a single append-only
Universal Journal. Kotlin 2 on Java 21, Spring Boot 3.3, PostgreSQL 16.

## Current status
Modules exist: `atlas-platform` (`plt-*`), `atlas-finance` (`fin-*`), `atlas-inventory`, `atlas-procurement`,
`atlas-sales`, `atlas-app`. Last commits: "complete Atlas ERP modular monolith with Universal Journal and Web
Portal" and a trial-balance fix. `./gradlew build` was NOT run in the 2026-11-26 session.

## Where things are
- PRD: `Atlas ERP — Product Requirements and Specification.md`; agent constitution: `AGENTS.md`.
- Registry stub: `za136/factory-hq` → `docs/projects/P02-atlas-erp.md`. Drive: "Atlas ERP — Product Requirements and Specification".

## How to run
```bash
./gradlew build
./gradlew :atlas-app:bootRun
```

## How to test
`./gradlew test` (JDK 21 required).

## Decisions
- D-01…D-15 in `AGENTS.md` §2 (Spring Modulith, Kotlin, JPA + jOOQ, Postgres RLS tenancy, Money as BigDecimal, UUIDv7).
- 2026-11-26 · Owner decision (delegated) · Extend ERPNext instead of building a full ERP; Atlas paused at PRD stage.
- 2026-11-26 · Claude · agent files unified (`CLAUDE.md`, `GEMINI.md` copies), broken local link in `agent.md` fixed, this handoff added.

## Open questions and blockers
- Which DACH-specific gaps in ERPNext are worth an Atlas module (first customer decides).

## Next actions
1. Run `./gradlew build test` on the Lenovo (JDK 21) and record the result here.
2. Map the PRD modules to ERPNext doctypes and list the DACH gaps (tracker T008).

## Session log
- 2026-11 · Gemini (Antigravity) · modular monolith, Universal Journal, web portal.
- 2026-11-26 · Claude · registry header, agent files, handoff · no build run.
