const { test: base, expect } = require('@playwright/test');
const { SEED_STOCK, resetStoreState } = require('./store-state');

async function expectSeedState(request) {
  const response = await request.get('/api/v1/catalog/products');
  expect(response.status()).toBe(200);
  const products = await response.json();
  expect(products).toHaveLength(Object.keys(SEED_STOCK).length);
  for (const product of products) {
    expect(product.stock, `${product.id} stock`).toBe(SEED_STOCK[product.id]);
    expect(product.version, `${product.id} version`).toBe(0);
  }
}

const test = base.extend({
  isolatedStoreState: [async ({ request }, use) => {
    resetStoreState();
    await expectSeedState(request);
    try {
      await use();
    } finally {
      resetStoreState();
      await expectSeedState(request);
    }
  }, { auto: true }]
});

module.exports = { test, expect };
