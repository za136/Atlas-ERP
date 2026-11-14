# **Atlas ERP — Product Requirements and Specification**

*Working title. Version 0.1, draft for spec-driven implementation. Prepared 13 November 2026\.*

## **0\. How to read this document**

**Purpose.** This is the source of truth for an end-to-end, SAP-class ERP platform. It is written so that implementer agents in Google Antigravity can derive technical plans, task lists and code from it, and so that a human reviewer can check the result against it. It contains no code and deliberately little project planning.

**Conventions.**

* Requirement IDs follow AREA-NNN, for example FIN-GL-012. The words MUST, SHOULD and MAY carry their RFC 2119 meaning.  
* Every requirement carries a tier: T0 (foundation and core), T1 (operational depth) or T2 (advanced). Tiers are defined in section 4\.  
* Technology choices are written as Decisions (D-01 and so on). Each gives a recommended default, the alternatives, and the reason. An implementer may override a decision only by writing a decision record that explains why.  
* Research facts are labelled **Verified** (checked in November 2026 against public sources listed in the appendix), **Assumption** (reasonable but unconfirmed) or **Open** (needs a human decision).  
* “SAP analog” notes are for orientation only. Atlas does not copy SAP’s data model, table names, transaction codes or UI.

**Out of this document by design:** organisation, staffing, budget, schedule and source code.

## **1\. Executive summary**

**What.** Atlas is a modular, multi-tenant, cloud-native ERP that covers the same business scope as SAP S/4HANA’s lines of business: Finance, Sourcing and Procurement, Manufacturing, Supply Chain, Sales, Service, Asset Management, R\&D and Engineering, and Human Resources. AI agents are built in as first-class participants in business processes rather than added as a chat window on the side.

**Why.** Incumbent suites are powerful but expensive, slow to change and hard to extend without breaking upgrades. A new system designed around a single ledger, declarative configuration, strict module boundaries and governed AI agents can deliver the same process coverage with far less customization pain.

**How it will be built.** By AI coding agents inside Google Antigravity, driven by this document under spec-driven development: the specification is written and reviewed first, and agents generate plans, tasks and code from it. Two constraints shape the whole document. First, accounting must be correct, so correctness rules are specified as invariants that tests can check automatically. Second, agent capacity on a Google AI Pro plan is finite and metered, so every module spec is small, self-contained and independently buildable.

**Four bets.**

1. A single append-only journal is the heart of the system. Every module posts to it, and every subledger reconciles to it by construction.  
2. A clean core. Standard behaviour is never modified. Customer-specific needs are met by configuration, custom fields, rules, extension points and side-by-side apps.  
3. Agent-native. Agents call the same APIs under the same permissions and the same audit trail as people.  
4. Spec-first. If a requirement cannot be tested automatically, it is not finished.

**Honest scope statement.** SAP represents decades of work and many thousands of person-years. Atlas aims for complete process coverage at a depth that suits mid-market companies that make, buy, store, sell and service physical goods and services. It does not aim to match SAP’s industry solutions or its long tail of country-specific features. Tiers T0 to T2 make that trade-off explicit.

## **2\. Research findings**

### **2.1 What SAP’s current suite covers (Verified)**

* SAP S/4HANA is organised into lines of business. Sources list roughly ten: Finance, Sourcing and Procurement, Manufacturing, Supply Chain, Sales, Service, Asset Management, R\&D and Engineering, and Human Resources, with environment, health and safety often treated alongside.  
* SAP describes its public cloud edition as covering hundreds of business processes across the families order-to-cash, procure-to-pay, record-to-report, ideation-to-market and service excellence. Preconfigured scenarios, reported as 300 or more, are switched on through fit-to-standard workshops rather than built from scratch.  
* SAP’s learning material describes a finance-led “administrative ERP” core made of Finance, Sales, and Sourcing and Procurement. Operational lines of business such as manufacturing, warehouse and transportation are layered on when the company designs, produces and ships goods itself.  
* The classic module map (FI, CO, SD, MM, PP, QM, PM, PS, HCM, EHS, and others) remains the most useful checklist of functionality. This document uses it as a coverage checklist, not as a design.

**Implication.** Atlas is organised by lines of business and process loops, with a finance-led core first (tier T0) and operational depth after (T1, T2).

### **2.2 Where ERP and AI are heading (Verified)**

* SAP’s 2026 direction is to embed AI inside processes, data and governance rather than beside them. It announced more than fifty domain-specific Joule assistants that coordinate many underlying agents across finance, supply chain, procurement, HR and customer engagement. Several assistants arrive in phases through early 2027\.  
* The stated shift is from assistance to execution: agents trigger actions across applications. Commentary stresses that organisations should review what each assistant may change, process by process, before activating it, and that clean data and process foundations decide the results.  
* SAP’s AI platform draws on several foundation-model vendors. Oracle has made a similar agent move in its own suite.

**Implication.** Agents need identities, scoped permissions, approval thresholds, audit trails and evaluation harnesses from day one. Section 10 specifies this.

### **2.3 Build tooling: Antigravity and Google AI Pro**

Plan details and model lineups changed several times during 2026 and sources disagree on some points. Treat everything in this subsection as a snapshot to re-verify before relying on it.

| Topic | Finding | Status |
| ----- | ----- | ----- |
| Product surfaces | Antigravity now ships as a family: the Antigravity 2.0 desktop app (editor-free, parallel agents, projects, worktrees, dynamic subagents), the original VS Code–based IDE, a Go-based CLI called agy that replaced Gemini CLI, extensions for other editors, and a Python SDK. Google has said the Agent Manager inside the IDE will be removed in a future release because multi-agent work is moving to the desktop app. | Verified |
| Plans | There is no Antigravity-only subscription. A free tier has a weekly quota. Google AI Pro is about US\$19.99 per month with a larger quota refreshed every five hours up to a weekly cap, plus a pool of AI credits for overage. Ultra tiers are about US\$99.99 (5x Pro) and US\$199.99 (20x Pro). | Verified |
| Models on Pro | Gemini 3.x Flash variants and Gemini 3.1 Pro on all plans. Gemini 4 Argon reached paid Pro in mid-October 2026 with a separate weekly-only allowance. Claude Sonnet 5.5 and Opus 5.5 are available on paid (non-trial) Pro. GPT-OSS-120b is also listed. Gemini models and Claude/GPT models draw on separate quota pools. | Verified, volatile |
| Quota numbers | Google publishes no numeric quotas. Limits scale with the amount of work an agent does, not with the prompt count. Some third-party sources report weekly lockouts for heavy Pro users. | Assumption |
| Runtime billing | A Google AI Pro subscription pays for the developer tool. Running AI features inside the finished ERP will use a model API that is billed separately. | Assumption, verify |
| Customization | AGENTS.md gives project-wide instructions and takes priority over GEMINI.md. Rules live in .agents/rules with activation modes such as glob matching. Workflows are named multi-step sequences. Skills are folders with a SKILL.md that agents load on demand. There are also hooks, MCP servers, plugins, permissions, a sandbox and subagents. | Verified |
| Review artifacts | Agents produce Plan and Walkthrough artifacts, screenshots and browser recordings that a human can review and annotate. | Verified |

### **2.4 Spec-driven development (Verified)**

* GitHub’s open-source Spec Kit popularised a four-phase flow: specify, plan, tasks, implement, with a constitution of governing principles, an optional clarify step and an optional analyze step that checks consistency before coding. The specification, not the code, is the durable artefact.  
* Practitioners warn that verification at each checkpoint cannot be delegated to the same agent that produced the work, and that rigid specs can bring back heavy process if overdone.  
* Antigravity has no built-in spec-driven mode, but its primitives map cleanly: constitution becomes AGENTS.md and rules, specs become versioned documents in the repository, the Plan artifact becomes the review gate, and workflows become the repeatable pipeline. A community eight-phase pattern assigns one role and one artifact per phase and keeps a SPEC.md in the folder.

### **2.5 Design consequences for this document**

* **C1. Small, self-contained specs.** Each module spec (sections 7 and 8\) can be handed to an agent with only the foundation spec and its declared dependencies.  
* **C2. Model-tier assignment.** Reserve the strongest reasoning models for ledger logic, authorization, data migration and architecture. Use faster models for scaffolding, CRUD screens and test expansion. Section 14 gives hints.  
* **C3. Deterministic verification outside the model.** Accounting invariants, reconciliation checks and contract tests run as automated gates, not as agent opinions.  
* **C4. Quota-aware sequencing.** Work is cut into units that finish inside one five-hour window where possible.  
* **C5. No runtime dependence on the build tool.** The product reaches models only through a model-gateway interface (section 10), so the AI vendor can change.

## **3\. Product definition**

### **3.1 Vision**

A company can run its whole operation, from quote to cash, requisition to payment, forecast to production, hire to retire and plan to close, on one coherent system. A controller can trace any figure to its source document in two clicks. A functional consultant can adapt a process through configuration in hours. An AI agent can prepare a close, chase an invoice or replan a shortage, and the audit trail shows exactly what it did and who approved it.

### **3.2 Goals**

* **G1.** Cover every SAP S/4HANA line of business at the process level across tiers T0 to T2.  
* **G2.** Deliver five end-to-end loops: procure-to-pay, order-to-cash, record-to-report, plan-to-produce and hire-to-retire.  
* **G3.** Provide correct double-entry accounting with parallel ledgers, so statutory and management views coexist.  
* **G4.** Make agents first-class: at least ten routine processes (for example invoice matching, dunning, bank reconciliation, reorder proposals) can run under governed autonomy.  
* **G5.** Fit-to-standard: ship preconfigured scenario templates activated by configuration.  
* **G6.** Extend without forking: customer extensions survive upgrades.  
* **G7.** Run with a small operations team: automated upgrades, observability, zero-downtime releases.  
* **G8.** Be buildable by agents within metered quotas.

### **3.3 Non-goals**

* **N1.** No clone of SAP’s UI, tables or programming language.  
* **N2.** No industry solutions (utilities, oil and gas, banking, insurance, public sector) in this version.  
* **N3.** No on-premises appliance. Atlas is cloud-native and containerised; self-hosting is possible in principle but not a product goal.  
* **N4.** No payroll for every country at launch. Deliver the payroll framework and one or two reference country packs.  
* **N5.** No advanced optimisation solvers (full supply-network planning, carrier-network transportation) in T0 or T1.  
* **N6.** No partner marketplace in this version.  
* **N7.** No dependency on Antigravity or Google AI Pro at runtime.

### **3.4 Design principles**

* **P1. Ledger truth.** Every financial effect comes from a posted, immutable, balanced journal entry. Corrections are reversals, never edits.  
* **P2. One source of data.** One transactional store. No batch replication between modules. Operational reports read live data.  
* **P3. Clean core, open edges.** Standard behaviour is not modified. Customer needs use configuration, custom fields, rules, extension points and side-by-side apps.  
* **P4. Configuration over code.** Business policy (organisation, account determination, pricing, approvals, number ranges) lives in versioned configuration.  
* **P5. API-first and event-first.** Every capability has a documented API. Every state change emits a domain event.  
* **P6. Agents are actors, not backdoors.** Agents use the same APIs, authorizations and audit trail as people. There is no privileged path.  
* **P7. Explainable and auditable.** Every number traces to source documents. Every change records who, what, when and why.  
* **P8. Executable specs.** Every requirement has acceptance criteria that can be tested automatically.  
* **P9. Modular monolith first.** Module boundaries are enforced from day one. Physical decomposition into services happens only on measured need.  
* **P10. Localization is a plug-in.** Tax, e-invoicing, statutory reporting and payroll rules live in country packs on top of a country-neutral core.

### **3.5 Personas**

| Persona | Primary goals | Main surfaces |
| ----- | ----- | ----- |
| CFO and controller | Close fast, trust the numbers, control risk | Close cockpit, financial statements, approvals |
| Accountant | Post, reconcile, correct, report | Journal entry, AP/AR worklists, bank reconciliation |
| Buyer and procurement manager | Source well, control spend, manage suppliers | Requisition inbox, RFQ, PO and contract workspaces |
| Warehouse operator | Receive, put away, pick, pack, count | Mobile and scanner screens, task lists |
| Production planner and shop-floor lead | Plan, release, confirm, resolve shortages | Planning board, production order workspace |
| Sales representative and order administrator | Quote, order, deliver, bill, resolve exceptions | Quote and order workspaces, availability, credit status |
| HR administrator and payroll specialist | Maintain people data, run payroll accurately | Employee file, time, payroll cockpit |
| Employee and manager (self-service) | Request leave, submit expenses, approve | Self-service launchpad, mobile |
| Platform administrator and functional consultant | Configure, extend, monitor, migrate | Configuration workbench, extension studio, admin console |
| Internal and external auditor | Test controls, trace transactions | Read-only audit workspace, change-log explorer |
| Supplier and customer (external) | Confirm orders, send invoices, check status | Portals, API and EDI |
| AI agent (system actor) | Execute scoped tasks under policy | APIs, tool contracts, approval queues |

### **3.6 Target customer and deployment (Assumption)**

Mid-market, multi-entity companies of roughly 50 to 2,000 employees in discrete manufacturing, distribution and professional services. Delivery as multi-tenant software as a service, with an option for a dedicated single-tenant deployment. Confirm in section 17 (Open questions).

## **4\. Scope and capability tiers**

* **T0, foundation and core (“Administrative ERP”).** Platform foundation, general ledger, AP, AR, bank, basic fixed assets, cost centres, procurement and sales basics, inventory basics, core analytics and the first governed agents. A company can run a trading business on T0.  
* **T1, operational depth.** Manufacturing, warehouse management, quality, projects, core HR and time, advanced controlling, service, planning and broader agent coverage. A manufacturer can run its operation on T1.  
* **T2, advanced.** Consolidation, treasury, transportation, enterprise asset management, product lifecycle management, advanced planning, multi-country payroll and talent suites.

| Domain | Module | SAP analog | T0 | T1 | T2 |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Platform | Foundation: organisation, identity, workflow, configuration, audit, integration | Basis, BTP, Fiori | Full | Hardening | Marketplace hooks |
| Finance | General ledger and close | FI-GL | Journal, periods, one ledger | Parallel ledgers, close cockpit | Consolidation |
| Finance | Accounts payable, receivable, bank | FI-AP, FI-AR, bank accounting | Invoices, payments, reconciliation | Dunning, disputes, netting | Treasury, in-house cash |
| Finance | Fixed assets | FI-AA | Register, straight-line | Multiple depreciation areas | Leases, asset transfers |
| Finance | Controlling | CO | Cost centres | Internal orders, product costing, profitability | Allocation cycles, planning |
| Finance | Tax and compliance | Tax, e-invoicing | Tax engine, one country pack | Withholding, e-invoicing | Multi-country packs |
| Procurement | Sourcing and purchasing | MM-PUR, Ariba-like | Requisition, PO, goods receipt, invoice match | RFQ, contracts, supplier portal | Supplier risk, auctions |
| Supply chain | Inventory and warehouse | MM-IM, EWM | Stock ledger, movements | Bins, batches, serials, tasks | Slotting, yard |
| Supply chain | Logistics and transportation | TM | Delivery | Shipment | Freight costing, carriers |
| Manufacturing | Planning and execution | PP, QM, PP-PI |  | BOM, routing, MRP, production orders, inspection | Advanced scheduling, process industry |
| Sales | Order to cash | SD | Quote, order, delivery, billing | Pricing engine, credit, returns, rebates | Subscription, contract billing |
| Service | Customer service | SD-CS, S/4 Service |  | Tickets, service orders | Field service, warranties |
| Asset management | Maintenance | PM, EAM |  | Work orders, preventive plans | Condition monitoring |
| Projects | Project system | PS |  | WBS, time, billing | Resource planning |
| R\&D | Product data and change | PLM | Product master | Engineering change | Product lifecycle, compliance data |
| HR | Core HR, time, payroll, expenses | HCM, SuccessFactors |  | Core HR, time, expenses | Payroll, recruiting, talent |
| Compliance | Environment, health, safety | EHS |  | Incident log | Full EHS |
| Analytics | Reporting and planning | Embedded analytics, BPC | Operational reports | Self-service analytics | Integrated planning |
| AI | Assistants and agents | Joule | Copilot, three agents | Ten or more agents | Cross-domain agent teams |

## **5\. System architecture**

### **5.1 Logical layers**

| Layer | Responsibility | Notes |
| ----- | ----- | ----- |
| Experience | Web application, mobile and scanner clients, portals, agent chat surface | Metadata-driven screens generated from entity and view definitions (section 12\) |
| Access | API gateway, backend-for-frontend, authentication, rate limiting | Single front door for people, partners and agents |
| Domain modules | Finance, Procurement, Inventory, Manufacturing, Sales, Service, Projects, HR, and others | Each module owns its data and exposes published interfaces |
| Platform services | Organisation, identity, master data, documents, workflow, rules, configuration, audit, localization, notifications | Shared by all modules; specified in section 6 |
| Data | Transactional store, analytical store, object storage for attachments | One transactional source of truth |
| Integration | REST and event APIs, webhooks, EDI and bank adapters, file exchange | Section 9 |
| AI | Model gateway, tool registry, agent runtime, evaluation harness | Section 10 |
| Operations | Observability, deployment, tenant lifecycle, backup | Section 11 |

### **5.2 Architecture decisions**

Each decision is a recommended default. Override only through a written decision record.

| ID | Decision | Recommended default | Alternatives and reasoning |
| ----- | ----- | ----- | ----- |
| D-01 | Application style | Modular monolith with module boundaries enforced by automated dependency checks | Microservices add network failure modes and make atomic posting hard. Split a module out later only on measured need. |
| D-02 | Language and runtime | TypeScript on a server runtime for back end and front end, with schemas shared between them | Java, Kotlin or C\# are common in ERP and equally valid. TypeScript is chosen for agent proficiency and single-language contracts. Financial arithmetic MUST use a decimal library, never native floating point. **Open** for human confirmation. |
| D-03 | Transactional store | PostgreSQL, one schema per module, row-level security for tenant isolation | Distributed SQL adds cost and complexity not needed at mid-market scale. Use partitioning on large journal and stock tables. |
| D-04 | Tenancy | Shared database with tenant key and row-level security as standard; dedicated database per tenant for the single-tenant option | Schema-per-tenant is a middle path if isolation requirements grow. |
| D-05 | Events | Transactional outbox in the same database transaction, relayed to a message broker | Start with a Postgres-backed relay. Move to a managed streaming service when throughput demands. Events drive integration and read models; they never replace the atomic posting rule. |
| D-06 | API contracts | REST described by OpenAPI 3.1; asynchronous events described by AsyncAPI; contracts are written before implementation | GraphQL MAY be added for UI aggregation only. |
| D-07 | Workflow | A durable workflow component with state machines and human tasks, behind a replaceable interface | An external workflow engine is acceptable if it meets the audit requirements in section 6.5. |
| D-08 | Rules and policy | Declarative decision tables plus a sandboxed expression language for pricing, approval, account determination and validation | Arbitrary customer code is not allowed in the core process path. |
| D-09 | Analytics | Operational reports read from replicas. Analytical workloads use a columnar store fed by change data capture, with a governed semantic layer | Embedded analytics on the transactional store is acceptable for T0. |
| D-10 | Front end | Single web application with a metadata-driven UI framework and a design system; progressive web app for warehouse and self-service | Native mobile is out of scope for this version. |
| D-11 | Hosting | Containerised, cloud-agnostic abstractions, with Google Cloud as the default target (managed containers, managed Postgres, managed messaging, managed analytics, managed model APIs) | Chosen to fit the Google tooling already in use. Kubernetes is an alternative. |
| D-12 | AI runtime | Provider-agnostic model gateway, default provider Google Gemini through its API | Other providers plug in through the same interface. Billing is separate from the Antigravity plan (Assumption). |
| D-13 | Money and quantities | Fixed-point decimals, amounts stored with currency, quantities with unit of measure, explicit rounding rules per currency | Floating point is forbidden anywhere in financial or inventory logic. |
| D-14 | Time | UTC instants for events, business dates for postings, fiscal calendars per legal entity, time zone per entity | Posting date, document date and entry timestamp are always distinct fields. |
| D-15 | Identifiers | Time-ordered UUIDs as internal keys; human-readable business numbers from governed number ranges | Business numbers are never reused. |

### **5.3 Module boundaries and dependency rules**

* **ARC-001 (T0).** A module MUST access another module’s data only through that module’s published interface or by consuming its events. Direct reads of another module’s tables are forbidden and the build MUST fail on violation.  
* **ARC-002 (T0).** Dependencies flow in one direction: Foundation, then Master Data, then operational modules (Procurement, Inventory, Manufacturing, Sales, Service, Projects, HR), then Finance via the Accounting Interface. Finance MUST NOT call back into operational modules to complete a posting.  
* **ARC-003 (T0).** Operational modules never write to ledgers directly. They submit an accounting request to the Accounting Interface, which validates, determines accounts, and posts.  
* **ARC-004 (T0).** **Atomic posting rule.** An operational document and its accounting document are committed in the same database transaction, or neither is. No accounting entry exists without a source document, and no valued operational document exists without its accounting entry.  
* **ARC-005 (T0).** Each module ships a manifest listing its entities, APIs, events, permissions, configuration objects and dependencies. The manifest is the unit of agent context for that module.  
* **ARC-006 (T0).** Cross-module reads for screens and reports go through read models or published query interfaces, never through ad hoc joins into foreign schemas.

### **5.4 Data architecture principles**

* **DAT-001 (T0).** **Universal journal.** Financial and controlling postings share one line-item store carrying ledger, account, company, cost centre, profit centre, segment, project and other dimensions on the same row. Controlling is a view on this store, not a separate copy.  
* **DAT-002 (T0).** Posted documents are immutable. Corrections use reversal documents that reference the original.  
* **DAT-003 (T0).** Every document follows a header and item pattern with a defined status model, document flow links to predecessors and successors, and change documents for every field change.  
* **DAT-004 (T0).** Amounts carry transaction currency, company currency and group currency, with optional additional currencies for parallel valuation. Exchange rates are dated, typed and sourced.  
* **DAT-005 (T0).** Master data supports validity periods. Transactions reference the version valid on the relevant date.  
* **DAT-006 (T0).** Hard deletion of business data is forbidden. Data is archived or anonymised under retention rules (section 11).  
* **DAT-007 (T1).** Stock is kept as a movement ledger from which balances are derived and reconciled to inventory accounts.

### **5.5 Environments, configuration transport and upgrades**

* **ARC-010 (T0).** At least three environments per tenant: development, test, production. Configuration moves between them as versioned packages with validation, diff and rollback.  
* **ARC-011 (T0).** Schema changes use the expand-then-contract pattern so that a new release and the previous release run side by side during rollout. Zero-downtime upgrade is a release gate.  
* **ARC-012 (T0).** New behaviour ships behind feature flags and scenario switches, so tenants adopt changes deliberately.  
* **ARC-013 (T1).** Every extension is checked against a compatibility suite on each release. A release that would break a registered extension is blocked or flagged.

### **5.6 Extensibility**

* **EXT-001 (T0).** Custom fields: typed, validated, permission-controlled, visible in UI, API, events, reports and import templates.  
* **EXT-002 (T0).** Validation and defaulting rules written as declarative expressions at defined hook points (before save, before post, on status change).  
* **EXT-003 (T1).** Custom entities and screens via a low-code studio that produces metadata, not code in the core.  
* **EXT-004 (T1).** Side-by-side extensions: external applications use public APIs, webhooks and events under scoped credentials.  
* **EXT-005 (T0).** No extension can bypass the Accounting Interface, authorization checks or audit logging.

## **6\. Platform foundation**

The foundation is specified first and is a dependency of every other module. SAP analogs: Basis, Business Technology Platform and the Fiori launchpad layer.

### **6.1 Organisational structure (PLT-ORG)**

Entities: tenant, enterprise group, legal entity (company), segment, profit centre, cost centre, plant, storage location, warehouse, sales organisation, distribution channel, division, purchasing organisation, purchasing group, controlling area, fiscal calendar, chart of accounts assignment.

* **PLT-ORG-001 (T0).** Organisation units are defined in configuration with validity dates, hierarchies and assignment rules (for example, a plant belongs to exactly one legal entity).  
* **PLT-ORG-002 (T0).** Every transactional document records the organisation units it belongs to; authorizations and reports can filter on any of them.  
* **PLT-ORG-003 (T0).** Changing an organisation assignment after postings exist is blocked or requires an explicit transfer document.  
* **PLT-ORG-004 (T1).** Multiple legal entities with intercompany relationships, each with its own currency, calendar and chart of accounts mapping to a group chart.

### **6.2 Identity and access (PLT-IAM)**

* **PLT-IAM-001 (T0).** Authentication through standards-based single sign-on (OpenID Connect and SAML) with multi-factor authentication. Local accounts only for bootstrap and break-glass use.  
* **PLT-IAM-002 (T0).** Role-based access control with fine-grained permissions of the form entity, action, scope. Scope limits by organisation unit, document type or amount range (attribute-based constraints).  
* **PLT-IAM-003 (T0).** Segregation-of-duties rules are data, for example creating a supplier and approving its payment may not be held by the same user. Role assignment is checked against these rules, and conflicts require a recorded exception with an expiry.  
* **PLT-IAM-004 (T0).** Delegation and substitution with start and end dates, fully audited.  
* **PLT-IAM-005 (T0).** Service identities and agent identities are distinct principal types with scoped, revocable credentials, per-tool permissions and spending or amount limits (section 10).  
* **PLT-IAM-006 (T0).** Every authorization decision is available to the audit log for sensitive objects. A user can see why access was denied.  
* **PLT-IAM-007 (T1).** Periodic access recertification workflow for managers and auditors.

### **6.3 Master data framework (PLT-MD)**

* **PLT-MD-001 (T0).** **Business partner.** One partner entity with roles (customer, supplier, employee, bank, carrier, contact), addresses, tax registrations, bank accounts and relationships. Role-specific data lives in role extensions.  
* **PLT-MD-002 (T0).** **Product master.** Products and services with general data plus views per plant, sales organisation, purchasing organisation and company, including units of measure with conversion rules, valuation class, batch and serial control.  
* **PLT-MD-003 (T0).** **Finance master data.** Chart of accounts (group and local), account groups, cost centres, profit centres, internal orders, tax codes, payment terms, house banks, currencies and exchange-rate types.  
* **PLT-MD-004 (T0).** Master-data change requests can be routed through workflow with approval before activation (configurable per object type).  
* **PLT-MD-005 (T0).** Duplicate detection on partner and product creation using configurable matching rules, with a merge process that preserves history.  
* **PLT-MD-006 (T1).** Mass change tool with preview, validation, approval and full audit.  
* **PLT-MD-007 (T1).** Data-quality rules and a dashboard (completeness, validity, duplicates) usable by data stewards and by agents.

### **6.4 Document framework (PLT-DOC)**

* **PLT-DOC-001 (T0).** All business documents share the header and item pattern, lifecycle states (draft, released, posted, reversed, archived where applicable) and permitted transitions defined as data.  
* **PLT-DOC-002 (T0).** Number ranges are configured per document type, entity and fiscal year, gap-free where law requires, with documented behaviour on failure.  
* **PLT-DOC-003 (T0).** Document flow links every document to its predecessors and successors, so users can navigate from a payment to the invoice, order, delivery and quote.  
* **PLT-DOC-004 (T0).** Attachments with virus scanning, size and type policy, version history and retention class.  
* **PLT-DOC-005 (T0).** Output management: templates for print and PDF, email and electronic exchange, with language and entity variants, versioned and previewable.  
* **PLT-DOC-006 (T0).** Reversal and credit mechanisms are defined per document type and always create new documents.  
* **PLT-DOC-007 (T0).** Idempotency: every create and post operation accepts an idempotency key so that retries from people, agents and integrations never duplicate a document.

### **6.5 Workflow and approvals (PLT-WF)**

* **PLT-WF-001 (T0).** Approval flows defined as data: conditions on document attributes, sequential and parallel steps, thresholds, delegation, escalation timers, and substitution.  
* **PLT-WF-002 (T0).** A unified task inbox for people and an approval queue for agent proposals, with one-click approve, reject and request changes.  
* **PLT-WF-003 (T0).** Workflow runs are durable across restarts and upgrades, and fully auditable (who, when, what changed, which rule triggered it).  
* **PLT-WF-004 (T1).** Process templates (for example, new supplier onboarding, period close, employee onboarding) with checklists, owners, due dates and status dashboards.  
* **PLT-WF-005 (T1).** Workflow simulation: test a flow against sample documents before activation.

### **6.6 Configuration and fit-to-standard (PLT-CFG)**

* **PLT-CFG-001 (T0).** A configuration workbench exposes every business-policy setting as typed, documented, validated configuration objects with dependency checks.  
* **PLT-CFG-002 (T0).** All configuration is versioned with author, reason and timestamp. A configuration set can be exported, diffed, imported and rolled back.  
* **PLT-CFG-003 (T0).** **Scenario templates.** A scenario (for example, standard procure-to-pay, make-to-stock manufacturing, project billing) is a bundle of configuration, workflow, output templates and test data that can be activated per entity.  
* **PLT-CFG-004 (T1).** A guided setup that asks business questions, proposes the matching scenarios, activates them in a sandbox and runs the scenario’s acceptance tests.  
* **PLT-CFG-005 (T1).** A configuration consistency checker finds contradictions and unreachable settings, such as an account determination with no matching account.

### **6.7 Audit and change log (PLT-AUD)**

* **PLT-AUD-001 (T0).** An append-only audit store records create, change, delete-intent, status change, posting, approval, login, permission change and configuration change, with actor (person, service or agent), time, source and before and after values.  
* **PLT-AUD-002 (T0).** The audit store is tamper-evident through hash chaining or equivalent, and exportable to external storage.  
* **PLT-AUD-003 (T1).** Optional read-access logging for sensitive objects (for example, salary data, bank accounts).  
* **PLT-AUD-004 (T1).** An audit workspace for auditors: read-only, search by document, user, date or account, with sampling tools.

### **6.8 Localization framework (PLT-LOC)**

* **PLT-LOC-001 (T0).** Country packs are installable units that add tax rules, legal document formats, statutory reports, number and date formats, and mandatory fields on top of the neutral core.  
* **PLT-LOC-002 (T0).** A **tax determination engine** selects tax codes and rates from rules on ship-from, ship-to, product tax class, partner tax status and date, and records the determination basis on each document line.  
* **PLT-LOC-003 (T1).** Electronic invoicing adapters (clearance and network-based regimes) behind a common interface, with status tracking and retry.  
* **PLT-LOC-004 (T0).** Multi-language user interface and master-data translations, with right-to-left support designed in.  
* **PLT-LOC-005 (T1).** Statutory report framework able to produce standard audit files and tax returns as defined by each country pack.

### **6.9 Integration and data exchange (PLT-INT)**

* **PLT-INT-001 (T0).** Every entity and process action is available through a versioned public API with consistent pagination, filtering, error model, optimistic concurrency and idempotency.  
* **PLT-INT-002 (T0).** Outbound events and webhooks with at-least-once delivery, signing, replay and dead-letter handling.  
* **PLT-INT-003 (T0).** Import and export in common file formats with mapping templates and validation reports.  
* **PLT-INT-004 (T1).** Adapters for electronic data interchange in the common standards, bank statement and payment formats (including ISO 20022), and an e-invoicing network access point.  
* **PLT-INT-005 (T1).** An integration monitor showing message status, failures, retries and payload inspection under authorization.

### **6.10 Data migration (PLT-MIG)**

* **PLT-MIG-001 (T0).** Migration templates per object type, a staging area, validation rules, dry-run, and reconciliation reports comparing legacy and Atlas totals.  
* **PLT-MIG-002 (T0).** Support for opening balances, open items (AP, AR, stock, assets) and historical transactions as summary or detail.  
* **PLT-MIG-003 (T1).** Re-runnable, idempotent loads and cutover rehearsal mode.

### **6.11 Supporting services (PLT-SVC)**

* **PLT-SVC-001 (T0).** Job scheduler for recurring and ad hoc background jobs with calendars, dependencies, retries, and monitoring.  
* **PLT-SVC-002 (T0).** Notification service for in-app, email and chat channels with user preferences and templates.  
* **PLT-SVC-003 (T0).** Global search across business objects, respecting authorizations.  
* **PLT-SVC-004 (T0).** Reference data service for currencies, countries, units, calendars and exchange rates, with scheduled rate updates from configured providers.  
* **PLT-SVC-005 (T1).** User-defined worklists, saved filters, favourites and personalised home pages.

## **7\. Core business modules**

Each module spec follows the same shape: scope and SAP analog, main entities, requirements by process area, invariants and acceptance criteria, and agent opportunities. An agent implementing a module needs only section 5, section 6 and the module’s own subsection plus the interfaces it declares.

### **7.1 Finance (FIN)**

**Scope.** General ledger, parallel ledgers, accounts payable, accounts receivable, bank and cash, fixed assets, tax, and period close. **SAP analog:** FI-GL, FI-AP, FI-AR, bank accounting, FI-AA, financial close.

**Main entities.** Ledger, chart of accounts, account, journal entry (header and lines), fiscal period, exchange rate, open item, clearing document, payment run, bank statement, asset, depreciation run, tax code, tax return.

#### **General ledger**

* **FIN-GL-001 (T0).** A journal entry has at least two lines and MUST balance in transaction currency per document and in company currency. It posts into an open period of the target ledger.  
* **FIN-GL-002 (T0).** Posting periods open and close per legal entity, per ledger and per account type. Special periods exist for year-end adjustments.  
* **FIN-GL-003 (T0).** Account master carries type (asset, liability, equity, revenue, expense, statistical), open-item management flag, currency control, required dimensions and tax category. Reconciliation accounts for subledgers reject direct manual posting.  
* **FIN-GL-004 (T0).** Recurring, accrual, deferral and reversing entries from templates, with allocation by key and automatic reversal in the next period.  
* **FIN-GL-005 (T0).** Foreign-currency revaluation of open items and balances at period end, with automatic reversal.  
* **FIN-GL-006 (T1).** Parallel ledgers: one leading ledger and additional ledgers for other accounting standards. Documents post to all ledgers by default, with ledger-specific adjustment entries.  
* **FIN-GL-007 (T0).** Reversal posts a new document that references the original. A reversal into a closed period is refused unless authorised.  
* **FIN-GL-008 (T0).** Reporting: trial balance, balance sheet, income statement, and (T1) cash flow statement with the indirect method. Every figure drills down to line items and source documents. Report structures are configurable versions.  
* **FIN-GL-009 (T1).** Four-eyes approval for manual journals above thresholds, mass journal upload, and balance-sheet splitting by profit centre or segment.  
* **FIN-GL-010 (T2).** Group consolidation: ownership structures, currency translation, intercompany elimination, minority interest.

#### **Accounts payable**

* **FIN-AP-001 (T0).** Supplier invoices (with or without purchase order), credit memos and down payments, with tax calculation, payment terms and discount.  
* **FIN-AP-002 (T0).** Duplicate invoice detection on partner, reference, amount and date.  
* **FIN-AP-003 (T0).** Payment proposal and payment run by due date and discount, per payment method and house bank, with blocking, approval and payment file output.  
* **FIN-AP-004 (T1).** Partial payments and residuals, netting against receivables of the same partner, one-time suppliers, and supplier statement reconciliation.

#### **Accounts receivable**

* **FIN-AR-001 (T0).** Customer invoices from billing or manual entry, credit memos, incoming payments, clearing with partial and residual handling, and unapplied cash.  
* **FIN-AR-002 (T0).** Aging, customer balances and statements.  
* **FIN-AR-003 (T1).** Dunning with configurable procedures and levels, interest, blocks and dispute flags.  
* **FIN-AR-004 (T1).** Collections worklist, promise-to-pay tracking, dispute cases, and automatic cash application using matching rules and remittance advice.  
* **FIN-AR-005 (T1).** Credit exposure published to Sales for credit management (SLS-CR-001).

#### **Bank and cash**

* **FIN-BNK-001 (T0).** Bank accounts, statement import in common formats, rule-based automatic reconciliation and manual matching.  
* **FIN-BNK-002 (T1).** Payment file generation and status feedback, cash position and short-term cash forecast.  
* **FIN-BNK-003 (T2).** Treasury: cash pooling, in-house bank, foreign-exchange exposure, debt and investment instruments, hedge accounting.

#### **Fixed assets**

* **FIN-FA-001 (T0).** Asset master and classes, acquisition from invoice or manually, retirement, transfer, and monthly straight-line depreciation with a posting run.  
* **FIN-FA-002 (T1).** Multiple depreciation areas aligned to ledgers, additional methods, assets under construction with settlement, revaluation and impairment.  
* **FIN-FA-003 (T2).** Lease accounting.

#### **Tax and close**

* **FIN-TAX-001 (T0).** Tax lines on every taxable document from the determination engine (PLT-LOC-002), input and output tax accounts, reverse charge.  
* **FIN-TAX-002 (T1).** Tax return preparation through country packs and withholding tax.  
* **FIN-CLS-001 (T0).** Period-end tasks run as controlled jobs: depreciation, revaluation, accrual reversal, goods-receipt/invoice-receipt clearing.  
* **FIN-CLS-002 (T1).** Close cockpit with task dependencies, owners, status and pre-close checks (unposted documents, open receipts, unreconciled bank items, unvalued stock).  
* **FIN-CLS-003 (T1).** Variance (flux) analysis with draft commentary produced by an agent and approved by a person.

#### **Invariants and acceptance criteria**

* **FIN-INV-1.** Debits equal credits per document, per ledger and per currency of record, always.  
* **FIN-INV-2.** For every subledger, the sum of open items equals the balance of its reconciliation account at every moment.  
* **FIN-INV-3.** The trial balance of any period equals the sum of its line items. Closed periods change only through documents dated in open periods.  
* **FIN-INV-4.** A posted document’s content never changes. A content hash stored at posting remains valid.  
* **FIN-INV-5.** No posting reaches a closed period except by authorised special-period entry.  
* **Acceptance.** Golden datasets with expected trial balances per scenario. Property-based tests generate random sequences of postings, reversals and clearings and verify FIN-INV-1 to 5 after each step. A full month-end rehearsal on migrated data must reconcile to the legacy trial balance.

**Agent opportunities.** Invoice coding and matching, bank reconciliation, dunning proposals, accrual proposals, close assistant, journal anomaly detection (section 10).

### **7.2 Controlling (CO)**

**Scope.** Cost and profitability management on the universal journal (DAT-001). **SAP analog:** CO-CCA, CO-OM, CO-PC, CO-PA.

**Main entities.** Controlling area, cost centre and hierarchy, activity type, statistical key figure, internal order, allocation cycle, cost estimate, profitability segment.

* **CO-CCA-001 (T0).** Cost-centre accounting: primary costs arrive from journal lines carrying a cost centre; plan versus actual and budgets by period.  
* **CO-CCA-002 (T0).** Cost-centre hierarchies, statistical postings, and period comparison reports reconciling to the ledger.  
* **CO-ORD-001 (T1).** Internal orders with budget and availability control and settlement rules to cost centre, asset or profit centre.  
* **CO-ALC-001 (T1).** Allocation cycles (assessment and distribution) and activity-type pricing for internal activity allocation. Secondary costs live in the same journal.  
* **CO-PC-001 (T1).** Product costing: standard cost estimates from bills of material, routings and overhead rules, with mark-and-release. Actual costing with layered price determination is T2.  
* **CO-PA-001 (T1).** Profitability analysis by customer, product, region and channel, fed by revenue, discounts and cost of goods sold, with configurable characteristics and value fields.  
* **CO-PLN-001 (T2).** Driver-based planning and budgeting with versions and plan-versus-actual.  
* **Invariants.** Allocation cycles conserve totals (sender equals receiver). Cost-centre reports reconcile to ledger accounts. Margin reports reconcile to revenue and cost-of-goods-sold accounts.

### **7.3 Procurement (PRC)**

**Scope.** Requisition to payment, sourcing, contracts and supplier management. **SAP analog:** MM-PUR, source-to-pay capabilities.

**Main entities.** Purchase requisition, request for quotation, quotation, purchase order, scheduling agreement, contract, goods receipt, service entry, supplier invoice, source list and info record, supplier evaluation, catalog.

* **PRC-REQ-001 (T0).** Requisitions from users, planning, maintenance and projects, with approval and source assignment.  
* **PRC-SRC-001 (T1).** Request for quotation to multiple suppliers, quotation comparison, award, and conversion to purchase order or contract.  
* **PRC-PO-001 (T0).** Purchase orders with item categories (standard, service, and later subcontracting, consignment, third-party), price determination from info records and contracts, delivery schedules, supplier confirmations, versioned changes with re-approval, and supplier output.  
* **PRC-PO-002 (T0).** Account assignment (cost centre, asset, project, order, or none for stock) with budget commitment visible in controlling.  
* **PRC-CTR-001 (T1).** Quantity and value contracts with validity, release orders and tiered prices.  
* **PRC-GR-001 (T0).** Goods receipt against orders with partial deliveries, tolerances for over- and under-delivery, returns to supplier. Service entry sheets are T1.  
* **PRC-IV-001 (T0).** Invoice verification: three-way match (order, receipt, invoice) with configurable price and quantity tolerances, two-way option, blocked invoices with release workflow, and clearing of the goods-receipt/invoice-receipt account.  
* **PRC-SUP-001 (T1).** Supplier onboarding workflow with sanctions screening, bank-detail verification with a callback step, evaluation scorecards, and a portal for order confirmation, shipping notices and invoice submission.  
* **PRC-SUP-002 (T2).** Supplier risk monitoring, auctions, spend classification and analytics.  
* **Business rules.** Changes to supplier bank details require four-eyes approval. Order commitments appear in budget availability. Price determination order is configurable and logged on each line.  
* **Invariants.** Quantity received minus quantity invoiced is explained by open receipts or invoices. No invoice posts beyond tolerance without a block.

**Agent opportunities.** Requisition-to-order assistant, invoice matching, supplier onboarding, late-delivery expediting, contract compliance checks.

### **7.4 Sales (SLS)**

**Scope.** Quote to cash for goods and services. **SAP analog:** SD, billing, parts of sales cloud.

**Main entities.** Customer role, price condition, quotation, sales order, scheduling agreement, delivery, shipment, billing document, return, rebate agreement, availability check.

* **SLS-QT-001 (T0).** Quotations with validity and conversion to orders.  
* **SLS-SO-001 (T0).** Sales orders with partner functions (sold-to, ship-to, bill-to, payer), schedule lines, delivery terms, payment terms, texts, copy control from quotations, versioned changes and status tracking.  
* **SLS-PRC-001 (T0).** Pricing through decision tables: base price, discounts, surcharges, freight and tax, with access sequences and justified manual overrides. T1 adds scales, bundles, customer-specific and promotional pricing.  
* **SLS-ATP-001 (T0).** Availability check against stock and planned receipts with confirmed dates. T1 adds allocation and backorder processing.  
* **SLS-CR-001 (T1).** Credit management with limits per customer and segment, exposure from orders, deliveries and invoices, and block and release workflow.  
* **SLS-DL-001 (T0).** Delivery creation, picking, packing and goods issue, partial deliveries and delivery blocks.  
* **SLS-BIL-001 (T0).** Billing from delivery, order or service: partial and collective billing, credit and debit memos, cancellation, revenue account determination, posting to receivables through the Accounting Interface, invoice output and electronic invoicing.  
* **SLS-RET-001 (T1).** Returns authorisation, receipt, inspection, credit and replacement.  
* **SLS-RBT-001 (T1).** Rebate agreements with accruals and settlement.  
* **SLS-REV-001 (T2).** Revenue recognition with performance obligations and deferral, subscription and usage-based billing.  
* **Invariants.** Billed quantity never exceeds delivered quantity unless configured. Revenue postings equal billing document lines. Cost of goods sold posts at goods issue at the valuation price.

**Agent opportunities.** Quote-to-order assistant, credit-block resolution, order exception handling (late, short, backordered), pricing anomaly detection.

### **7.5 Inventory and warehouse (INV)**

**Scope.** Stock, movements, valuation, physical inventory, warehouse execution. **SAP analog:** MM-IM, extended warehouse management.

**Main entities.** Movement ledger, stock type, batch, serial number, storage bin, handling unit, physical inventory document, reservation, valuation layer.

* **INV-MOV-001 (T0).** Goods movements for receipt, issue, transfer, scrap and adjustment. A movement type is data that defines its stock and accounting effect. Reversal creates a new movement.  
* **INV-STK-001 (T0).** Stock is derived from the movement ledger. Real-time on-hand, reserved and available quantities by stock type (unrestricted, quality inspection, blocked, in transit, consignment).  
* **INV-VAL-001 (T0).** Valuation by moving average and standard price, with price change documents and a negative-stock policy. T1 adds FIFO and layered valuation.  
* **INV-PI-001 (T0).** Physical inventory with count documents, recounts and variance posting. T1 adds cycle counting.  
* **INV-BAT-001 (T1).** Batch and serial management with forward and backward traceability.  
* **INV-WM-001 (T1).** Warehouse management: bins, putaway and picking strategies (including first-expired-first-out), wave picking, handling units, packing, mobile scanner tasks, replenishment, inbound and outbound processing. Cross-docking and slotting are T2.  
* **INV-REP-001 (T1).** Reorder-point and min-max replenishment proposals that create requisitions.  
* **INV-TRN-001 (T1).** Stock transfer orders between plants, in-transit stock and intercompany transfers.  
* **INV-CON-001 (T1).** Consignment stock and returnable packaging.  
* **Invariants.** Stock balance equals the sum of movements. Stock value equals the inventory accounts in the ledger. Stock is never negative unless the policy allows it. Batch traceability has no gaps.

