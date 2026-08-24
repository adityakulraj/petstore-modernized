# PetStore Modernization Implementation PRD

**Status:** implementation-ready specification | **Depends on:** `docs/modernization-approach-prd.md`

## Goal

Deliver a Java 21 Spring Boot PetStore that preserves agreed legacy behavior and is demonstrably runnable, testable, secure-by-default, and backed by MongoDB.

## Personas and journeys

| Persona | Journey | Acceptance criteria |
|---|---|---|
| Guest | Browse category, product, item | Catalog is available without authentication; invalid/missing ID produces a consistent 404. |
| Customer | Add/update/remove items in cart | Quantities are positive bounded integers; a cart recalculates totals server-side. |
| Customer | Checkout | Requires authenticated customer; validates address/inventory policy; creates one durable order per idempotency key. |
| Customer | View order history/detail | Customer can access only their own orders; order uses immutable purchase-time snapshots. |
| Operator | Diagnose service | Liveness/readiness and structured logs make failures actionable without exposing secrets. |

## Functional requirements

### FR-1 Catalog

Expose `GET /api/v1/categories`, `GET /api/v1/categories/{id}/products`, `GET /api/v1/products/{id}`, and defined search behavior. Return stable DTOs, never persistence entities. Unknown IDs return RFC 9457-style problem details. Product price uses decimal currency values, never binary floating point.

### FR-2 Cart

Expose authenticated/session-scoped cart read/add/update/remove operations. Reject quantity <=0, missing product, unavailable product, malformed payload, and concurrent stale updates. Recalculate total from server-side canonical prices; the client-supplied total is ignored.

### FR-3 Checkout and orders

`POST /api/v1/orders` transforms the active cart into an order atomically according to the selected inventory policy. Require an idempotency key and return the original successful order for a safe retry. Persist price/address/product snapshots, status, timestamp, customer reference, and audit correlation ID. Clear or mark the cart according to the documented legacy parity rule.

### FR-4 Identity and authorization

Use Spring Security with a deliberately chosen local-demo authentication approach. Passwords are salted hashes; authorization is enforced in services as well as route configuration. No endpoint allows horizontal access to another customer’s cart/order.

### FR-5 Operations

Provide `/actuator/health/liveness`, `/actuator/health/readiness`, metrics, JSON logs, request/correlation IDs, sanitized error responses, and configuration profiles for `local`, `test`, and `prod`.

## Non-functional requirements

- Java 21; latest stable Spring Boot selected and pinned during repository bootstrap.
- Run: `./mvnw verify`, `docker compose up --build`, then documented browser/API smoke journey.
- MongoDB connection and all secrets supplied through environment variables or a local ignored `.env`; committed `.env.example` contains placeholders only.
- Testcontainers runs Mongo integration tests without a shared developer database.
- OpenAPI describes all public endpoints and examples. API changes are versioned or backward-compatible.
- Dependencies are pinned, scanned, and periodically reviewed. CI fails on test failure and publishes test reports.

## Repository contract

```text
petstore-modernized/
  src/main/java/.../{catalog,cart,orders,identity,shared}/
  src/main/resources/{application.yml,db/}
  src/test/java/.../{unit,integration,contract,e2e}/
  docker-compose.yml
  Dockerfile
  .github/workflows/ci.yml
  docs/{adr,parity,ai-decision-log}/
  README.md
```

Within each module: `api` (controller/DTO), `application` (use cases), `domain` (rules/ports), `infrastructure` (Mongo adapters/config). Dependencies point inward; controllers never query Mongo directly.

## API behavior contract

| Condition | Status | Response requirement |
|---|---:|---|
| Valid read/create | 200/201 | DTO plus correlation ID header |
| Invalid request | 400 | field-level problem details; no stack trace |
| Unauthenticated | 401 | no existence disclosure |
| Unauthorized ownership | 403 or legacy-equivalent 404, decided once | documented and tested consistently |
| Unknown resource | 404 | problem details |
| Stale cart/version conflict | 409 | client can refresh and retry |
| Duplicate checkout key | 200/201 | original order representation; no duplicate side effect |
| Unexpected dependency failure | 503/500 | sanitized error, structured server log |

## Implementation backlog

1. Bootstrap build, wrappers, formatter/linter, quality gate, Dockerfile/Compose, profiles, health, README.
2. Import/seed fixture data; write ADR-001 (modular monolith), ADR-002 (Mongo aggregates), ADR-003 (authentication), and parity matrix.
3. Build catalog vertical slice with Mongo indexes and contract tests.
4. Build cart vertical slice with server-side calculations and concurrency controls.
5. Build checkout/order slice with idempotency and transaction/reservation decision documented in ADR-004.
6. Add UI, accessibility checks, E2E journeys, observability, security hardening, performance baseline, and presentation rehearsal.

## Definition of done for every slice

- Requirement and legacy behavior are linked in the parity matrix.
- DTO validation, domain rule, repository behavior, and error contract have tests at the appropriate level.
- Logging does not reveal passwords, tokens, or PII.
- OpenAPI, README, and seed/reset path are updated.
- CI passes; review includes an AI decision-log entry if AI materially contributed.

## Deferred decisions to make explicitly

- UI choice (React versus server-side MVC) after inspecting the legacy routes and presentation requirements.
- Guest cart identity and merge-on-login semantics.
- Exact inventory behavior if legacy source lacks a real reservation model.
- Authentication implementation appropriate to a demonstration versus production SSO.
- Atlas deployment topology; this is not needed to prove functional modernization locally.
