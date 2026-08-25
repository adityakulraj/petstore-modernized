# Java Pet Store modernization summary

## Executive summary

This project preserves the familiar catalog, cart, checkout, and order-history
experience of the Oracle Java Pet Store while replacing its classic J2EE-era
application style with a Java 21, Spring Boot 4 modular monolith. The result is
easier to run, test, observe, and evolve, without turning a small domain into a
distributed system.

The application can run against Oracle or MongoDB through the same business
contract. This lets the team modernize the application layer independently of a
database migration decision, and makes the trade-offs visible through the same
API and operations dashboard.

## Architecture: before and after

| Concern | Traditional Java Pet Store / J2EE baseline | Modernized implementation | Why this is better |
|---|---|---|---|
| Runtime | Application-server-centric deployment, typically with container-managed components and deployment descriptors. | Self-contained Spring Boot 4.1 application on Java 21. | Faster startup, one executable deployment unit, and fewer server-specific moving parts. |
| Application structure | Technical tiers and framework artifacts tend to dominate navigation. | Modular monolith organized by catalog, cart, orders, shared application services, and persistence adapters. | Business behavior is easier to find and change while retaining simple in-process calls. |
| HTTP interface | Server-rendered/web-tier interactions and legacy integration styles. | Versioned JSON APIs under `/api/v1`, plus a same-origin static web UI. | Clearer client contract, browser-friendly integration, and simpler automated testing. |
| Configuration | Environment-specific server configuration and application packaging. | Typed, externalized Spring configuration and environment variables. | The same artifact runs locally, in CI, and in containers without source edits. |
| Deployment | Application server plus separately provisioned database. | Docker Compose starts the app and one selected database profile. | Reproducible local setup and isolated, disposable E2E environments. |
| Persistence | Oracle-centric data access. | One `StorefrontStore` contract with Oracle/JPA and MongoDB adapters. | Oracle remains supported while the data-store choice can evolve independently. |
| Reliability | Transaction behavior may be implicit in framework/container boundaries. | Explicit transactions, optimistic cart versions, conditional stock decrements, and idempotent checkout keys. | Prevents lost updates, overselling, and duplicate orders under concurrent requests. |
| Security | Legacy authentication patterns and broad server configuration. | Spring Security form login/basic auth, CSRF protection for mutations, role-separated customer/admin routes, and restrictive response headers/CSP. | Safer demo defaults and a clearer migration path to enterprise OIDC. |
| Observability | Logs and runtime state are often separate from the user workflow. | Correlation IDs, JSON logs, health probes, request/database telemetry, query diagnostics, and an admin dashboard. | Faster diagnosis without giving customers access to operational data. |
| Quality gates | Manual verification is common for a sample application. | JUnit, integration tests, Playwright browser/API contracts, and GitHub Actions for both Oracle and MongoDB modes. | Regressions are caught consistently across both persistence implementations. |

## What changed

### 1. A modern, portable runtime

- Upgraded the runtime target to Java 21 and Spring Boot 4.1.
- Replaced application-server deployment assumptions with a standard Spring Boot
  executable and Maven Wrapper.
- Added Docker Compose profiles so the application runs with exactly one chosen
  database: `APP_STORE=oracle` or `APP_STORE=mongo`.
- Kept the database separate from the application container and persisted each
  database through a Docker volume.

**Outcome:** a developer can launch the complete MongoDB or Oracle variant with
one repeatable command, while CI uses the same application artifact.

### 2. A domain-oriented modular monolith

The code is grouped around the domain rather than around an application server:

- `catalog`, `cart`, and `orders` contain their API and domain behavior.
- `shared/application/StorefrontService` orchestrates business operations.
- `StorefrontStore` is the persistence boundary.
- `persistence/oracle` and `persistence/mongo` implement that boundary.

**Outcome:** the service is simpler than a microservice split, but persistence
implementations can be changed, tested, and optimized independently.

### 3. Safer checkout and concurrency behavior

The modernization makes correctness rules explicit:

- Cart responses carry a version; stale writes receive HTTP 409.
- Inventory decrement requires `stock >= requestedQuantity` in the database
  predicate.
- Checkout performs inventory update, order insertion, and cart clearing in a
  single transaction.
- A unique `(customerId, idempotencyKey)` protects checkout retries from creating
  duplicate orders.
- Order lines and shipping addresses are immutable purchase-time snapshots.

**Outcome:** concurrent customers cannot oversell inventory, overwrite another
cart update, or accidentally place the same order twice.

### 4. Database modernization without a forced cutover

Oracle remains a first-class implementation through JPA and Oracle transactions.
MongoDB uses documents, a replica set for transactions, and the MongoDB driver's
connection pool. Both implementations share the same external behavior and
concurrency rules.

Indexes are aligned with real query paths: category lookup, order history,
idempotency lookup, and required foreign-key-style joins in Oracle. The dashboard
shows read-only query plans so the team can see scans, selected indexes, rows,
and execution details rather than relying on assumptions.

**Outcome:** a migration can be evaluated with production-like behavior and
diagnostics, while preserving an Oracle fallback.

### 5. Security and API hygiene

- Customer and administrator roles are separate.
- Mutation endpoints require CSRF protection.
- The admin-only health and log endpoints are not exposed to customer accounts.
- Responses carry safe request/correlation IDs for support investigations.
- Structured logs avoid exposing a caller-selected file path through the log API.
- Content Security Policy and defensive browser headers are enabled.

**Outcome:** diagnostics are useful to operators without weakening the public
application surface.

### 6. Observability built into the application

The admin dashboard at `/admin/health.html` reads the protected
`/api/v1/admin/health` endpoint and presents:

- application health, uptime, and store selection;
- rolling request rate, HTTP 4xx/5xx rate, and average/max latency;
- JVM heap and thread counts;
- database-pool usage;
- database-operation calls, failures, retries, and latency; and
- MongoDB/Oracle query-plan diagnostics.

The dashboard polls every five seconds. Its own polling and Actuator probes are
excluded from request traffic metrics, preventing the monitoring system from
creating artificial load in its charts. Telemetry is intentionally process-local
and resets at restart, which keeps the demo simple.

**Outcome:** developers and operators have a useful first-response diagnostic
surface instead of a raw JSON payload or log-file hunt.

### 7. Automated confidence across both stores

- JUnit tests cover domain and observability behavior.
- Testcontainers integration tests exercise persistence when Docker is available.
- Playwright API and browser tests validate user flows, authorization, CSRF,
  concurrency, rollback, idempotency, and the dashboard endpoint.
- GitHub Actions runs Java verification and independent MongoDB and Oracle E2E
  suites.

**Outcome:** the two persistence options are held to the same behavioral
contract, reducing the chance that a database migration quietly changes user
outcomes.

## Measurable improvements

| Improvement | Evidence in this implementation |
|---|---|
| Easier developer setup | Maven Wrapper, Java 21, and Compose profiles replace server-specific setup. |
| Clearer operational visibility | Health probes, request IDs, structured logs, protected log search, telemetry API, and dashboard. |
| Better data safety | Optimistic locking, conditional inventory updates, transactions, and idempotency. |
| Lower migration risk | One business contract, Oracle and Mongo adapters, and E2E parity tests. |
| Better security posture | Spring Security, CSRF, roles, CSP/headers, and restricted admin diagnostics. |
| Stronger release confidence | Unit, integration, browser, and API tests run in CI for both data stores. |

## What is deliberately not yet production-ready

This is a modernization foundation and local demonstration environment, not a
complete production platform. Before production, add:

- enterprise OIDC/SSO and centrally managed secrets;
- TLS, audited authorization, rate limiting, and secure credential rotation;
- Flyway/Liquibase migrations for Oracle and controlled MongoDB index/data
  migrations;
- backups, restore drills, disaster-recovery objectives, and capacity testing;
- exported metrics/traces/logs (for example OpenTelemetry plus the organization’s
  metrics platform) instead of process-local telemetry only; and
- a managed database service or production-grade database operations model.

## Recommended next milestone

Keep this modular monolith as the application boundary. Export its existing
request and database signals to centralized observability, replace demo identity
with OIDC, introduce controlled schema migrations, and use the Oracle/Mongo E2E
contract suite as the acceptance gate for any future database cutover.
