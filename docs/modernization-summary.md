# Java Pet Store modernization summary

## Executive summary

This project preserves the familiar catalog, cart, checkout, order-history,
customer-account, payment, customer cancellation/refund, administrator-approval, supplier-fulfilment, and customer order-notification experience of the Oracle Java Pet Store while replacing its classic J2EE-era
application style with a Java 21, Spring Boot 4 modular monolith. The result is
easier to run, test, observe, and evolve, without turning a small domain into a
distributed system.

The application can run against Oracle or MongoDB through the same business
contract. This lets the team modernize the application layer independently of a
database migration decision, and makes the trade-offs visible through the same
API and operations dashboard.

## Evidence base and modernization scope

This comparison was checked against the supplied PetStore 1.3.2 source tree at
`/Users/adkunwar/Downloads/petstore1.3.2`. The modernization in this repository
covers the customer storefront, customer account lifecycle, atomic backorders and supplier replenishment, demo payment authorization/capture/void/refund, customer cancellation/refund, high-value order approval, supplier inventory, supplier purchase-order processing, and durable in-app customer notifications. Remaining external production integrations are called out explicitly rather than presented as complete.

The source confirms a J2EE 1.3 architecture: four EAR applications
(storefront, administrator, OPC, and supplier); a storefront EAR containing
seven EJB modules plus `petstore.war`; BluePrints WAF `MainServlet` and web
controllers; CMP EJBs, JNDI/JDBC bindings, JMS queues/topics, and
message-driven beans. Its installation guide requires the J2EE SDK 1.3.1,
Cloudscape, a J2EE server, and Ant 1.4.1 build/deploy steps.

Relevant legacy sources include:

- `src/apps/petstore/src/application.xml`
- `src/apps/petstore/src/docroot/WEB-INF/web.xml`
- `src/apps/petstore/src/sun-j2ee-ri.xml`
- `src/components/asyncsender` and `src/components/mailer`
- `docs/installing.html`, `docs/building.html`, and `src/build.sh`

## Architecture: before and after

| Concern | Traditional Java Pet Store / J2EE baseline | Modernized implementation | Why this is better |
|---|---|---|---|
| Runtime | J2EE SDK 1.3.1 runtime with vendor-specific J2EE RI descriptors. | Self-contained Spring Boot 4.1 application on Java 21. | Faster startup, one executable deployment unit, and fewer server-specific moving parts. |
| Application structure | Four EARs, WAF, EJBs, and shared component modules. | A modular monolith organized by accounts, catalog, cart, orders, supplier, observability, and persistence adapters. | Related workflows remain easy to navigate and transact without recreating distributed-system complexity. |
| HTTP interface | WAF `MainServlet`, web controllers, JSP/template views, and EJB facades. | Versioned JSON APIs under `/api/v1`, plus a same-origin static web UI. | Clearer client contract, browser-friendly integration, and simpler automated testing. |
| Configuration | XML descriptors and JNDI names configure EJBs, JDBC, JMS, and server mappings. | Typed, externalized Spring configuration and environment variables. | The same artifact runs locally, in CI, and in containers without source edits. |
| Deployment | Ant 1.4.1 produces and verifies multiple EARs for a J2EE server; Cloudscape is part of setup. | Docker Compose starts the app and one selected database profile. | Reproducible local setup and isolated, disposable E2E environments. |
| Persistence | CMP EJBs and JNDI JDBC data sources. | One `StorefrontStore` contract with Oracle/JPA and MongoDB adapters. | Oracle remains supported while the data-store choice can evolve independently. |
| Reliability | Transaction behavior may be implicit in framework/container boundaries. | Explicit transactions, optimistic versions, conditional stock changes, idempotent checkout/inventory/PO/decision/customer-action handling, all-or-nothing backorder allocation, atomic payment transitions, and serialized races. | Prevents lost updates, overselling, partial reservations, duplicate orders/charges/fulfilment/refunds, and double inventory restoration. |
| Security | Legacy authentication patterns and broad server configuration. | Spring Security form-login sessions for business workflows, Basic auth limited to read-only diagnostics, CSRF protection for mutations, role-separated customer/admin routes, and restrictive response headers/CSP. | Safer demo defaults, no cached-Basic role confusion, and a clearer migration path to enterprise OIDC. |
| Workflow integration | JMS queues/topics and message-driven beans connect storefront, OPC, supplier, customer relations, and mailer flows. | Approval, supplier fulfilment, payment, cancellation, and refund are transactional workflows; a transactional notification outbox drives an in-app inbox/timeline with retry audit. The local payment ledger uses opaque demo tokens and leaves a production provider as a replaceable adapter; email is intentionally unnecessary. | Preserves visible outcomes, prevents state/message divergence, and leaves true external boundaries replaceable. |
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

- `accounts`, `analytics`, `catalog`, `cart`, `mylist`, `orders`, `payments`, and `supplier` contain their API and domain behavior.
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
- Orders below the configurable approval threshold are auto-approved; high-value orders wait in an admin queue.
- Approval creates one supplier PO; denial atomically restores reserved stock exactly once.
- If any line is unavailable, checkout reserves nothing and creates a durable `BACKORDERED` order instead of losing the customer's request.
- Supplier replenishment checks waiting orders oldest first, allocates every line atomically, and returns released orders to the same approval policy.
- A durable inventory-command idempotency key makes concurrent retries converge without resetting stock consumed by a prior backorder release.
- Checkout authorizes an opaque demo payment token in the same transaction; a decline rolls back order, inventory, notification, and cart changes.
- Supplier processing captures the authorization atomically with PO/order completion.
- Customer cancellation voids the authorization, cancels any ready supplier PO, and restores reserved stock exactly once; backorders restore no stock because they reserved none.
- Customer refund changes a completed order and captured payment together. It deliberately does not restock a physical item without a return-receipt workflow.
- Durable customer-action commands make cancel/refund retries converge and reject same-key/different-request misuse.

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

- Customer, administrator, and supplier roles are separate.
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

### 8. Legacy account and back-office workflows in a browser

- Customer registration/profile persists contact details, default address, language/category, and display preferences using BCrypt password hashes.
- Checkout uses an opaque local demo token and persists a payment ledger with `AUTHORIZED`, `CAPTURED`, `VOIDED`, and `REFUNDED` states. Raw card details and even the opaque input token are never stored.
- Customers can cancel eligible pre-fulfilment orders or refund completed/captured orders from the storefront; all outcomes and payment transitions appear as durable in-app notifications.
- `/admin/orders.html` replaces the Java Web Start approval client with a responsive pending queue, audit history, and replay-safe approve/deny commands.
- `/admin/sales.html` restores the legacy date/category sales and revenue charts with explicit recognized-versus-pending semantics and item/SKU drilldown.
- `/admin/catalog.html` adds role-protected item/SKU creation, metadata and price changes, publish/archive controls, and a durable audit trail while suppliers retain inventory ownership.
- `/supplier/` replaces the separate supplier EAR with role-protected inventory, waiting-backorder, and purchase-order screens.
- The storefront **Inbox** and per-order timeline replace opaque customer-relations mail hand-offs with durable events for backordered, inventory-allocated, pending, approved/denied, and completed orders.
- The storefront **MyList** persists explicit customer favourites, derives deterministic recommendations from sibling variants and favourite categories, and restores item-level choices such as **Male Adult Bulldog** and **Female Puppy Bulldog**.
- The original strict US policy is retained: totals below $500 auto-approve; totals at or above $500 require review.

**Outcome:** the most visible account, personalized catalogue, payment, cancellation/refund, catalog/pricing, approval, sales analytics, supplier, and customer-relations capabilities now run in the same deployable service while retaining role separation and transactional guarantees. Payment/cancellation/refund diagrams are in [feature-payment-cancellation-refund.md](feature-payment-cancellation-refund.md), approval diagrams are in [feature-admin-order-approval.md](feature-admin-order-approval.md), catalog diagrams are in [feature-catalog-price-management.md](feature-catalog-price-management.md), analytics diagrams are in [feature-sales-revenue-analytics.md](feature-sales-revenue-analytics.md), backorder/replenishment diagrams are in [feature-backorder-replenishment.md](feature-backorder-replenishment.md), notification/outbox diagrams are in [feature-customer-notifications.md](feature-customer-notifications.md), and MyList/item diagrams are in [feature-mylist-and-item-variants.md](feature-mylist-and-item-variants.md).

The complete Oracle relational model, MongoDB document model, primary/foreign/logical keys, column dictionaries, and cross-store mapping are documented in [database-schema.md](database-schema.md).

## Measurable improvements

| Improvement | Evidence in this implementation |
|---|---|
| Easier developer setup | Maven Wrapper, Java 21, and Compose profiles replace server-specific setup. |
| Clearer operational visibility | Health probes, request IDs, structured logs, protected log search, telemetry API, and dashboard. |
| Better data safety | Optimistic locking, conditional inventory updates, transactions, idempotency, and a transactional notification outbox. |
| Lower migration risk | One business contract, Oracle and Mongo adapters, and E2E parity tests. |
| Better security posture | Spring Security, CSRF, roles, CSP/headers, and restricted admin diagnostics. |
| Stronger release confidence | Unit, integration, browser, and API tests run in CI for both data stores. |
