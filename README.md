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

Open [http://localhost:8080](http://localhost:8080). The local demo login is `alice` / `petstore-demo`. Override it with `DEMO_USERNAME` and `DEMO_PASSWORD`.

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
APP_STORE=mongo MONGODB_URI='mongodb://localhost:27017/petstore?replicaSet=rs0' ./mvnw spring-boot:run
```

or:

```bash
docker compose --profile oracle up -d oracle
APP_STORE=oracle ORACLE_URL='jdbc:oracle:thin:@localhost:1521/FREEPDB1' \
  ORACLE_USERNAME=petstore ORACLE_PASSWORD=petstore_local_only ./mvnw spring-boot:run
```

## Verify

```bash
./mvnw verify
npm ci
npx playwright install chromium
npm run e2e
```

Java integration tests use Testcontainers and are automatically skipped when Docker is unavailable. End-to-end tests expect a running application and accept `BASE_URL` and demo credential overrides.

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
| `MONGODB_URI` | `mongodb://localhost:27017/petstore?replicaSet=rs0` | MongoDB target; checkout requires a replica set. |
| `DEMO_USERNAME` | `alice` | Local form-login user. |
| `DEMO_PASSWORD` | `petstore-demo` | Local form-login password; override outside a demo. |
| `JPA_DDL_AUTO` | `update` | Local Oracle schema mode; use controlled migrations in production. |

## Troubleshooting

- **Port 8080 is already in use:** stop the other profile before switching stores. `docker compose --profile mongo down` and `docker compose --profile oracle down` retain data volumes.
- **MongoDB says transactions are unsupported:** use the Compose service or connect to a replica set. A standalone `mongod` cannot execute checkout transactions.
- **Oracle is still starting:** the first boot may take several minutes. Check `docker compose --profile oracle ps` and `docker compose --profile oracle logs oracle`.
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
