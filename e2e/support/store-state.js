const { spawnSync } = require('node:child_process');

const SEED_STOCK = Object.freeze({
  'FI-SW-01': 25,
  'FI-SW-02': 12,
  'K9-BD-01': 4,
  'K9-RT-01': 5,
  'FL-DSH-01': 8,
  'AV-CB-01': 10,
  'RP-IG-01': 6
});

function requireIsolatedConfiguration() {
  const isolated = process.env.E2E_ISOLATED === 'true';
  const store = process.env.E2E_STORE;
  const project = process.env.E2E_COMPOSE_PROJECT;
  if (!isolated || !['mongo', 'oracle'].includes(store) || !project?.startsWith('petstore-e2e-')) {
    // Resetting stock is intentionally impossible unless the disposable project naming contract is present.
    throw new Error(
      'Refusing to reset a non-isolated database. Run npm run e2e:api:mongo or npm run e2e:api:oracle.'
    );
  }
  return { store, project };
}

function composeExec(project, service, command, input) {
  const result = spawnSync(
    'docker',
    ['compose', '--project-name', project, 'exec', '-T', service, ...command],
    { cwd: process.cwd(), input, encoding: 'utf8' }
  );
  if (result.status !== 0) {
    throw new Error(`State reset failed for ${service}:\n${result.stdout}\n${result.stderr}`);
  }
}

function resetMongo(project) {
  const updates = Object.entries(SEED_STOCK)
    .map(([id, stock]) => `db.products.updateOne({_id:${JSON.stringify(id)}},{$set:{stock:${stock},version:NumberLong(0)}});`)
    .join('');
  const script = `
    db.carts.deleteMany({});
    db.supplierPurchaseOrders.deleteMany({});
    db.orders.deleteMany({});
    db.customerAccounts.deleteMany({_id: {$nin: ['alice', 'aditya']}});
    ${updates}
    if (db.products.countDocuments({}) !== ${Object.keys(SEED_STOCK).length}) {
      throw new Error('Unexpected seeded product count');
    }
  `;
  composeExec(project, 'mongo', ['mongosh', '--quiet', 'petstore', '--eval', script]);
}

function resetOracle(project) {
  const cases = Object.entries(SEED_STOCK)
    .map(([id, stock]) => `WHEN '${id}' THEN ${stock}`)
    .join(' ');
  const sql = `
    WHENEVER SQLERROR EXIT SQL.SQLCODE
    DELETE FROM PS_SUPPLIER_PO_LINE;
    DELETE FROM PS_SUPPLIER_PO;
    DELETE FROM PS_ORDER_LINE;
    DELETE FROM PS_ORDER;
    DELETE FROM PS_CART_LINE;
    DELETE FROM PS_CART;
    DELETE FROM PS_CUSTOMER_ACCOUNT WHERE USERNAME NOT IN ('alice', 'aditya');
    UPDATE PS_PRODUCT
      SET STOCK = CASE ID ${cases} END,
          VERSION = 0
      WHERE ID IN (${Object.keys(SEED_STOCK).map(id => `'${id}'`).join(',')});
    COMMIT;
    DECLARE
      product_count NUMBER;
    BEGIN
      SELECT COUNT(*) INTO product_count FROM PS_PRODUCT;
      IF product_count != ${Object.keys(SEED_STOCK).length} THEN
        RAISE_APPLICATION_ERROR(-20001, 'Unexpected seeded product count');
      END IF;
    END;
    /
    EXIT;
  `;
  const username = process.env.E2E_ORACLE_USERNAME || 'petstore';
  const password = process.env.E2E_ORACLE_PASSWORD || 'petstore_local_only';
  composeExec(project, 'oracle', ['sqlplus', '-s', `${username}/${password}@//localhost:1521/FREEPDB1`], sql);
}

function resetStoreState() {
  // Direct database cleanup keeps the test-only operation out of the production HTTP surface.
  const { store, project } = requireIsolatedConfiguration();
  if (store === 'mongo') resetMongo(project);
  else resetOracle(project);
}

module.exports = { SEED_STOCK, resetStoreState };
