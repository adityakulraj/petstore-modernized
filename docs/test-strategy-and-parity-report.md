# Test Strategy and Legacy-to-Modern Parity Report

**Status:** planned before source baseline; update execution status as the implementation lands.

## Purpose

Prove the modern application preserves agreed PetStore behavior while adding explicit modern non-functional guarantees. This is a test plan and a parity evidence register, not a claim that the current empty workspace has executed tests.

## Test pyramid and environments

| Layer | Purpose | Tools/candidates | Run when |
|---|---|---|---|
| Unit | Domain rules, calculations, mapping, validation | JUnit 5, AssertJ, Mockito sparingly | every commit |
| Slice/web | Controller binding, validation, error contract, authorization | Spring MockMvc/WebTestClient | every commit |
| Integration | Mongo repositories, indexes, transactions, config | Testcontainers MongoDB | every commit/CI |
| Contract | OpenAPI and client compatibility | springdoc + contract tests | every PR |
| E2E | Browser journeys and critical UI/API integration | Playwright | every PR; smoke post-deploy |
| Parity | Compare legacy and modern observed outputs | shared fixtures + API/browser captures | per completed slice |
| Security/performance/resilience | modern quality attributes | dependency scan, OWASP tests, k6/Gatling, fault injection | CI/nightly/release |

## Legacy baseline versus modern test diff

| Area | Legacy evidence to capture | Modern automated coverage | Intentional modernization additions |
|---|---|---|---|
| Build/runtime | exact bootstrap, JDK/container, logs | Maven wrapper/container clean-start test | reproducible build and dependency scan |
| Navigation | JSP page routes, redirects, text/fields | route/UI smoke tests | accessibility and responsive UI checks |
| Catalog | categories, product detail, unknown IDs | API contracts, repository/query tests, E2E browse | indexes and latency assertions |
| Cart | session behavior, totals, quantity errors | unit totals; API/slice; E2E state persistence | optimistic locking, malicious payload tests |
| Checkout | validation, success outcome, duplicate click behavior | idempotency, order aggregate integration, E2E | retry safety, atomic stock policy, audit correlation |
| Orders | history/detail, ownership behavior | authorization/ownership integration and E2E | immutable price/address snapshots |
| Data | source schema, seed counts, referential assumptions | fixture transform/reconciliation tests | schema validators, indexes, backup/restore drill |
| Failures | legacy error pages/statuses | problem-detail contract tests | no stacktrace/secret leakage, graceful readiness |
| Operations | manual setup/deploy knowledge | config and health tests | structured logs, metrics, CI/CD, SBOM/scans |

## Functional test catalogue

### Catalog (CAT)

- CAT-001 list categories: stable sort/shape matches baseline.
- CAT-002 products by category: only correct category products; empty category behavior matches baseline.
- CAT-003 product/item detail: required attributes and price formatting/precision match baseline.
- CAT-004 unknown/malformed identifiers: exact chosen status/problem contract.
- CAT-005 search: blank, Unicode, case, special-character, long-query, no-result, pagination/sort behavior.
- CAT-006 concurrent product update/read: current catalog view is valid and no partial document is exposed.
- CAT-007 explain-plan/index assertion for category/detail/search critical queries.

### Cart (CART)

- CART-001 empty cart; CART-002 add product; CART-003 add same product policy (increment versus separate line); CART-004 update quantity; CART-005 remove line; CART-006 clear cart.
- CART-007 reject zero, negative, decimal, enormous, overflow, null, missing, and malformed quantities.
- CART-008 reject unknown/unavailable product; CART-009 server ignores tampered price/total/product display fields.
- CART-010 cart isolation between guests/customers; CART-011 login merge behavior if supported.
- CART-012 stale version/two-tab concurrent updates return deterministic conflict/no lost update.
- CART-013 precise decimal subtotal/tax/shipping/total, including rounding boundaries.

### Checkout/orders (ORD)

- ORD-001 authenticated valid checkout creates one order with correct line snapshots/totals.
- ORD-002 unauthenticated checkout; ORD-003 missing/invalid address/payment placeholder; ORD-004 empty cart.
- ORD-005 duplicate button/retry with same idempotency key creates no second order.
- ORD-006 same cart with different idempotency key follows documented state policy.
- ORD-007 stock boundary (last item), simultaneous buyers, and compensation/rollback behavior.
- ORD-008 order history default sort/pagination and detail shape.
- ORD-009 customer cannot read/modify another customer’s cart/order, including guessed IDs.
- ORD-010 catalog price/address changes after purchase do not mutate historical order.
- ORD-011 persistence failure/timeout: response safe, no half-created order, retry semantics verified.

### Identity, API, and configuration (SEC/OPS)

- SEC-001 password hashing; SEC-002 invalid credentials; SEC-003 session/token expiry; SEC-004 role and ownership matrix.
- SEC-005 mass assignment/unknown JSON fields; SEC-006 injection-shaped search input; SEC-007 CSRF/CORS policy appropriate to selected UI/auth model.
- SEC-008 errors/logs never disclose secrets, password hashes, stack traces, or full PII.
- OPS-001 missing required configuration fails fast with a clear safe message; OPS-002 profile separation; OPS-003 health liveness/readiness under database unavailable; OPS-004 correlation IDs.
- OPS-005 seed/reset repeatability; OPS-006 container cold start; OPS-007 backup/restore fixture reconciliation.

## Parity method

1. Run the legacy application from a pinned environment and capture route/request, preconditions, response status/body or screen, database effect, and error outcome for each test ID.
2. Convert captures to versioned sanitized fixtures. Prefer API assertions; use screenshots only where presentation behavior matters.
3. Run the same fixture against the modern app. Normalize irrelevant data (timestamps, generated IDs, HTML whitespace) but never normalize business values or errors.
4. Record `pass`, `intentional difference`, `blocked`, or `fail`; every intentional difference requires product/architecture rationale and panel-ready explanation.
5. Re-run all affected parity tests whenever a slice, dependency, schema, or shared validation rule changes.

## Parity evidence register

| ID | Legacy evidence | Modern test | Status | Notes |
|---|---|---|---|---|
| CAT-001 | pending baseline capture | pending | not started | capture route and display/API shape |
| CART-002 | pending baseline capture | pending | not started | determine duplicate-line legacy rule |
| CART-007 | pending baseline capture | pending | not started | capture validation and error behavior |
| ORD-001 | pending baseline capture | pending | not started | capture order persistence/result |
| ORD-005 | pending baseline capture | pending | not started | likely a new safety guarantee; document difference |
| ORD-009 | pending baseline capture | pending | not started | verify legacy ownership controls |

## Quality gates

- Pull request: compilation, formatter, unit/web/integration/contract tests, dependency/security scan, and changed-slice parity tests pass.
- Release candidate: E2E suite, all core parity rows complete, data reconciliation clean, performance baseline met, manual exploratory checkout completed, and demo reset rehearsed.
- Defect severity: block release for data loss, unauthorized access, duplicate order, wrong money calculation, checkout failure, or secret exposure. Record all other accepted risks with owner/date.

## What the panel should see

Run one characterization test side-by-side with the modern counterpart, show the parity row it satisfies, then navigate from controller to domain service to Mongo repository/index. This makes “functional equivalence” an auditable engineering claim rather than an assertion.
