# PetStore Modernized

A Java 21 / Spring Boot modernization of the Oracle Java Pet Store. The same catalog, cart, checkout, and order-history behavior runs against either Oracle Database or MongoDB. One variable selects the persistence implementation:

```bash
APP_STORE=oracle  # JPA + Oracle transactions and @Version
APP_STORE=mongo   # MongoDB documents, transactions, and @Version
```

The application uses Spring Boot 4.1.x, a modular-monolith package structure, same-origin storefront, administrator, and supplier UIs, externalized configuration, health probes, idempotent checkout and supplier hand-off, conditional inventory updates, optimistic concurrency, and persisted customer accounts.

## Fastest run: Docker Compose

Prerequisites: Docker Desktop with at least 6 GB available memory. Oracle needs more startup time and memory than MongoDB.

MongoDB mode:

```bash
docker compose --profile mongo up --build
```

Oracle mode:

```bash
docker compose --profile oracle up --build
```

Open [http://localhost:8080](http://localhost:8080). The local accounts are:

| Purpose | Username | Password |
|---|---|---|
| Customer 1 | `alice` | `petstore-demo` |
| Customer 2 | `aditya` | `password` |
| Supplier portal | `supplier` | `supplier` |
| Order approvals, operations dashboard, and logs | `admin` | `admin` |

These are demo-only defaults. Override every password outside local development.

Stop the selected stack without deleting its database volume:

```bash
docker compose --profile mongo down
docker compose --profile oracle down
```

The two app services intentionally share port 8080; run one profile at a time. Switching `APP_STORE` activates the matching Spring profile and excludes the unused database auto-configuration, so the application never needs both databases online or multiple PetStore app ports.

## Customer accounts

The storefront now supports self-service account registration and profile management at **Account** in the UI. Customer data is persisted in the selected store (MongoDB or Oracle), and passwords are stored as BCrypt hashes only. A customer can manage their contact details, default shipping address, preferred language/category, and display preferences.

The API is:

```text
POST /api/v1/accounts       Register a customer (public)
GET  /api/v1/accounts/me    Read the signed-in customer's profile
PUT  /api/v1/accounts/me    Update the signed-in customer's profile
```

Registration is deliberately exempt from CSRF because it cannot operate as an
existing signed-in user. Every authenticated mutation, including profile updates,
continues to require the normal CSRF cookie/token. Account registration and
profile updates produce structured `account.registered` / `account.profile.updated`
log events with request and correlation IDs. Their `account.by_username` and
`account.save` database operations also appear in the protected operations
dashboard telemetry.

## Administrator order approvals

Open [http://localhost:8080/admin/orders.html](http://localhost:8080/admin/orders.html) and sign in with `admin` / `admin`. Orders below the configurable `$500.00` threshold are automatically approved. Orders at or above the threshold remain `PENDING` until an administrator approves or denies them. Approval creates exactly one supplier PO; denial restores reserved inventory exactly once.

Decisions are versioned, CSRF-protected, and replay-safe. Two opposite decisions have one winner and one HTTP 409 conflict, while duplicate same-decision requests converge on the committed result. Decision logs and database timings appear in the existing operations dashboard. See [the state machine, concurrency diagrams, and manual walkthrough](docs/feature-admin-order-approval.md).

## Supplier portal

Open [http://localhost:8080/supplier/](http://localhost:8080/supplier/) and sign in with `supplier` / `supplier`.
The supplier can view and replace absolute inventory quantities, view purchase orders created by customer
checkout, and process each purchase order. Inventory PUT retries are idempotent, stale competing versions receive
HTTP 409, one customer order creates one durable supplier PO, and concurrent process retries converge on one
`PROCESSED` result. The matching customer order becomes `COMPLETED` in the same database transaction.

The supplier flow, reliability decisions, diagrams, and manual walkthrough are documented in
[docs/feature-supplier-portal.md](docs/feature-supplier-portal.md).

## Run the application outside Docker

Use JDK 21. The repository includes a Maven 3.9.11 wrapper, so a separate Maven installation is unnecessary. Start one database service first:

```bash
docker compose --profile mongo up -d mongo
APP_STORE=mongo MONGODB_URI='mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true' \
  ./mvnw spring-boot:run
```

or:

```bash
docker compose --profile oracle up -d oracle
APP_STORE=oracle ORACLE_URL='jdbc:oracle:thin:@localhost:1521/FREEPDB1' \
  ORACLE_USERNAME=petstore ORACLE_PASSWORD=petstore_local_only ./mvnw spring-boot:run
```

## Search local logs by request ID

Every response includes `X-Request-ID` and `X-Correlation-ID`. Supply your own safe request ID when reproducing a problem, then query the JSON log through the admin-only endpoint:

```bash
curl -i -H 'X-Request-ID: checkout-demo-123' \
  http://localhost:8080/api/v1/catalog/products/AV-CB-01

curl -u admin:admin \
  'http://localhost:8080/api/v1/admin/logs?requestId=checkout-demo-123'
```

Return the most recent 200 entries without filtering:

```bash
curl -u admin:admin 'http://localhost:8080/api/v1/admin/logs?limit=200'
```

For a host-run app, the same newline-delimited JSON is in `logs/petstore.log`. In Docker it is persisted in the project `app-logs` volume at `/app/logs/petstore.log`. The endpoint accepts only an optional request ID and a limit from 1 to 1000; callers cannot choose a file path. Customer accounts receive HTTP 403 and anonymous calls receive HTTP 401.

## Health and database performance dashboard

Sign in with `admin/admin` and open [http://localhost:8080/admin/health.html](http://localhost:8080/admin/health.html).

The page refreshes every five seconds and shows application status, process uptime, requests/minute, rolling 4xx and 5xx rates, average/max HTTP latency, JVM heap/threads, connection-pool utilization, and per-store-operation calls/failures/retries/latency. It also shows read-only query-plan diagnostics: MongoDB `executionStats` including `COLLSCAN`, `IXSCAN`, documents and keys examined; Oracle optimizer scan type, index, estimated rows, and cost. Query plans are cached for 60 seconds so dashboard polling does not repeatedly run `explain` work.

The JSON source is also admin-only:

```bash
curl -u admin:admin http://localhost:8080/api/v1/admin/health
```

HTTP and query telemetry is deliberately local and in memory: it resets on application restart and covers the current process, with a rolling 60-minute HTTP graph. Dashboard polls and Actuator probes are excluded from traffic calculations so they do not manufacture their own throughput. This is appropriate for the take-home's local diagnostics; production history should be exported to the organization's metrics platform.

## Database pooling, retries, and indexes

Both implementations use bounded connection pools. Oracle uses HikariCP; one MongoClient supplies the MongoDB Java driver's built-in pool. The defaults are 1 minimum and 10 maximum connections, a 10-second acquisition wait, and five-minute maximum idle time. Pool size and live active/idle/waiting counts are visible on the dashboard.

Transient failures use a maximum of five attempts with capped exponential equal jitter (25 ms initial, 500 ms cap). The half-cap delay floor prevents a burst of immediate retries from exhausting the budget while a competing transaction is still committing. Retries are limited to reads and transactionally idempotent checkout, supplier, and administrator-decision operations. Cart mutations are observed but never blindly replayed after an ambiguous commit, because doing so could apply a non-idempotent quantity change twice. MongoDB driver `retryReads` and `retryWrites` also remain enabled.

Indexes match the real query shapes in both stores: product category; customer + idempotency key (unique); customer + descending creation time; Oracle cart-line customer and order-line order joins; plus each store's primary-key indexes. The category API now pushes category filtering into the selected database so its index is actually used. The unconstrained seven-row catalog query intentionally remains a full scan; arbitrary substring search preserves legacy behavior and is filtered after that small result set rather than pretending a B-tree index can accelerate contains-anywhere matching.

## Run the isolated E2E suites

Install the Node dependencies once:

```bash
npm ci
```

Run every API contract against MongoDB, Oracle, or both:

```bash
npm run e2e:api:mongo
npm run e2e:api:oracle
npm run e2e:api:all
```

The runner builds and starts a disposable Compose project on non-default ports, executes tests serially, resets carts/customer orders/supplier purchase orders/inventory before and after every test, verifies all seven product stock/version values after each reset, and deletes only the disposable test volumes. It refuses to reset a project whose name does not start with `petstore-e2e-`, so your normal local database is not a valid reset target.

The API suite covers public catalog/session APIs, authentication and authorization for customer/admin/supplier roles, CSRF, customer registration/profile lifecycle, both demo accounts, cart CRUD and validation, order history, checkout rollback, out-of-stock behavior, stale versions, two-user inventory races, simultaneous cart updates, concurrent idempotency retries, administrator approval/denial races and inventory restoration, supplier inventory replacement, and concurrent supplier PO processing. Run API plus browser tests with:

```bash
npx playwright install chromium
npm run e2e:mongo
npm run e2e:oracle
```

See the [complete testing guide and executable coverage matrix](e2e/README.md) for every scenario and direct links to its test code.

See [the Oracle and MongoDB schema diagrams](docs/database-schema.md) for every table/collection, column/field, primary key, enforced foreign key, logical reference, unique constraint, and index.

Oracle normally needs about 2 GB by itself. On a 6 GB Docker VM, stop another local Oracle container before running the isolated Oracle suite; `docker compose stop oracle` preserves its data and `docker compose start oracle` restores it.

## Verify Java tests

```bash
./mvnw verify
```

Java integration tests use Testcontainers and are automatically skipped when Docker is unavailable. CI runs the unit/integration tests and both full E2E implementations independently.

If your network blocks Testcontainers from downloading its optional Ryuk cleanup image, run `TESTCONTAINERS_RYUK_DISABLED=true ./mvnw verify`. JUnit still stops its containers normally; use this local fallback only when the standard command reports the database contracts as skipped.

Health probes:

```bash
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

## Configuration reference

| Variable | Default | Purpose |
|---|---|---|
| `APP_STORE` | `oracle` | Selects `oracle` or `mongo`; invalid values fail startup. |
| `ORACLE_URL` | `jdbc:oracle:thin:@localhost:1521/FREEPDB1` | Oracle JDBC target. |
| `ORACLE_USERNAME` | `petstore` | Oracle application user. |
| `ORACLE_PASSWORD` | local-only value | Oracle application password. |
| `MONGODB_URI` | `mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true` | Host-run MongoDB target; the replica-set option enables transactions and direct connection avoids resolving the container-only member hostname. |
| `DB_POOL_MIN_SIZE` | `1` | Minimum idle connections for MongoDB and Oracle pools. |
| `DB_POOL_MAX_SIZE` | `10` | Maximum connections for MongoDB and Oracle pools. |
| `DB_POOL_MAX_WAIT_MS` | `10000` | Maximum wait for a pooled connection. |
| `DB_POOL_MAX_IDLE_MS` | `300000` | MongoDB connection idle limit; Oracle uses the same default. |
| `DB_RETRY_MAX_ATTEMPTS` | `5` | Total attempts for a classified transient, retry-safe operation. |
| `DB_RETRY_INITIAL_DELAY` | `25ms` | Initial exponential-backoff ceiling. |
| `DB_RETRY_MAX_DELAY` | `500ms` | Maximum jittered retry delay. |
| `DEMO_USERNAME` | `alice` | Local form-login user. |
| `SUPPLIER_USERNAME` | `supplier` | Local supplier portal user. |
| `SUPPLIER_PASSWORD` | `supplier` | Local supplier portal password; override outside local development. |
| `DEMO_PASSWORD` | `petstore-demo` | Local form-login password; override outside a demo. |
| `DEMO_ADDITIONAL_USERNAME` | `aditya` | Additional local form-login user. |
| `DEMO_ADDITIONAL_PASSWORD` | `password` | Additional local form-login password; override outside a demo. |
| `ADMIN_USERNAME` | `admin` | Local log-viewer username. |
| `ADMIN_PASSWORD` | `admin` | Local log-viewer password; override outside a demo. |
| `ORDER_APPROVAL_THRESHOLD` | `500.00` | Orders below this total auto-approve; orders at or above it require administrator review. |
| `LOG_FILE` | `logs/petstore.log` | JSON log file read by the protected endpoint. |
| `LOG_MAX_FILE_SIZE` | `10MB` | Maximum size before the local log rotates. |
| `LOG_MAX_HISTORY` | `7` | Number of rotated local log files retained. |
| `JPA_DDL_AUTO` | `update` | Local Oracle schema mode; use controlled migrations in production. |

## Troubleshooting

- **Port 8080 is already in use:** stop the other profile before switching stores. `docker compose --profile mongo down` and `docker compose --profile oracle down` retain data volumes.
- **MongoDB says transactions are unsupported:** use the Compose service or connect to a replica set. A standalone `mongod` cannot execute checkout transactions.
- **Oracle is still starting:** the first boot may take several minutes. Check `docker compose --profile oracle ps` and `docker compose --profile oracle logs oracle`.
- **Oracle exits on Apple Silicon:** if your shell globally forces `DOCKER_DEFAULT_PLATFORM=linux/amd64`, unset it or set it to `linux/arm64`. The E2E runner detects the Docker server architecture automatically.
- **Oracle E2E exits while another Oracle is running:** the Docker VM is usually out of memory. Pause the normal container with `docker compose stop oracle`, run the suite, then use `docker compose start oracle`; the volume is retained.
- **Docker reports `x509: certificate signed by unknown authority`:** Docker's VM does not trust your network's certificate authority. Add the organization-approved root CA to Docker/Rancher Desktop and restart it; do not disable TLS verification.
- **Reset demo data:** after confirming no needed local data remains, remove only this project's volumes with `docker compose --profile <oracle|mongo> down --volumes`, then start again.
- **Dashboard asks for credentials:** use the operations account (`admin/admin` by default), not either customer account. Direct navigation returns to the dashboard after login.

## Concurrency and consistency contract

- Every cart response contains a `version`; the client must return it on a mutation. A stale version receives HTTP 409 instead of overwriting a concurrent change.
- Product inventory is decremented with `stock >= requestedQuantity` in the database predicate. Concurrent buyers cannot drive inventory below zero.
- Checkout, inventory decrement, order insert, and cart clear share one database transaction. MongoDB runs as a replica set because transactions are unavailable on standalone deployments.
- `(customerId, idempotencyKey)` is unique. Repeating a successful checkout key returns the original order and cannot create a second order.
- Order lines and shipping address are purchase-time snapshots, so later catalog/profile changes do not rewrite history.
- High-value checkout reserves stock as `PENDING`; approval emits one supplier PO, while denial restores stock in the decision transaction. Decision replays are idempotent and opposite races have one winner.
- The UI calculates nothing authoritative: quantities are validated and totals are recomputed from server-held prices.

## Navigation for the panel

| Concern | Location |
|---|---|
| Shared persistence contract | `shared/application/StorefrontStore.java` |
| Business orchestration | `shared/application/StorefrontService.java` |
| Administrator approval orchestration | `orders/application/AdminOrderService.java`, `orders/application/AdminOrderStore.java` |
| Cart rules | `cart/domain/Cart.java` |
| Request validation | `cart/api/CartController.java`, `orders/api/OrderController.java` |
| Oracle transactions | `persistence/oracle/OracleStorefrontStore.java` |
| MongoDB transactions | `persistence/mongo/MongoStorefrontStore.java` |
| Database selection | `application.yml`, `application-oracle.yml`, `application-mongo.yml` |
| Security | `config/SecurityConfig.java` |
| HTTP/query telemetry and retry policy | `observability/` |
| MongoDB pool and explain diagnostics | `persistence/mongo/MongoObservabilityConfig.java`, `MongoDatabaseDiagnostics.java` |
| Oracle pool and explain diagnostics | `persistence/oracle/OracleDatabaseDiagnostics.java` |
| Operations dashboard | `static/admin/health.html`, `static/assets/health.js` |
| Approval dashboard | `static/admin/orders.html`, `static/assets/admin-orders.js` |
| UI | `src/main/resources/static/` |
| Tests | `src/test/` and `e2e/` |

