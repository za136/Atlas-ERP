<!-- Identical in AGENTS.md, CLAUDE.md and GEMINI.md. Edit AGENTS.md, then: cp AGENTS.md CLAUDE.md && cp AGENTS.md GEMINI.md -->
# Read this first (ZAIN LABS registry header)

1. This file. 2. `HANDOFF.md` (status, next actions). 3. `Atlas ERP — Product Requirements and Specification.md`.

**Status note (2026-11-26, owner decision):** Atlas is **paused at PRD stage**. Pilots use **ERPNext** as the ERP
core; Atlas becomes the home for DACH-specific modules on top of ERPNext when a customer asks for one. Do not
start large new Atlas modules without the owner's go.

## Owner context
- Solo developer (Zain). Target market: DACH (Germany, Austria, Switzerland). Code, comments and docs in English.
- Budget for tools is EUR 0: prefer free and open-source; flag any paid service before adding it.
- Main dev machine: Lenovo i5-7300U / 16 GB on Linux. No Mac.
- This repo is project P02 in the ZAIN LABS registry `za136/factory-hq` (`projects.toml`, `HANDOFF.md`,
  `docs/projects/P02-atlas-erp.md`). Ecosystem guide with diagrams: `za136/factory-hq` → `docs/guide/README.md`.

## Working rules (all repos)
- Small, reviewable steps. End each step with: decisions, assumptions, risks, artifacts, next step.
- Tests with every change; tests stay offline (no real API calls).
- Ask the owner before anything that sends messages, spends money, deletes data or publishes publicly.
- Never commit secrets; document variables in `.env.example`.
- **Before you stop:** update `HANDOFF.md` (Current status, Next actions, Session log) and, if the status changed,
  the P02 stub in `za136/factory-hq`.
- Git: `main` stays demo-able; feature branches `feat/*`, `fix/*`; conventional commits.

---

# Atlas ERP — Agent Constitution & Implementation Guidelines

*Project Instructions for AI Agents in Google Antigravity. Version 1.0 (November 2026).*

---

## 1. System Mission & Core Principles

**Atlas** is an enterprise-class, modular, cloud-native ERP platform matching SAP S/4HANA core business processes. It is built under **Spec-Driven Development** by AI coding agents within Google Antigravity.

### The Four Architectural Bets

1. **Single Append-Only Universal Journal (DAT-001, P1)**: The ledger is the single source of financial truth. All operational modules post to it; all subledgers reconcile to it by construction. Posted documents are permanently immutable. Corrections are executed solely via reversal documents.
2. **Clean Core, Open Edges (P3)**: Standard behavior is never mutated with customer hacks. Customization is strictly declarative through versioned configuration (`plt-cfg`), rules tables, extension points, and side-by-side apps.
3. **Agent-Native Architecture (P6, Section 10)**: AI agents are first-class actors (`PrincipalType.AGENT`) using the exact same APIs, permission checks (`@PreAuthorize`), and tamper-evident audit trails (`plt-aud`) as human users. There are no backdoor shortcuts.
4. **Spec-First Verification (P8)**: If a requirement or invariant cannot be automatically verified through deterministic unit/integration tests, it is not complete.

---

## 2. Technology Stack & Runtime Standards

| Component | Standard / Selection | Reference |
| :--- | :--- | :--- |
| **Language & SDK** | Kotlin 2.0+ on Java 21 LTS (Gradle Kotlin DSL) | Decision D-02 |
| **Framework** | Spring Boot 3.3.x & Spring Modulith 1.2.x | Decision D-01 |
| **Persistence** | Hybrid: Spring Data JPA (Hibernate 6) + jOOQ (Typesafe SQL) | Decision D-03 |
| **Database** | PostgreSQL 16+ with multi-schema partitioning | Decision D-03 |
| **Tenancy Isolation** | Shared DB + `tenant_id` + Postgres Row-Level Security (RLS) + `@TenantId` | Decision D-04 |
| **Monetary Arithmetic** | Strict fixed-point `BigDecimal` via `Money` value object (NO float/double) | Decision D-13 |
| **Identifiers** | Time-ordered UUIDv7 + governed human-readable business numbers | Decision D-15 |
| **Audit Log** | Tamper-evident append-only store with SHA-256 hash chaining | PLT-AUD-001 |

---

## 3. Strict Non-Negotiable Invariants

Every agent generating code or tests MUST strictly adhere to and preserve these invariants:

### Financial Invariants
- **`FIN-INV-1` (Debits equal Credits)**: Every journal entry MUST balance per document, per ledger, and per currency (`sum(Debits) == sum(Credits)`).
- **`FIN-INV-2` (Subledger Reconciliation)**: For AP and AR, the sum of open items (`is_cleared = false`) MUST reconcile to the reconciliation account balance at all times.
- **`FIN-INV-3` (Trial Balance Integrity)**: The trial balance for any period equals the sum of its posted line items.
- **`FIN-INV-4` (Cryptographic Immutability)**: A posted document's content hash (SHA-256) is recorded at posting time and must never alter.
- **`FIN-INV-5` (Fiscal Period Gate)**: Postings are rejected if the target period is closed, unless authorized for special year-end periods.

### Architectural Rules
- **`ARC-001` (Boundary Enforcement)**: Operational modules access data only through published module interfaces (`:*-api`) or domain events. Cross-schema foreign keys or ad-hoc joins are strictly forbidden.
- **`ARC-002` (Unidirectional Dependency Flow)**: Dependencies flow: `Platform` -> `Master Data` -> `Operational Modules` (`PRC`, `SLS`, `INV`) -> `Accounting Interface` (`fin-accounting-api`). Finance NEVER calls operational modules to complete a posting.
- **`ARC-004` (Atomic Posting Rule)**: Operational documents (Goods Receipt, Goods Issue, Billing, Invoices) and their corresponding accounting entries MUST commit within the exact same database transaction (`@Transactional`), or neither commits.

---

## 4. Module Map & Architecture Layout

```
Atlas/
├── atlas-platform/
│   ├── plt-common/         # Core value objects: Money, Quantity, TenantId, UUIDv7, Invariants
│   ├── plt-org/            # Organization hierarchy: Company, Plant, StorageLocation, CostCenter
│   ├── plt-iam/            # Security: AtlasPrincipal, PrincipalType (HUMAN, AGENT), SoD rules
│   ├── plt-doc/            # Document Framework: Flow links, Number ranges, Idempotency
│   ├── plt-aud/            # Audit Store: Tamper-evident hash-chained audit events
│   ├── plt-cfg/            # Versioned business policy configuration
│   └── plt-ai/             # Decoupled Model Gateway & Governed Agent Actor Runner
│
├── atlas-finance/
│   ├── fin-accounting-api/ # Public Accounting Gateway contracts (PostingRequest, JournalLineDraft)
│   ├── fin-accounting-core/# Universal Journal (header/lines), Invariant engine, jOOQ reporting
│   ├── fin-gl/             # General Ledger, Chart of Accounts, Fiscal Periods
│   ├── fin-ap/             # Accounts Payable, Supplier Invoices, Open Items, Payment runs
│   └── fin-ar/             # Accounts Receivable, Customer Invoices, Open Items, Cash application
│
├── atlas-procurement/      # Requisition, PO, Goods Receipt, 3-Way Match (MM-PUR)
├── atlas-sales/            # Sales Order, Delivery / Goods Issue, Billing Document (SD)
├── atlas-inventory/        # Stock movement ledger, on-hand balances, valuation (MM-IM)
│
└── atlas-app/              # Spring Boot Runner, REST APIs, Flyway migrations, OpenAPI UI
```

---

## 5. Agent Workflow & Development Protocol

1. **Do Not Break Invariants**: Any PR or patch that causes `FIN-INV-1` or `ARC-004` to fail will fail the CI/CD pipeline immediately.
2. **Schema Changes**:
   - Place all DDL in `atlas-app/src/main/resources/db/migration/`.
   - Always include `tenant_id UUID NOT NULL` on new transactional tables.
   - Add the table to the Row-Level Security loop in Flyway scripts.
3. **Domain Event Publication**:
   - For inter-module side effects that do not involve atomic ledger posting, emit Spring Modulith domain events (`@ApplicationModuleListener`).
4. **Testing Standards**:
   - Run `./gradlew test` to execute JUnit 5 and Spring Modulith boundary verification tests.
   - Always write property-based or invariant test cases for new transactional paths.
