# PetStore Modernization Approach PRD

**Status:** proposed | **Owner:** candidate | **Target:** Java 21 + latest stable Spring Boot, MongoDB (stretch goal) | **Decision date:** 2026-08-24

## 1. Executive summary

Modernize Oracle Java Pet Store Demo 1.3.1_02 from J2EE 1.3-era JSP/Servlet/EJB/JMS technology into a secure, testable, observable Java 21 application without changing the customer-visible shopping flows. The first delivery is a **modular monolith**, not premature microservices: it removes obsolete platform dependencies while keeping the domain coherent, deployable, and easy to demonstrate. MongoDB is the target operational database; its document model will be designed from PetStore access patterns, not created by copying relational tables.

Oracle describes the legacy sample as an application using JSP, Servlets, EJB, and JMS. [Oracle Java Pet Store](https://www.oracle.com/java/technologies/petstore-v1312.html)

## 2. Problem and opportunity

The legacy runtime is no longer a viable operational platform: it has obsolete libraries, container-coupled components, thin automated coverage, and tightly coupled presentation/business/data concerns. The objective is not a cosmetic rewrite. It is to preserve the proven business behavior while improving delivery safety, maintainability, security posture, and the ability to evolve the catalog and order experience.

## 3. Modernization principles

1. **Inventory before change.** Capture routes, user journeys, business rules, data entities, external dependencies, configuration, and failure behavior before implementation.
2. **Preserve behavior deliberately.** Write characterization tests and a parity matrix before replacing a feature. A green modern test suite alone is not parity evidence.
3. **Small vertical slices.** Modernize catalog browsing, basket, checkout, and order history independently behind stable HTTP contracts. Keep the legacy system runnable until each slice has an acceptance decision.
4. **Transform the data model.** Model documents around reads and writes: embed product presentation and order-line snapshots; reference independently managed entities. Do not mechanically reproduce tables in collections.
5. **Security, operations, and delivery are product requirements.** Configuration comes from environment variables, validation occurs at the API boundary, secrets never enter source control, and every change runs through repeatable tests and CI.
6. **AI accelerates, engineers decide.** AI-generated inventories, code, and tests require source review, execution, and traceability to a requirement or observed legacy behavior.

These align with MongoDB guidance to assess first, implement and validate iteratively, modernize the data layer alongside the application, and avoid a table-to-collection lift-and-shift. [MongoDB Application Modernization](https://www.mongodb.com/resources/solutions/use-cases/application-modernization)

## 4. Scope

### In scope

- Customer catalog browse/search, item detail, cart lifecycle, checkout, order confirmation, and order history.
- Public REST API plus a server-rendered or SPA user interface, selected during implementation.
- Spring Boot modular-monolith application on Java 21; Maven wrapper; Docker Compose local dependency setup.
- MongoDB document data model, migrations/seed data, indexes, validation, and local/Atlas configuration.
- Authentication/authorization for customer actions, input validation, error handling, audit-friendly logs, health endpoints, metrics, CI, and automated tests.
- A reproducible legacy baseline and evidence-based parity report.

### Explicitly out of scope for the take-home

- Payments that charge real cards, email delivery, production PII, multi-region deployment, and microservice extraction.
- New customer features that would blur parity evidence.

## 5. Architecture decision

| Decision | Choice | Why |
|---|---|---|
| Runtime | Java 21 + Spring Boot | Modern supported language/runtime; fast developer feedback and strong test ecosystem. |
| Shape | Modular monolith | Right-sized for a bounded domain; preserves transactional boundaries and avoids distributed-systems overhead. Modules are extractable later. |
| Modules | `catalog`, `cart`, `orders`, `identity`, `shared` | Makes domain ownership and dependency direction visible. |
| API | Versioned REST (`/api/v1`) with OpenAPI | A stable seam for UI and future strangler extraction. |
| Data | MongoDB | Stretch goal directly addresses the challenge; document aggregates align with catalog and order reads. |
| Data access | Spring Data MongoDB plus explicit repository interfaces | Keeps query/index decisions visible and infrastructure replaceable. |
| UI | Thin React SPA or Spring MVC/Thymeleaf, selected after baseline | UI is deliberately separate from domain rules. |
| Deployment | Container image; local Docker Compose; cloud-ready config | Same artifact across environments; no cloud account required for the demo. |

## 6. Target data model and integrity boundaries

- `products`: product identity, name, description, category, media, tags, current price, inventory summary. Embed static display attributes because catalog/detail reads need them together.
- `customers`: identity profile and address book; do not store credentials in this document beyond a password hash if local identity is retained.
- `carts`: one active cart per customer/session; line items include `productId`, quantity, and price-at-add only if the legacy behavior needs it; enforce a unique active-cart lookup and optimistic locking/versioning.
- `orders`: an order aggregate embeds line-item product name/SKU, unit price, quantity, shipping address snapshot, totals, and status history. It must remain historically correct if the catalog changes.
- `inventory`: separate when write contention or operational ownership differs; use atomic conditional updates and a transaction with order creation when stock reservation is in scope.

Required indexes: product category/name/search fields; unique customer identity; cart customer/status; order customer/created-at; inventory product ID. Validate cardinality, document size, query explain plans, and duplicate-key behavior. MongoDB Relational Migrator supports schema redesign, denormalization, custom mapping, snapshot migration, and integrity verification; use it as an accelerator, not an architecture substitute. [Relational Migrator documentation](https://www.mongodb.com/docs/relational-migrator/)

## 7. Delivery plan and gates

| Phase | Deliverable | Exit gate |
|---|---|---|
| 0. Discovery | legacy runbook, dependency map, route inventory, data/schema inventory | Legacy starts from a clean checkout; unknowns are recorded. |
| 1. Baseline | characterization tests, screenshots/API captures, golden fixtures, parity matrix | Core journeys have independently repeatable observed outcomes. |
| 2. Foundation | Java 21 app, config, health, CI, test harness, Mongo local environment | `./mvnw verify` and `docker compose up` are reproducible. |
| 3. Catalog slice | product/category APIs and UI | Legacy/modern output comparison passes. |
| 4. Cart slice | cart CRUD and validation | Price, quantity, and invalid-input behavior agree with baseline. |
| 5. Orders slice | checkout, order persistence, history | Idempotency/stock policy and historical price rules tested. |
| 6. Hardening | security, observability, load/chaos smoke, docs | Release checklist passes; demo is rehearsed from a fresh environment. |

## 8. Success metrics and acceptance criteria

- Both legacy and modern apps boot from documented steps on a clean machine.
- 100% of agreed core user journeys appear in the parity matrix; all have a test/evidence link.
- 0 critical/high dependency or code-scanning findings accepted without a documented mitigation.
- Unit, integration, and end-to-end suites pass in CI; coverage is a guardrail, not the parity claim (target: >=80% line coverage for new domain/application code and 100% coverage of defined business-rule decisions).
- P95 catalog API latency <300 ms and checkout <750 ms against the local test dataset; document assumptions and hardware.
- All configuration is externalized; no credentials, connection strings, or real customer data are committed.
- Demo can be reset deterministically with seed data.

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Legacy source/build does not run on current tooling | Containerize the legacy runtime; preserve logs and setup notes as baseline evidence. |
| Hidden behavior in JSP/EJB code | Characterization tests, route tracing, and fixtures before reimplementation. |
| Database migration changes semantics | Map each entity and rule; reconcile row/document counts and business totals; run dual-read comparison on fixtures. |
| MongoDB schema causes duplication anomalies | Declare aggregate ownership, snapshot historical order data intentionally, and test update paths. |
| AI produces plausible but incorrect code | Require human review, test execution, and an AI decision log. |
| Time-box pressure encourages microservices | Keep modular monolith; show extraction seams and a future strangler roadmap. |

## 10. Presentation evidence

Show the legacy route/component map, a representative characterization test, the modern module/API/data-model path for one checkout request, CI results, and an AI decision record. Conclude with the trade-off: a modular monolith makes a safer first cut; microservices are a later decision based on proven independent scaling/team boundaries, not a modernization checkbox.

## Sources

- [Oracle Java Pet Store](https://www.oracle.com/java/technologies/petstore-v1312.html)
- [MongoDB: Application Modernization](https://www.mongodb.com/resources/solutions/use-cases/application-modernization)
- [MongoDB: What is Application Modernization?](https://www.mongodb.com/resources/basics/application-modernization)
- [MongoDB Relational Migrator](https://www.mongodb.com/docs/relational-migrator/)
