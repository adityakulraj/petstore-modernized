# PetStore Testing Guide

This is the source-of-truth inventory for the automated test suite. The same Playwright API and browser specifications run unchanged against MongoDB and Oracle; the Java persistence contract also runs once per store.

## Test matrix and commands

| Layer | MongoDB | Oracle | Command |
|---|---:|---:|---|
| Java unit + persistence integration | Yes | Yes | `./mvnw verify` |
| API E2E only | 21 tests | 21 tests | `npm run e2e:api:mongo`, `npm run e2e:api:oracle` |
| API + real-browser E2E | 28 tests | 28 tests | `npm run e2e:mongo`, `npm run e2e:oracle` |
| Both API implementations | 42 executions | — | `npm run e2e:api:all` |

Install prerequisites once:

```bash
npm ci
npx playwright install chromium
```

The full local verification is:

```bash
./mvnw verify
npm run e2e:mongo
npm run e2e:oracle
```

`./mvnw verify` currently executes 39 JUnit tests: nineteen unit/service/telemetry tests plus the ten-test persistence contract against each database. The two full Playwright runs execute 56 tests. Together, the matrix is 95 test executions across 57 unique specifications.

Latest local verification (August 26, 2026):

| Command | Result |
|---|---:|
| Java unit/service tests | 19 passed, 0 failed |
| MongoDB ten-scenario persistence contract | 10 passed, 0 failed |
| Oracle ten-scenario persistence contract | 10 passed, 0 failed |
| MongoDB API + Chromium E2E | 28 passed, 0 failed |
| Oracle API + Chromium E2E | 28 passed, 0 failed |
| Live MongoDB service on port 8080 | Healthy; approval page/API and telemetry API all HTTP 200 |
| Live Oracle service on port 8081 | Healthy; approval page/API and telemetry API all HTTP 200 |

## Isolation and inventory guarantee

The E2E runner creates only `petstore-e2e-mongo` or `petstore-e2e-oracle`, using ports 18080/37017 and 18081/2521 respectively. The guard rejects any non-isolated project name. See [the runner](../scripts/run-e2e.sh#L16) and [the reset guard](./support/store-state.js#L13).

Before and after every API or browser test, the fixture:

1. Deletes all carts, customer orders, supplier purchase orders, line snapshots, and non-demo customer accounts from the disposable database.
2. Restores all seven product stock values and their versions to zero.
3. Calls the public catalog API and asserts every stock/version value.
4. Repeats the reset and assertion in a `finally` block even when the test fails.
5. Deletes the disposable test volumes when the suite exits.

The exact implementation is in [fixtures.js](./support/fixtures.js#L4), with the [MongoDB reset](./support/store-state.js#L37) and [Oracle reset](./support/store-state.js#L52). The normal local `petstore-modernized` volumes are never reset by these tests.

Seed inventory asserted after every test:

| Product | Stock | Version |
|---|---:|---:|
| `FI-SW-01` | 25 | 0 |
| `FI-SW-02` | 12 | 0 |
| `K9-BD-01` | 4 | 0 |
| `K9-RT-01` | 5 | 0 |
| `FL-DSH-01` | 8 | 0 |
| `AV-CB-01` | 10 | 0 |
| `RP-IG-01` | 6 | 0 |

## API E2E coverage

Every row below runs against both MongoDB and Oracle. The link points to the executable test, and the test title is stable for command-line filtering with `--grep`.

| # | Executable test | Cases and expected results |
|---:|---|---|
| 1 | [Public session, catalog, categories, filters, and product lookup](./api-contract.spec.js#L73) | Anonymous session reports unauthenticated and the selected store; catalog returns seven products in stable order with seed stock/version; categories return all five values; category filter is case-insensitive; query is trimmed and case-insensitive; blank filters return all products; known product returns its snapshot; unknown product returns 404 Problem Details. |
| 2 | [Safe correlation/request IDs](./api-contract.spec.js#L110) | A valid legacy `X-Correlation-ID` is echoed in both response headers; `X-Request-ID` takes precedence when both are supplied; a value longer than 64 characters is rejected and replaced with a UUID. |
| 3 | [Admin structured-log search](./api-contract.spec.js#L127) | A request is logged with the supplied request ID; anonymous access is rejected; a customer receives 403; admin form login succeeds; `admin/admin` Basic auth succeeds; search returns the matching HTTP event and fields; an unsafe request-ID filter receives 400. |
| 4 | [Admin health, pool, telemetry, and query plans](./api-contract.spec.js#L174) | Anonymous access is rejected and customer receives 403; admin receives UP/store/uptime, exactly 60 traffic buckets, JVM data, pool range/utilization, observed query timings, and cached explain plans; a known 404 is reflected in client-error telemetry. |
| 5 | [Authentication and CSRF enforcement](./api-contract.spec.js#L225) | Anonymous cart, order-history, and CSRF reads receive 401 with a Basic challenge; anonymous mutation receives 403; authenticated mutation with missing or invalid CSRF receives 403; rejected mutations leave the cart empty. |
| 6 | [Invalid login, both customers, and logout](./api-contract.spec.js#L254) | Wrong password redirects to `login?error` and leaves the session anonymous; `alice/petstore-demo` authenticates; `aditya/password` authenticates; the session reports the correct username/store; CSRF-protected logout returns 204 and invalidates the session. |
| 7 | [Customer cart isolation](./api-contract.spec.js#L288) | Alice adds two Canary items; Aditya's cart remains empty. |
| 8 | [Cart CRUD, totals, snapshots, and versions](./api-contract.spec.js#L303) | New cart is empty at version 0; add returns version 1 and exact price/total; update returns version 2 and recomputed total; delete returns version 3, zero total, and no lines. |
| 9 | [Cart input validation](./api-contract.spec.js#L335) | Unknown product returns 404; blank product, quantity 0, quantity 100, negative expected version, and unknown JSON field return 400; malformed JSON returns 400; no rejected request mutates the cart. |
| 10 | [Missing cart lines and stale versions](./api-contract.spec.js#L361) | Updating or deleting a missing line returns 404; delete without `expectedVersion` returns 400; a valid update succeeds; reusing its stale version returns 409 with an explanatory detail. |
| 11 | [Simultaneous cart-update race](./api-contract.spec.js#L395) | Two sessions for Alice update from the same version concurrently; exactly one receives 200 and one 409; the winning quantity is retained and the cart version increments exactly once. |
| 12 | [Checkout validation](./api-contract.spec.js#L416) | Missing, blank, or over-100-character idempotency keys return 400; invalid nested address returns field-level Problem Details; unknown JSON field returns 400; checkout of an empty cart returns 409. |
| 13 | [Successful, atomic, idempotent checkout](./api-contract.spec.js#L443) | Checkout returns 201 with immutable product/address/price snapshots and exact total; retrying the key returns the same order ID; only one history row exists; cart is cleared; stock decrements once and inventory version increments once. |
| 14 | [Stale-cart checkout](./api-contract.spec.js#L473) | Checkout with an old cart version returns 409; product stock/version is unchanged; no order is created. |
| 15 | [Insufficient-stock rollback](./api-contract.spec.js#L491) | A multi-line cart has one available and one over-quantity item; checkout returns 409; the earlier candidate decrement is rolled back; both product versions remain zero; cart is preserved; no order exists. |
| 16 | [Two-customer last-inventory race](./api-contract.spec.js#L509) | Alice and Aditya each request all four Bulldogs and checkout concurrently; exactly one receives 201 and one 409; stock ends at zero, never negative; exactly one order exists; winner's cart clears while loser's cart remains. |
| 17 | [Concurrent same-key idempotency race](./api-contract.spec.js#L533) | Two sessions checkout the same cart with one key concurrently; both receive 201 with the same order ID; only one order is stored; stock and inventory version change exactly once. |
| 18 | [Customer-scoped idempotency and history](./api-contract.spec.js#L554) | Alice and Aditya reuse the same idempotency key; distinct orders are created because uniqueness includes customer ID; each user sees only their own order; stock decrements twice. |
| 19 | [Administrator approval lifecycle](./api-contract.spec.js) | Anonymous/customer access is rejected; a high-value checkout stays `PENDING` without a supplier PO; duplicate approvals converge on one version and one PO; an opposite decision conflicts; denial replay restores reserved stock exactly once and never creates a PO; CSRF remains mandatory. |
| 20 | [Supplier authorization, inventory, and purchase-order processing](./api-contract.spec.js) | Anonymous/customer access is rejected; `supplier/supplier` receives only the supplier role; inventory PUT is replay-safe and rejects a stale competing value; checkout creates exactly one PO; two simultaneous processing requests both converge on one `PROCESSED` version; the customer order becomes `COMPLETED`; CSRF remains mandatory. |
| 21 | [Customer account lifecycle](./api-contract.spec.js) | Invalid registration is rejected; a valid account is persisted without exposing its password hash; duplicates conflict; the new credentials authenticate; profile read/update persists address and preferences; CSRF remains mandatory. |

All HTTP endpoints are exercised: `GET /api/v1/session`, `GET /api/v1/csrf`, all three catalog routes, `GET /api/v1/cart`, all three cart mutation routes, `GET/POST /api/v1/orders`, supplier inventory and purchase-order routes, administrator order list/decision routes, `GET /api/v1/admin/logs`, `GET /api/v1/admin/health`, `/supplier/`, `/admin/orders.html`, `/admin/health.html`, `/login`, and `/logout`.

Run one API scenario against one store, for example:

```bash
E2E_GREP='insufficient inventory' npm run e2e:api:mongo
```

The standard runner executes the full file. For ad hoc filtering while its stack is already active, pass Playwright `--grep` directly.

## Browser E2E coverage

These tests use a real Chromium instance and the same before/after inventory fixture.

| # | Executable test | Cases and expected results |
|---:|---|---|
| 1 | [Complete customer purchase journey](./petstore.spec.js#L3) | Landing page and seven cards render; stock is visible; Alice signs in through the form; adds an item; opens cart; opens checkout dialog; places the order once; order confirmation/history appears; visible inventory changes from 10 to 9. |
| 2 | [Catalog search](./petstore.spec.js#L28) | Searching `iguana` filters seven cards to one Green Iguana without a server-side state mutation. |
| 3 | [Additional customer login](./petstore.spec.js#L35) | Aditya signs in with the configured second demo account and the UI displays the correct identity. |
| 4 | [Admin health dashboard](./petstore.spec.js#L44) | Anonymous navigation reaches login; `admin/admin` returns to the saved dashboard; UP status, five summary cards, three SVG graphs, live pool/query telemetry, optimizer plans, and the logs link render in real Chromium. |
| 5 | [Administrator approval-queue journey](./petstore.spec.js) | A high-value checkout creates a pending order; anonymous navigation resumes after `admin/admin` login; the live summary and order card render; approving removes it from pending, adds it to history, and confirms supplier release. |
| 6 | [Supplier portal journey](./petstore.spec.js) | A storefront checkout creates a PO; anonymous portal navigation resumes after `supplier/supplier` login; seven inventory rows render; an inventory update persists; the PO is visible and can be processed to `PROCESSED`. |
| 7 | [Customer account browser journey](./petstore.spec.js) | A visitor registers through the UI, signs in with the new credentials, sees the persisted profile, updates it, reloads, and sees the update retained. |

## Java unit and service coverage

| Executable test | Cases and expected results |
|---|---|
| [Cart combines duplicates and calculates money exactly](../src/test/java/com/mongodb/modernization/petstore/cart/domain/CartTest.java#L15) | Adding the same product twice merges the line and uses decimal-safe arithmetic (`49.50`). |
| [Cart rejects boundary violations](../src/test/java/com/mongodb/modernization/petstore/cart/domain/CartTest.java#L23) | Quantities 0 and 100 are rejected. |
| [Cart prevents accumulated overflow](../src/test/java/com/mongodb/modernization/petstore/cart/domain/CartTest.java#L31) | Adding one to an existing quantity of 99 is rejected. |
| [Cart requires an existing line](../src/test/java/com/mongodb/modernization/petstore/cart/domain/CartTest.java#L37) | Update and remove of a missing product both throw the domain not-found exception. |
| [Service short-circuits idempotent retries and repairs hand-off](../src/test/java/com/mongodb/modernization/petstore/shared/application/StorefrontServiceTest.java) | An existing order is returned without invoking checkout again, while its supplier PO hand-off is safely retried. |
| [Strict administrator approval threshold](../src/test/java/com/mongodb/modernization/petstore/orders/domain/OrderTest.java) | `$499.99` auto-approves while exactly `$500.00` and higher remain pending, matching the legacy strict comparison. |
| [Administrator decision orchestration](../src/test/java/com/mongodb/modernization/petstore/orders/application/AdminOrderServiceTest.java) | Approval triggers the idempotent supplier handoff; denial does not. |
| [Security creates encoded, role-separated users](../src/test/java/com/mongodb/modernization/petstore/config/SecurityConfigTest.java#L10) | Customer, administrator, and supplier passwords match their BCrypt hashes and each principal has only its intended role. |
| [Supplier PO snapshot and idempotent transition](../src/test/java/com/mongodb/modernization/petstore/supplier/domain/SupplierPurchaseOrderTest.java) | An immutable PO snapshots the customer order; `READY` becomes `PROCESSED`; repeating the domain transition has no effect. |
| [HTTP telemetry classification and self-exclusion](../src/test/java/com/mongodb/modernization/petstore/observability/RequestTelemetryTest.java#L18) | 2xx/4xx/5xx counts, rates, average/max latency, and 60 buckets are exact; dashboard polls and Actuator probes do not inflate their own graphs. |
| [HTTP telemetry rolling expiry](../src/test/java/com/mongodb/modernization/petstore/observability/RequestTelemetryTest.java#L41) | The oldest bucket expires at 60 minutes while lifetime counts remain. |
| [Concurrent HTTP telemetry](../src/test/java/com/mongodb/modernization/petstore/observability/RequestTelemetryTest.java#L56) | Eight threads record 16,000 requests without lost updates. |
| [Transient retry and telemetry](../src/test/java/com/mongodb/modernization/petstore/observability/DatabaseExecutorTest.java#L17) | SQL transient failures and Spring-wrapped MongoDB `TransientTransactionError` labels are retried; the safe operation succeeds on attempt three and reports exactly two retries. |
| [Unsafe and deterministic failures are not retried](../src/test/java/com/mongodb/modernization/petstore/observability/DatabaseExecutorTest.java#L46) | An ambiguous cart write and a duplicate constraint each execute once and are counted as failures. |
| [Capped exponential equal jitter](../src/test/java/com/mongodb/modernization/petstore/observability/DatabaseExecutorTest.java#L66) | Every sampled delay remains between half the exponential ceiling and the ceiling, capped at 200 ms. |

## Shared persistence contract

Each test below runs once through `MongoStorefrontStore` and once through `OracleStorefrontStore`; see [MongoDB integration wiring](../src/test/java/com/mongodb/modernization/petstore/persistence/MongoStorefrontIntegrationTest.java) and [Oracle integration wiring](../src/test/java/com/mongodb/modernization/petstore/persistence/OracleStorefrontIntegrationTest.java).

| Executable contract | Cases and expected results |
|---|---|
| [Seeded catalog and optimistic cart version](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java#L29) | At least seven products exist; cart mutation advances the version; a stale update throws `StoreConflictException`. |
| [Atomic and idempotent checkout](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java#L41) | Same customer/key returns one order; cart clears; order history contains exactly that order. |
| [Simultaneous cart writes](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java#L54) | Two threads update the same version behind a latch; exactly one succeeds and one conflicts. |
| [Simultaneous buyers](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java#L78) | Two threads attempt to buy all remaining stock; one order is placed, one receives insufficient stock, and final stock is exactly zero. |
| [Replay-safe supplier inventory](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java) | Absolute inventory replacement advances the optimistic version once; an identical retry succeeds without another write; a stale competing value receives a conflict. |
| [Idempotent supplier PO lifecycle](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java) | One customer order maps to one supplier PO; create and process replays return the same records; the customer order becomes `COMPLETED`. |
| [Concurrent supplier processing](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java) | Two threads process one READY version; both converge on the same stored `PROCESSED` result and no replay advances the version. |
| [Concurrent same-key checkout replay](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java) | Two threads submit the same customer cart and idempotency key; both return the same order, stock decrements once, and exactly one order is stored. Oracle serializes this boundary with a per-cart row lock and re-checks the key after acquiring it. |
| [Replay-safe approval and denial](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java) | High-value checkout creates no PO until approval; approval replay creates one PO; denial replay restores stock once; reversing a committed decision conflicts. |
| [Concurrent approve-versus-deny race](../src/test/java/com/mongodb/modernization/petstore/persistence/StorefrontStoreContract.java) | Two opposite decisions start behind one latch; exactly one commits, one conflicts, and final inventory/PO state matches the winning decision. |

## Failure artifacts and diagnostics

- The runner prints Compose database/application logs before cleanup on failure.
- Playwright retains a screenshot on failure and a trace on the first retry.
- CI uploads separate `playwright-report-mongo`, `playwright-report-oracle`, and `java-test-reports` artifacts.
- Send a known `X-Request-ID` and query `GET /api/v1/admin/logs?requestId=...` with `admin/admin` when diagnosing an HTTP failure.
- Open `/admin/health.html` with `admin/admin` to correlate HTTP errors, pool pressure, retries, operation latency, and optimizer scan choices.
- The supplier flow diagrams, guarantees, and manual walkthrough are in [feature-supplier-portal.md](../docs/feature-supplier-portal.md).
- The administrator approval state machine, race guarantees, observability, and manual walkthrough are in [feature-admin-order-approval.md](../docs/feature-admin-order-approval.md).

The E2E suites use one worker because the reset fixture owns one disposable database. Concurrency is created deliberately inside the race tests with simultaneous requests/threads, so serial test scheduling does not reduce race-condition coverage.
