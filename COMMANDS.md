# PetStore command list

Run these commands from the repository root. The application requires Docker
Desktop for database-backed runs, integration tests, E2E tests, and deployment.

## Select JDK 21 (macOS with Homebrew)

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
java -version
./mvnw -version
```

Persist the selection for future zsh sessions:

```bash
printf '%s\n' 'export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"' 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

If Homebrew has not installed JDK 21 yet:

```bash
brew install openjdk@21
```

## Build

```bash
./mvnw clean package
```

Build without running tests:

```bash
./mvnw clean package -DskipTests
```

Generate the CycloneDX software bill of materials:

```bash
./mvnw -B -ntp org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
ls -l target/bom.*
```

## Run Java tests

Fast unit test run:

```bash
./mvnw test
```

Full verification, including database integration contracts when Docker is
available:

```bash
./mvnw verify
```

Fallback when Testcontainers cannot download Ryuk:

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./mvnw verify
```

## Run E2E tests

Install Node dependencies once:

```bash
npm ci
```

Install the Chromium browser used by Playwright once:

```bash
npx playwright install chromium
```

API-only contracts:

```bash
npm run e2e:api:mongo
npm run e2e:api:oracle
npm run e2e:api:all
```

Complete API and browser contracts:

```bash
npm run e2e:mongo
npm run e2e:oracle
```

Interactive Playwright UI mode:

```bash
npm run e2e:ui
```

## Run locally (application outside Docker)

Start MongoDB, then the application:

```bash
docker compose --profile mongo up -d mongo
APP_STORE=mongo MONGODB_URI='mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true' ./mvnw spring-boot:run
```

Start Oracle, then the application:

```bash
docker compose --profile oracle up -d oracle
APP_STORE=oracle ORACLE_URL='jdbc:oracle:thin:@localhost:1521/FREEPDB1' ORACLE_USERNAME=petstore ORACLE_PASSWORD=petstore_local_only ./mvnw spring-boot:run
```

## Deploy with Docker Compose

Deploy the MongoDB variant at http://localhost:8080:

```bash
APP_PORT=8080 docker compose -p petstore-observability --profile mongo up --build -d mongo app-mongo
```

Deploy the Oracle variant at http://localhost:8081:

```bash
APP_PORT=8081 docker compose -p petstore-observability --profile oracle up --build -d oracle app-oracle
```

Wait for either deployment and view status/logs:

```bash
docker compose -p petstore-observability --profile mongo ps
docker compose -p petstore-observability --profile mongo logs -f app-mongo
docker compose -p petstore-observability --profile oracle ps
docker compose -p petstore-observability --profile oracle logs -f app-oracle
```

Verify health:

```bash
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8081/actuator/health/readiness
curl --fail --user admin:admin http://localhost:8080/api/v1/admin/health
curl --fail --user admin:admin http://localhost:8081/api/v1/admin/health
```

Stop deployments without deleting local data:

```bash
docker compose -p petstore-observability --profile mongo stop app-mongo mongo
docker compose -p petstore-observability --profile oracle stop app-oracle oracle
```

Restart stopped deployments:

```bash
docker compose -p petstore-observability --profile mongo start mongo app-mongo
docker compose -p petstore-observability --profile oracle start oracle app-oracle
```

Remove a deployment and its named volumes (destructive; deletes that Compose
project's persisted MongoDB data and application logs):

```bash
docker compose -p petstore-observability --profile mongo down --volumes --remove-orphans
docker compose -p petstore-observability --profile oracle down --volumes --remove-orphans
```

## Inspect the running databases

The local comparison environment runs two independent databases:

- MongoDB: `localhost:27017`, database `petstore`; local authentication is disabled.
- Oracle: `localhost:1521/FREEPDB1`, schema `PETSTORE`, username `petstore`, password `petstore_local_only`.

Changes made through the MongoDB application on port `8080` do not appear in
Oracle. Changes made through the Oracle application on port `8081` do not
appear in MongoDB.

### Quick commands when the application is already up

These commands connect to the containers that are already running. They do not
rebuild or restart the application.

Start from the repository directory and confirm the existing processes:

```bash
cd /Users/adkunwar/mongo

docker compose -p petstore-observability \
  --profile mongo --profile oracle ps
```

Open the MongoDB database interactively:

```bash
docker compose -p petstore-observability --profile mongo exec mongo \
  mongosh 'mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true'
```

Then run, for example:

```javascript
show collections
db.products.find({}, {_id: 1, name: 1, stock: 1, version: 1}).sort({_id: 1})
db.orders.find().sort({createdAt: -1}).pretty()
exit
```

Print MongoDB inventory directly without entering `mongosh`:

```bash
docker compose -p petstore-observability --profile mongo exec -T mongo \
  mongosh --quiet \
  'mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true' \
  --eval 'db.products.find({}, {_id:1,name:1,stock:1,version:1}).sort({_id:1}).forEach(printjson)'
```

Open the Oracle database interactively:

```bash
docker compose -p petstore-observability --profile oracle exec oracle \
  sqlplus 'petstore/petstore_local_only@//localhost:1521/FREEPDB1'
```

Then run, for example:

```sql
SET LINESIZE 240
SET PAGESIZE 100

SELECT ID, NAME, STOCK, VERSION
FROM PS_PRODUCT
ORDER BY ID;

SELECT ID, CUSTOMER_ID, STATUS, TOTAL, CREATED_AT
FROM PS_ORDER
ORDER BY CREATED_AT DESC;

EXIT;
```

Print Oracle inventory directly without entering SQL*Plus:

```bash
docker compose -p petstore-observability --profile oracle exec -T oracle sh -lc \
  "printf \"SET LINESIZE 240 PAGESIZE 100\\nSELECT ID, NAME, STOCK, VERSION FROM PS_PRODUCT ORDER BY ID;\\nEXIT;\\n\" | sqlplus -s petstore/petstore_local_only@//localhost:1521/FREEPDB1"
```

Check the already-running applications and protected dashboards:

```bash
curl --fail http://localhost:8080/actuator/health/readiness
curl --fail http://localhost:8081/actuator/health/readiness

curl --fail --user admin:admin \
  http://localhost:8080/api/v1/admin/health

curl --fail --user admin:admin \
  http://localhost:8081/api/v1/admin/health
```

Open the applications in a browser:

```text
MongoDB-backed application: http://localhost:8080
Oracle-backed application:  http://localhost:8081
MongoDB health dashboard:   http://localhost:8080/admin/health.html
Oracle health dashboard:    http://localhost:8081/admin/health.html
```

Confirm that both databases and applications are running:

```bash
docker compose -p petstore-observability --profile mongo --profile oracle ps
docker ps --format 'table {{.Names}}\t{{.Ports}}\t{{.Status}}'
```

### MongoDB shell

Open `mongosh` inside the running MongoDB container:

```bash
docker compose -p petstore-observability --profile mongo exec mongo \
  mongosh 'mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true'
```

If `mongosh` is installed directly on the host:

```bash
mongosh 'mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true'
```

Basic navigation inside `mongosh`:

```javascript
show dbs
use petstore
db
show collections
db.getCollectionNames().sort()
```

Count documents in every collection:

```javascript
db.getCollectionNames().sort().forEach(collection => {
  print(collection + ": " + db.getCollection(collection).countDocuments())
})
```

View products and inventory:

```javascript
db.products.find().pretty()

db.products.find(
  {},
  {
    _id: 1,
    name: 1,
    variantName: 1,
    categoryId: 1,
    price: 1,
    stock: 1,
    version: 1
  }
).sort({_id: 1})

db.products.findOne({_id: "FI-SW-01"})

db.products.find(
  {stock: {$lte: 10}},
  {_id: 1, name: 1, stock: 1, version: 1}
).sort({stock: 1})
```

View orders:

```javascript
db.orders.find().sort({createdAt: -1}).pretty()
db.orders.find({customerId: "alice"}).sort({createdAt: -1}).pretty()
db.orders.find({customerId: "aditya"}).sort({createdAt: -1}).pretty()

db.orders.find(
  {},
  {_id: 1, customerId: 1, status: 1, total: 1, createdAt: 1, version: 1}
).sort({createdAt: -1})

db.orders.findOne({_id: "REPLACE_WITH_ORDER_ID"})

db.orders.aggregate([
  {$group: {_id: "$status", count: {$sum: 1}, total: {$sum: "$total"}}},
  {$sort: {_id: 1}}
])
```

View carts and payments:

```javascript
db.carts.find().pretty()
db.carts.findOne({customerId: "alice"})
db.carts.findOne({customerId: "aditya"})

db.payments.find().sort({createdAt: -1}).pretty()
db.payments.find({customerId: "alice"}).sort({createdAt: -1}).pretty()
db.payments.find({status: "CAPTURED"}).pretty()
```

View customer accounts without displaying password hashes:

```javascript
db.customerAccounts.find({}, {passwordHash: 0, _class: 0}).pretty()
db.customerAccounts.findOne({_id: "alice"}, {passwordHash: 0, _class: 0})
```

Do not expose the `passwordHash` field during a demonstration.

View favourites, supplier data, notifications, idempotency commands, and
catalog history:

```javascript
db.favoriteItems.find().sort({addedAt: -1}).pretty()
db.favoriteItems.find({customerId: "alice"}).pretty()

db.supplierPurchaseOrders.find().sort({createdAt: -1}).pretty()
db.supplierPurchaseOrders.find({status: "READY"}).pretty()

db.customerNotifications.find().sort({createdAt: -1}).pretty()
db.customerNotifications.find({customerId: "alice"}).sort({createdAt: -1}).pretty()
db.customerNotifications.find({deliveryStatus: {$ne: "DELIVERED"}}).pretty()

db.customerOrderCommands.find().pretty()
db.supplierInventoryCommands.find().pretty()
db.catalogChanges.find().sort({occurredAt: -1}).pretty()
```

View indexes:

```javascript
db.products.getIndexes()
db.orders.getIndexes()

db.getCollectionNames().sort().forEach(collection => {
  print("\n=== " + collection + " ===")
  printjson(db.getCollection(collection).getIndexes())
})
```

Explain a MongoDB query and inspect whether it used `IXSCAN` or `COLLSCAN`:

```javascript
db.products.find({categoryId: "DOGS"}).explain("executionStats")

const result = db.products.find({categoryId: "DOGS"}).explain("executionStats")
printjson({
  winningPlan: result.queryPlanner.winningPlan,
  returned: result.executionStats.nReturned,
  keysExamined: result.executionStats.totalKeysExamined,
  documentsExamined: result.executionStats.totalDocsExamined,
  executionTimeMs: result.executionStats.executionTimeMillis
})
```

View replica-set status and leave the shell:

```javascript
rs.status()
exit
```

Run a MongoDB query directly from the terminal without opening an interactive
shell:

```bash
docker compose -p petstore-observability --profile mongo exec -T mongo \
  mongosh --quiet \
  'mongodb://localhost:27017/petstore?replicaSet=rs0&directConnection=true' \
  --eval 'db.products.find().sort({_id:1}).forEach(printjson)'
```

### Oracle SQL*Plus

Open SQL*Plus inside the running Oracle container:

```bash
docker compose -p petstore-observability --profile oracle exec oracle \
  sqlplus 'petstore/petstore_local_only@//localhost:1521/FREEPDB1'
```

Improve interactive SQL*Plus output:

```sql
SET LINESIZE 240
SET PAGESIZE 100
SET LONG 10000
SET TRIMSPOOL ON
ALTER SESSION SET NLS_TIMESTAMP_TZ_FORMAT = 'YYYY-MM-DD HH24:MI:SS TZH:TZM';
```

Verify the connected user, database, and pluggable database:

```sql
SELECT USER FROM DUAL;

SELECT
    SYS_CONTEXT('USERENV', 'DB_NAME') AS DATABASE_NAME,
    SYS_CONTEXT('USERENV', 'CON_NAME') AS CONTAINER_NAME
FROM DUAL;
```

List and describe tables:

```sql
SELECT TABLE_NAME
FROM USER_TABLES
ORDER BY TABLE_NAME;

DESC PS_PRODUCT;
DESC PS_ORDER;
DESC PS_ORDER_LINE;
DESC PS_CART;
DESC PS_CART_LINE;
DESC PS_PAYMENT;
DESC PS_CUSTOMER_ACCOUNT;

SELECT TABLE_NAME, COLUMN_ID, COLUMN_NAME, DATA_TYPE
FROM USER_TAB_COLUMNS
ORDER BY TABLE_NAME, COLUMN_ID;
```

The application tables are:

```text
PS_CART
PS_CART_LINE
PS_CATALOG_CHANGE
PS_CUSTOMER_ACCOUNT
PS_CUSTOMER_NOTIFICATION
PS_CUSTOMER_ORDER_COMMAND
PS_FAVORITE_ITEM
PS_ORDER
PS_ORDER_LINE
PS_PAYMENT
PS_PRODUCT
PS_SUPPLIER_INV_COMMAND
PS_SUPPLIER_PO
PS_SUPPLIER_PO_LINE
```

View products and inventory:

```sql
SELECT
    ID,
    NAME,
    VARIANT_NAME,
    CATEGORY_ID,
    PRICE,
    STOCK,
    ACTIVE,
    VERSION
FROM PS_PRODUCT
ORDER BY ID;

SELECT *
FROM PS_PRODUCT
WHERE ID = 'FI-SW-01';

SELECT ID, NAME, STOCK, VERSION
FROM PS_PRODUCT
WHERE STOCK <= 10
ORDER BY STOCK, ID;
```

View orders and their line items:

```sql
SELECT
    ID,
    CUSTOMER_ID,
    STATUS,
    TOTAL,
    CREATED_AT,
    VERSION
FROM PS_ORDER
ORDER BY CREATED_AT DESC;

SELECT *
FROM PS_ORDER
WHERE CUSTOMER_ID = 'alice'
ORDER BY CREATED_AT DESC;

SELECT
    O.ID AS ORDER_ID,
    O.CUSTOMER_ID,
    O.STATUS,
    O.TOTAL,
    L.LINE_NUMBER,
    L.PRODUCT_ID,
    L.PRODUCT_NAME,
    L.QUANTITY,
    L.UNIT_PRICE,
    L.SUBTOTAL
FROM PS_ORDER O
JOIN PS_ORDER_LINE L ON L.ORDER_ID = O.ID
ORDER BY O.CREATED_AT DESC, L.LINE_NUMBER;

SELECT STATUS, COUNT(*) AS ORDER_COUNT, SUM(TOTAL) AS TOTAL_VALUE
FROM PS_ORDER
GROUP BY STATUS
ORDER BY STATUS;
```

View carts and their lines:

```sql
SELECT * FROM PS_CART ORDER BY CUSTOMER_ID;
SELECT * FROM PS_CART_LINE ORDER BY CUSTOMER_ID, LINE_NUMBER;

SELECT
    C.CUSTOMER_ID,
    C.VERSION,
    L.PRODUCT_ID,
    L.PRODUCT_NAME,
    L.QUANTITY,
    L.UNIT_PRICE
FROM PS_CART C
LEFT JOIN PS_CART_LINE L ON L.CUSTOMER_ID = C.CUSTOMER_ID
WHERE C.CUSTOMER_ID = 'alice'
ORDER BY L.LINE_NUMBER;
```

View payments and customer accounts. The account query deliberately excludes
`PASSWORD_HASH`:

```sql
SELECT
    ID,
    ORDER_ID,
    CUSTOMER_ID,
    AMOUNT,
    CURRENCY,
    STATUS,
    METHOD_LABEL,
    CREATED_AT,
    VERSION
FROM PS_PAYMENT
ORDER BY CREATED_AT DESC;

SELECT
    USERNAME,
    FULL_NAME,
    EMAIL,
    PHONE,
    FAVORITE_CATEGORY,
    PREFERRED_LANGUAGE
FROM PS_CUSTOMER_ACCOUNT
ORDER BY USERNAME;
```

View favourites, supplier data, notifications, commands, and catalog history:

```sql
SELECT * FROM PS_FAVORITE_ITEM ORDER BY ADDED_AT DESC;
SELECT * FROM PS_SUPPLIER_PO ORDER BY CREATED_AT DESC;
SELECT * FROM PS_SUPPLIER_PO_LINE ORDER BY SUPPLIER_PO_ID, LINE_NUMBER;

SELECT
    P.ID,
    P.ORDER_ID,
    P.STATUS,
    P.CREATED_AT,
    L.PRODUCT_ID,
    L.PRODUCT_NAME,
    L.QUANTITY
FROM PS_SUPPLIER_PO P
JOIN PS_SUPPLIER_PO_LINE L ON L.SUPPLIER_PO_ID = P.ID
ORDER BY P.CREATED_AT DESC, L.LINE_NUMBER;

SELECT * FROM PS_CUSTOMER_NOTIFICATION ORDER BY CREATED_AT DESC;
SELECT * FROM PS_CUSTOMER_ORDER_COMMAND ORDER BY CREATED_AT DESC;
SELECT * FROM PS_SUPPLIER_INV_COMMAND ORDER BY COMPLETED_AT DESC;
SELECT * FROM PS_CATALOG_CHANGE ORDER BY OCCURRED_AT DESC;
```

View indexes and indexed columns:

```sql
SELECT TABLE_NAME, INDEX_NAME, UNIQUENESS
FROM USER_INDEXES
ORDER BY TABLE_NAME, INDEX_NAME;

SELECT TABLE_NAME, INDEX_NAME, COLUMN_POSITION, COLUMN_NAME
FROM USER_IND_COLUMNS
ORDER BY TABLE_NAME, INDEX_NAME, COLUMN_POSITION;
```

Explain an Oracle query:

```sql
EXPLAIN PLAN FOR
SELECT *
FROM PS_PRODUCT
WHERE CATEGORY_ID = 'DOGS';

SELECT *
FROM TABLE(DBMS_XPLAN.DISPLAY);
```

Leave SQL*Plus:

```sql
EXIT;
```

Run an Oracle query directly from the terminal without opening an interactive
shell:

```bash
docker compose -p petstore-observability --profile oracle exec -T oracle sh -lc \
  "printf \"SET LINESIZE 240 PAGESIZE 100\\nSELECT ID, NAME, STOCK, VERSION FROM PS_PRODUCT ORDER BY ID;\\nEXIT;\\n\" | sqlplus -s petstore/petstore_local_only@//localhost:1521/FREEPDB1"
```
