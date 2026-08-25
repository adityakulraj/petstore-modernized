# PetStore Modernized

A Java 21 / Spring Boot modernization of the Oracle Java Pet Store. The same catalog, cart, checkout, and order-history behavior runs against either Oracle Database or MongoDB. One variable selects the persistence implementation:

```bash
APP_STORE=oracle  # JPA + Oracle transactions and @Version
APP_STORE=mongo   # MongoDB documents, transactions, and @Version
```

The application uses Spring Boot 4.1.x, a modular-monolith package structure, a same-origin web UI, externalized configuration, health probes, idempotent checkout, conditional inventory decrements, and optimistic cart concurrency.

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
| Log viewer | `admin` | `admin` |

These are demo-only defaults. Override every password outside local development.

Stop the selected stack without deleting its database volume:

```bash
docker compose --profile mongo down
docker compose --profile oracle down
```

The two app services intentionally share port 8080; run one profile at a time. Switching `APP_STORE` activates the matching Spring profile and excludes the unused database auto-configuration, so the application never needs both databases online.

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

If MongoDB and Oracle app processes are running simultaneously on ports 8080 and 8081, respectively, use:

- MongoDB: `http://localhost:8080/api/v1/admin/logs`
- Oracle: `http://localhost:8081/api/v1/admin/logs`

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

The runner builds and starts a disposable Compose project on non-default ports, executes tests serially, resets carts/orders/inventory before and after every test, verifies all seven product stock/version values after each reset, and deletes only the disposable test volumes. It refuses to reset a project whose name does not start with `petstore-e2e-`, so your normal local database is not a valid reset target.

The API suite covers public catalog/session APIs, authentication and authorization, CSRF, both customer accounts, cart CRUD and validation, order history, checkout rollback, out-of-stock behavior, stale versions, two-user inventory races, simultaneous cart updates, and concurrent idempotency retries. Run API plus browser tests with:

```bash
npx playwright install chromium
npm run e2e:mongo
npm run e2e:oracle
```

See the [complete testing guide and executable coverage matrix](e2e/README.md) for every scenario and direct links to its test code.

Oracle normally needs about 2 GB by itself. On a 6 GB Docker VM, stop another local Oracle container before running the isolated Oracle suite; `docker compose stop oracle` preserves its data and `docker compose start oracle` restores it.

## Verify Java tests

```bash
./mvnw verify
```

Java integration tests use Testcontainers and are automatically skipped when Docker is unavailable. CI runs the unit/integration tests and both full E2E implementations independently.

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
| `DEMO_USERNAME` | `alice` | Local form-login user. |
| `DEMO_PASSWORD` | `petstore-demo` | Local form-login password; override outside a demo. |
| `DEMO_ADDITIONAL_USERNAME` | `aditya` | Additional local form-login user. |
| `DEMO_ADDITIONAL_PASSWORD` | `password` | Additional local form-login password; override outside a demo. |
| `ADMIN_USERNAME` | `admin` | Local log-viewer username. |
| `ADMIN_PASSWORD` | `admin` | Local log-viewer password; override outside a demo. |
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

## Concurrency and consistency contract

- Every cart response contains a `version`; the client must return it on a mutation. A stale version receives HTTP 409 instead of overwriting a concurrent change.
- Product inventory is decremented with `stock >= requestedQuantity` in the database predicate. Concurrent buyers cannot drive inventory below zero.
- Checkout, inventory decrement, order insert, and cart clear share one database transaction. MongoDB runs as a replica set because transactions are unavailable on standalone deployments.
- `(customerId, idempotencyKey)` is unique. Repeating a successful checkout key returns the original order and cannot create a second order.
- Order lines and shipping address are purchase-time snapshots, so later catalog/profile changes do not rewrite history.
- The UI calculates nothing authoritative: quantities are validated and totals are recomputed from server-held prices.

## Navigation for the panel

| Concern | Location |
|---|---|
| Shared persistence contract | `shared/application/StorefrontStore.java` |
| Business orchestration | `shared/application/StorefrontService.java` |
| Cart rules | `cart/domain/Cart.java` |
| Request validation | `cart/api/CartController.java`, `orders/api/OrderController.java` |
| Oracle transactions | `persistence/oracle/OracleStorefrontStore.java` |
| MongoDB transactions | `persistence/mongo/MongoStorefrontStore.java` |
| Database selection | `application.yml`, `application-oracle.yml`, `application-mongo.yml` |
| Security | `config/SecurityConfig.java` |
| UI | `src/main/resources/static/` |
| Tests | `src/test/` and `e2e/` |

## Production hardening still required

The local identity provider and automatic schema/index creation are demonstration conveniences. A production deployment should use enterprise OIDC, secrets management, Flyway/Liquibase for Oracle, explicit controlled MongoDB index migrations, TLS, backups/restore drills, audited authorization, rate limits, and capacity-tested connection pools.
