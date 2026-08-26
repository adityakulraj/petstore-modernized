const crypto = require('node:crypto');
const { test, expect } = require('./support/fixtures');
const { SEED_STOCK } = require('./support/store-state');

const USERS = Object.freeze({
  alice: process.env.DEMO_USERNAME || 'alice',
  aditya: process.env.DEMO_ADDITIONAL_USERNAME || 'aditya',
  admin: process.env.ADMIN_USERNAME || 'admin',
  supplier: process.env.SUPPLIER_USERNAME || 'supplier'
});
const PASSWORDS = Object.freeze({
  alice: process.env.DEMO_PASSWORD || 'petstore-demo',
  aditya: process.env.DEMO_ADDITIONAL_PASSWORD || 'password',
  admin: process.env.ADMIN_PASSWORD || 'admin',
  supplier: process.env.SUPPLIER_PASSWORD || 'supplier'
});
const ADDRESS = Object.freeze({
  fullName: 'API Test Customer',
  line1: '100 Modernization Way',
  line2: '',
  city: 'Pune',
  state: 'Maharashtra',
  postalCode: '411001',
  country: 'India'
});

async function login(playwright, user = 'alice', password = PASSWORDS[user]) {
  const context = await playwright.request.newContext({
    baseURL: process.env.BASE_URL,
    extraHTTPHeaders: { Accept: 'application/json' }
  });
  const loginPage = await context.get('/login');
  expect(loginPage.status()).toBe(200);
  const tokenMatch = (await loginPage.text()).match(/name="_csrf"[^>]*value="([^"]+)"/);
  expect(tokenMatch, 'login form CSRF token').not.toBeNull();
  const response = await context.post('/login', {
    form: { username: USERS[user] || user, password, _csrf: tokenMatch[1] },
    maxRedirects: 0
  });
  expect(response.status()).toBe(302);
  expect(response.headers().location).toMatch(/\/$/);

  const csrfResponse = await context.get('/api/v1/csrf');
  expect(csrfResponse.status()).toBe(200);
  const csrf = await csrfResponse.json();
  return { context, csrf: { [csrf.headerName]: csrf.token }, user: USERS[user] || user };
}

async function cart(client) {
  const response = await client.context.get('/api/v1/cart');
  expect(response.status()).toBe(200);
  return response.json();
}

async function addItem(client, productId, quantity, expectedVersion) {
  return client.context.post('/api/v1/cart/items', {
    headers: client.csrf,
    data: { productId, quantity, expectedVersion }
  });
}

async function checkout(client, expectedCartVersion, idempotencyKey = crypto.randomUUID(), address = ADDRESS) {
  return client.context.post('/api/v1/orders', {
    headers: { ...client.csrf, 'Idempotency-Key': idempotencyKey },
    data: { expectedCartVersion, address }
  });
}

async function product(context, id) {
  const response = await context.get(`/api/v1/catalog/products/${id}`);
  expect(response.status()).toBe(200);
  return response.json();
}

test('public session, catalog, categories, filters, and product lookup have parity', async ({ request }) => {
  const session = await request.get('/api/v1/session');
  expect(session.status()).toBe(200);
  expect(await session.json()).toEqual({
    authenticated: false,
    username: '',
    store: process.env.E2E_STORE,
    admin: false,
    supplier: false
  });

  const catalog = await request.get('/api/v1/catalog/products');
  expect(catalog.status()).toBe(200);
  const products = await catalog.json();
  expect(products.map(item => item.id)).toEqual([
    'AV-CB-01', 'FI-SW-01', 'FI-SW-02', 'FL-DSH-01', 'K9-BD-01', 'K9-BD-02', 'K9-RT-01', 'RP-IG-01'
  ]);
  expect(products.every(item => item.stock === SEED_STOCK[item.id] && item.version === 0)).toBe(true);

  const categories = await request.get('/api/v1/catalog/categories');
  expect(categories.status()).toBe(200);
  expect((await categories.json()).map(item => item.id).sort()).toEqual(['BIRDS', 'CATS', 'DOGS', 'FISH', 'REPTILES']);

  const fish = await request.get('/api/v1/catalog/products', { params: { category: 'fish' } });
  expect((await fish.json()).map(item => item.id)).toEqual(['FI-SW-01', 'FI-SW-02']);
  const search = await request.get('/api/v1/catalog/products', { params: { query: '  IGUANA  ' } });
  expect((await search.json()).map(item => item.id)).toEqual(['RP-IG-01']);
  const blanks = await request.get('/api/v1/catalog/products', { params: { category: ' ', query: ' ' } });
  expect(await blanks.json()).toHaveLength(8);

  const item = await request.get('/api/v1/catalog/products/AV-CB-01');
  expect(item.status()).toBe(200);
  expect(await item.json()).toMatchObject({ id: 'AV-CB-01', name: 'Canary', price: 125, stock: 10 });
  const bulldogs = products.filter(item => item.productGroupId === 'K9-BD');
  expect(bulldogs).toMatchObject([
    { id: 'K9-BD-01', variantName: 'Male Adult', name: 'Bulldog' },
    { id: 'K9-BD-02', variantName: 'Female Puppy', name: 'Bulldog' }
  ]);
  const missing = await request.get('/api/v1/catalog/products/does-not-exist');
  expect(missing.status()).toBe(404);
  expect(await missing.json()).toMatchObject({ status: 404, instance: '/api/v1/catalog/products/does-not-exist' });
});

test('correlation IDs are echoed only when safe', async ({ request }) => {
  const safe = await request.get('/api/v1/catalog/categories', { headers: { 'X-Correlation-ID': 'api-e2e_123.abc' } });
  expect(safe.headers()['x-correlation-id']).toBe('api-e2e_123.abc');
  expect(safe.headers()['x-request-id']).toBe('api-e2e_123.abc');

  const preferred = await request.get('/api/v1/catalog/categories', {
    headers: { 'X-Request-ID': 'request-456', 'X-Correlation-ID': 'legacy-ignored' }
  });
  expect(preferred.headers()['x-request-id']).toBe('request-456');
  expect(preferred.headers()['x-correlation-id']).toBe('request-456');

  const unsafe = 'x'.repeat(65);
  const replaced = await request.get('/api/v1/catalog/categories', { headers: { 'X-Correlation-ID': unsafe } });
  expect(replaced.headers()['x-correlation-id']).not.toBe(unsafe);
  expect(replaced.headers()['x-correlation-id']).toMatch(/^[0-9a-f-]{36}$/);
});

test('structured logs are searchable by request ID only by the admin role', async ({ request, playwright }) => {
  const requestId = `searchable-${crypto.randomUUID()}`;
  const source = await request.get('/api/v1/catalog/products/AV-CB-01', {
    headers: { 'X-Request-ID': requestId }
  });
  expect(source.status()).toBe(200);

  const anonymous = await request.get('/api/v1/admin/logs', {
    params: { requestId }, maxRedirects: 0
  });
  expect([302, 401]).toContain(anonymous.status());

  const basicAdmin = await playwright.request.newContext({
    baseURL: process.env.BASE_URL,
    extraHTTPHeaders: {
      Accept: 'application/json',
      Authorization: `Basic ${Buffer.from(`${USERS.admin}:${PASSWORDS.admin}`).toString('base64')}`
    }
  });

  const customer = await login(playwright, 'alice');
  const admin = await login(playwright, 'admin');
  try {
    expect((await customer.context.get('/api/v1/admin/logs', { params: { requestId } })).status()).toBe(403);
    const search = await admin.context.get('/api/v1/admin/logs', { params: { requestId, limit: 10 } });
    expect(search.status()).toBe(200);
    const result = await search.json();
    expect(result.requestId).toBe(requestId);
    expect(result.matched).toBeGreaterThanOrEqual(1);
    expect(result.entries).toContainEqual(expect.objectContaining({
      requestId,
      event: 'http.request.completed',
      httpMethod: 'GET',
      path: '/api/v1/catalog/products/AV-CB-01',
      status: 200
    }));
    const basicSearch = await basicAdmin.get('/api/v1/admin/logs', { params: { requestId } });
    expect(basicSearch.status()).toBe(200);
    expect((await basicSearch.json()).matched).toBeGreaterThanOrEqual(1);
    expect((await admin.context.get('/api/v1/admin/logs', { params: { requestId: 'bad value' } })).status()).toBe(400);
  } finally {
    await basicAdmin.dispose();
    await customer.context.dispose();
    await admin.context.dispose();
  }
});

test('health telemetry, pool metrics, and query plans are visible only to admin', async ({ request, playwright }) => {
  await request.get('/api/v1/catalog/products');
  await request.get('/api/v1/catalog/products/missing-health-probe');

  const anonymous = await request.get('/api/v1/admin/health', { maxRedirects: 0 });
  expect([302, 401]).toContain(anonymous.status());
  const customer = await login(playwright, 'alice');
  const admin = await login(playwright, 'admin');
  try {
    expect((await customer.context.get('/api/v1/admin/health')).status()).toBe(403);
    expect((await customer.context.get('/actuator/metrics')).status()).toBe(403);
    expect((await admin.context.get('/actuator/metrics')).status()).toBe(200);
    const response = await admin.context.get('/api/v1/admin/health');
    expect(response.status()).toBe(200);
    const health = await response.json();
    expect(health).toMatchObject({
      status: 'UP',
      store: process.env.E2E_STORE,
      traffic: {
        lifetimeRequests: expect.any(Number),
        windowRequests: expect.any(Number),
        windowClientErrors: expect.any(Number),
        serverErrorRatePercent: expect.any(Number),
        series: expect.any(Array)
      },
      jvm: { heapUsedBytes: expect.any(Number), liveThreads: expect.any(Number) },
      database: {
        pool: {
          provider: expect.any(String), configuredMin: 1, configuredMax: 10,
          total: expect.any(Number), active: expect.any(Number), idle: expect.any(Number)
        },
        operations: expect.any(Array),
        queryPlans: { capturedAt: expect.any(String), plans: expect.any(Array) }
      }
    });
    expect(health.uptimeSeconds).toBeGreaterThanOrEqual(0);
    expect(health.traffic.series).toHaveLength(60);
    expect(health.traffic.windowClientErrors).toBeGreaterThanOrEqual(1);
    expect(health.database.operations).toContainEqual(expect.objectContaining({ operation: 'catalog.products.all' }));
    expect(health.database.queryPlans.plans).toContainEqual(expect.objectContaining({ operation: 'orders.by_idempotency' }));
    expect(health.database.queryPlans.plans.every(plan => ['IXSCAN', 'COLLSCAN', 'OTHER'].includes(plan.scanType))).toBe(true);
    expect(health.database.queryPlans.plans).toContainEqual(expect.objectContaining({
      operation: 'catalog.products.by_category', scanType: 'IXSCAN'
    }));
    expect(health.database.queryPlans.plans).toContainEqual(expect.objectContaining({
      operation: 'orders.by_customer', scanType: 'IXSCAN'
    }));
  } finally {
    await customer.context.dispose();
    await admin.context.dispose();
  }
});

test('protected APIs reject anonymous requests and mutations without CSRF', async ({ request, playwright }) => {
  for (const path of ['/api/v1/cart', '/api/v1/orders']) {
    const response = await request.get(path, { maxRedirects: 0 });
    expect(response.status(), path).toBe(302);
    expect(response.headers().location, path).toMatch(/\/login$/);
  }
  expect((await request.get('/api/v1/csrf')).status()).toBe(200);

  const anonymousMutation = await request.post('/api/v1/cart/items', {
    data: { productId: 'AV-CB-01', quantity: 1, expectedVersion: 0 }, maxRedirects: 0
  });
  expect(anonymousMutation.status()).toBe(403);

  const client = await login(playwright);
  try {
    const missingCsrf = await client.context.post('/api/v1/cart/items', {
      data: { productId: 'AV-CB-01', quantity: 1, expectedVersion: 0 }
    });
    expect(missingCsrf.status()).toBe(403);
    const badCsrf = await client.context.post('/api/v1/cart/items', {
      headers: { 'X-XSRF-TOKEN': 'not-the-token' },
      data: { productId: 'AV-CB-01', quantity: 1, expectedVersion: 0 }
    });
    expect(badCsrf.status()).toBe(403);
    expect((await cart(client)).lines).toEqual([]);
  } finally {
    await client.context.dispose();
  }
});

test('invalid credentials fail and both configured users can authenticate and log out', async ({ playwright }) => {
  const failed = await playwright.request.newContext({ baseURL: process.env.BASE_URL });
  try {
    const page = await failed.get('/login');
    const match = (await page.text()).match(/name="_csrf"[^>]*value="([^"]+)"/);
    const response = await failed.post('/login', {
      form: { username: USERS.alice, password: 'incorrect', _csrf: match[1] },
      maxRedirects: 0
    });
    expect(response.status()).toBe(302);
    expect(response.headers().location).toMatch(/\/login\?error$/);
    expect(await (await failed.get('/api/v1/session')).json()).toMatchObject({ authenticated: false });
  } finally {
    await failed.dispose();
  }

  for (const user of ['alice', 'aditya']) {
    const client = await login(playwright, user);
    try {
      expect(await (await client.context.get('/api/v1/session')).json()).toEqual({
        authenticated: true,
        username: USERS[user],
        store: process.env.E2E_STORE,
        admin: false,
        supplier: false
      });
      const logout = await client.context.post('/logout', { headers: client.csrf, maxRedirects: 0 });
      expect(logout.status()).toBe(302);
      expect(logout.headers().location).toMatch(/\/$/);
      expect(await (await client.context.get('/api/v1/session')).json()).toMatchObject({ authenticated: false });
    } finally {
      await client.context.dispose();
    }
  }
});

test('cached Basic credentials cannot override a stale customer session during an admin role switch', async ({ playwright }) => {
  const customer = await login(playwright, 'alice');
  try {
    expect(await (await customer.context.get('/api/v1/session')).json()).toMatchObject({
      username: USERS.alice, admin: false
    });

    const authorization = `Basic ${Buffer.from(`${USERS.admin}:${PASSWORDS.admin}`).toString('base64')}`;
    const diagnostic = await customer.context.get('/api/v1/admin/health', {
      headers: { Authorization: authorization }
    });
    expect(diagnostic.status()).toBe(200);
    // Basic is permitted for this read-only request, but must not be saved over the customer form session.
    expect(await (await customer.context.get('/api/v1/session')).json()).toMatchObject({
      authenticated: true, username: USERS.alice, admin: false
    });

    const dashboard = await customer.context.get('/admin/catalog.html', {
      headers: { Authorization: authorization }, maxRedirects: 0
    });
    expect(dashboard.status()).toBe(403);

    // The cached Basic credential is ignored and the customer session remains authoritative.
    expect(await (await customer.context.get('/api/v1/session')).json()).toMatchObject({
      authenticated: true, username: USERS.alice, admin: false
    });

    const logout = await customer.context.post('/logout', { headers: customer.csrf, maxRedirects: 0 });
    expect(logout.status()).toBe(302);

    const loginPage = await customer.context.get('/login');
    const token = (await loginPage.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)[1];
    const adminLogin = await customer.context.post('/login', {
      form: { username: USERS.admin, password: PASSWORDS.admin, _csrf: token }, maxRedirects: 0
    });
    expect(adminLogin.status()).toBe(302);
    expect(adminLogin.headers().location).toMatch(/\/$/);
    expect(await (await customer.context.get('/api/v1/session')).json()).toMatchObject({
      username: USERS.admin, admin: true
    });
    expect((await customer.context.get('/api/v1/admin/catalog/items')).status()).toBe(200);
  } finally {
    await customer.context.dispose();
  }
});

test('the two users have isolated carts', async ({ playwright }) => {
  const alice = await login(playwright, 'alice');
  const aditya = await login(playwright, 'aditya');
  try {
    const aliceCart = await cart(alice);
    const added = await addItem(alice, 'AV-CB-01', 2, aliceCart.version);
    expect(added.status()).toBe(200);
    expect((await added.json()).lines).toMatchObject([{ productId: 'AV-CB-01', quantity: 2 }]);
    expect((await cart(aditya)).lines).toEqual([]);
  } finally {
    await alice.context.dispose();
    await aditya.context.dispose();
  }
});

test('MyList is customer-isolated and favorite retries are idempotent with personalized recommendations', async ({ request, playwright }) => {
  expect((await request.get('/api/v1/my-list', { maxRedirects: 0 })).status()).toBe(302);
  const alice = await login(playwright, 'alice');
  const aditya = await login(playwright, 'aditya');
  try {
    const initial = await alice.context.get('/api/v1/my-list');
    expect(initial.status()).toBe(200);
    expect(await initial.json()).toMatchObject({
      enabled: true,
      favorites: [],
      recommendations: [
        { id: 'K9-BD-01' }, { id: 'K9-BD-02' }, { id: 'K9-RT-01' }
      ]
    });
    const add = () => alice.context.post('/api/v1/my-list/items/K9-BD-01', { headers: alice.csrf });
    const retried = await Promise.all([add(), add()]);
    expect(retried.map(response => response.status())).toEqual([200, 200]);
    const saved = await (await alice.context.get('/api/v1/my-list')).json();
    expect(saved.favorites).toMatchObject([{ id: 'K9-BD-01', variantName: 'Male Adult' }]);
    expect(saved.recommendations[0]).toMatchObject({ id: 'K9-BD-02', variantName: 'Female Puppy' });
    expect(saved.recommendations.map(item => item.id)).not.toContain('K9-BD-01');
    expect((await (await aditya.context.get('/api/v1/my-list')).json()).favorites).toEqual([]);

    expect((await alice.context.post('/api/v1/my-list/items/not-a-product', { headers: alice.csrf })).status()).toBe(404);
    expect((await alice.context.post('/api/v1/my-list/items/K9-BD-02')).status()).toBe(403);
    const remove = () => alice.context.delete('/api/v1/my-list/items/K9-BD-01', { headers: alice.csrf });
    expect((await remove()).status()).toBe(200);
    expect((await remove()).status()).toBe(200);
    expect((await (await alice.context.get('/api/v1/my-list')).json()).favorites).toEqual([]);
  } finally {
    await alice.context.dispose();
    await aditya.context.dispose();
  }
});

test('cart add, update, delete, totals, and version increments work end to end', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    const initial = await cart(client);
    expect(initial).toMatchObject({ id: USERS.alice, customerId: USERS.alice, version: 0, lines: [], total: 0 });

    const addedResponse = await addItem(client, 'AV-CB-01', 2, initial.version);
    expect(addedResponse.status()).toBe(200);
    const added = await addedResponse.json();
    expect(added.version).toBe(1);
    expect(added.total).toBe(250);
    expect(added.lines[0]).toMatchObject({ productId: 'AV-CB-01', productName: 'Canary', unitPrice: 125, quantity: 2 });

    const updatedResponse = await client.context.put('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      data: { quantity: 3, expectedVersion: added.version }
    });
    expect(updatedResponse.status()).toBe(200);
    const updated = await updatedResponse.json();
    expect(updated).toMatchObject({ version: 2, total: 375, lines: [{ productId: 'AV-CB-01', quantity: 3 }] });

    const removedResponse = await client.context.delete('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      params: { expectedVersion: updated.version }
    });
    expect(removedResponse.status()).toBe(200);
    expect(await removedResponse.json()).toMatchObject({ version: 3, lines: [], total: 0 });
  } finally {
    await client.context.dispose();
  }
});

test('cart endpoints validate products, quantities, versions, and JSON shape', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    expect((await addItem(client, 'missing', 1, 0)).status()).toBe(404);
    for (const data of [
      { productId: '', quantity: 1, expectedVersion: 0 },
      { productId: 'AV-CB-01', quantity: 0, expectedVersion: 0 },
      { productId: 'AV-CB-01', quantity: 100, expectedVersion: 0 },
      { productId: 'AV-CB-01', quantity: 1, expectedVersion: -1 },
      { productId: 'AV-CB-01', quantity: 1, expectedVersion: 0, unexpected: true }
    ]) {
      const response = await client.context.post('/api/v1/cart/items', { headers: client.csrf, data });
      expect(response.status(), JSON.stringify(data)).toBe(400);
    }

    const malformed = await client.context.post('/api/v1/cart/items', {
      headers: { ...client.csrf, 'Content-Type': 'application/json' },
      data: '{"productId":'
    });
    expect(malformed.status()).toBe(400);
    expect((await cart(client)).lines).toEqual([]);
  } finally {
    await client.context.dispose();
  }
});

test('cart update and delete report missing lines, missing parameters, and stale versions', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    await cart(client);
    const missingUpdate = await client.context.put('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      data: { quantity: 2, expectedVersion: 0 }
    });
    expect(missingUpdate.status()).toBe(404);
    const missingDelete = await client.context.delete('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      params: { expectedVersion: 0 }
    });
    expect(missingDelete.status()).toBe(404);
    const noVersion = await client.context.delete('/api/v1/cart/items/AV-CB-01', { headers: client.csrf });
    expect(noVersion.status()).toBe(400);

    const added = await (await addItem(client, 'AV-CB-01', 1, 0)).json();
    const changed = await client.context.put('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      data: { quantity: 2, expectedVersion: added.version }
    });
    expect(changed.status()).toBe(200);
    const stale = await client.context.put('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      data: { quantity: 3, expectedVersion: added.version }
    });
    expect(stale.status()).toBe(409);
    expect((await stale.json()).detail).toContain('Expected cart version');
  } finally {
    await client.context.dispose();
  }
});

test('simultaneous updates using one cart version yield one winner and one conflict', async ({ playwright }) => {
  const first = await login(playwright, 'alice');
  const second = await login(playwright, 'alice');
  try {
    const initial = await cart(first);
    const added = await (await addItem(first, 'AV-CB-01', 1, initial.version)).json();
    const update = (client, quantity) => client.context.put('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      data: { quantity, expectedVersion: added.version }
    });
    const responses = await Promise.all([update(first, 2), update(second, 3)]);
    expect(responses.map(response => response.status()).sort()).toEqual([200, 409]);
    const finalCart = await cart(first);
    expect([2, 3]).toContain(finalCart.lines[0].quantity);
    expect(finalCart.version).toBe(2);
  } finally {
    await first.context.dispose();
    await second.context.dispose();
  }
});

test('checkout validates headers, address shape, and an empty cart', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    const body = { expectedCartVersion: 0, address: ADDRESS };
    expect((await client.context.post('/api/v1/orders', { headers: client.csrf, data: body })).status()).toBe(400);
    expect((await client.context.post('/api/v1/orders', {
      headers: { ...client.csrf, 'Idempotency-Key': '' }, data: body
    })).status()).toBe(400);
    expect((await client.context.post('/api/v1/orders', {
      headers: { ...client.csrf, 'Idempotency-Key': 'x'.repeat(101) }, data: body
    })).status()).toBe(400);
    const invalidAddress = await checkout(client, 0, crypto.randomUUID(), { ...ADDRESS, fullName: '' });
    expect(invalidAddress.status()).toBe(400);
    expect(await invalidAddress.json()).toMatchObject({ status: 400, fields: { 'address.fullName': expect.any(String) } });
    const unknownField = await client.context.post('/api/v1/orders', {
      headers: { ...client.csrf, 'Idempotency-Key': crypto.randomUUID() },
      data: { ...body, unexpected: true }
    });
    expect(unknownField.status()).toBe(400);
    const empty = await checkout(client, 0);
    expect(empty.status()).toBe(409);
    expect((await empty.json()).detail).toBe('Cart is empty');
  } finally {
    await client.context.dispose();
  }
});

test('checkout creates one immutable order, clears the cart, decrements stock, and is idempotent', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    const initial = await cart(client);
    const added = await (await addItem(client, 'AV-CB-01', 2, initial.version)).json();
    const key = crypto.randomUUID();
    const firstResponse = await checkout(client, added.version, key);
    expect(firstResponse.status()).toBe(201);
    const first = await firstResponse.json();
    expect(first).toMatchObject({
      customerId: USERS.alice,
      idempotencyKey: key,
      status: 'APPROVED',
      shippingAddress: ADDRESS,
      lines: [{ productId: 'AV-CB-01', productName: 'Canary', unitPrice: 125, quantity: 2, subtotal: 250 }],
      total: 250
    });

    const repeatedResponse = await checkout(client, added.version, key);
    expect(repeatedResponse.status()).toBe(201);
    expect((await repeatedResponse.json()).id).toBe(first.id);
    expect((await cart(client)).lines).toEqual([]);
    const orders = await client.context.get('/api/v1/orders');
    expect(await orders.json()).toMatchObject([{ id: first.id, total: 250 }]);
    expect(await product(client.context, 'AV-CB-01')).toMatchObject({ stock: 8, version: 1 });
  } finally {
    await client.context.dispose();
  }
});

test('checkout rejects a stale cart without changing orders or inventory', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    const added = await (await addItem(client, 'AV-CB-01', 1, (await cart(client)).version)).json();
    const changed = await client.context.put('/api/v1/cart/items/AV-CB-01', {
      headers: client.csrf,
      data: { quantity: 2, expectedVersion: added.version }
    });
    expect(changed.status()).toBe(200);
    const stale = await checkout(client, added.version);
    expect(stale.status()).toBe(409);
    expect(await product(client.context, 'AV-CB-01')).toMatchObject({ stock: 10, version: 0 });
    expect(await (await client.context.get('/api/v1/orders')).json()).toEqual([]);
  } finally {
    await client.context.dispose();
  }
});

test('insufficient inventory creates one atomic backorder without consuming available lines', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    let current = await cart(client);
    current = await (await addItem(client, 'FI-SW-01', 1, current.version)).json();
    current = await (await addItem(client, 'K9-BD-01', 5, current.version)).json();
    const response = await checkout(client, current.version);
    expect(response.status()).toBe(201);
    expect(await response.json()).toMatchObject({ status: 'BACKORDERED' });
    expect(await product(client.context, 'FI-SW-01')).toMatchObject({ stock: 25, version: 0 });
    expect(await product(client.context, 'K9-BD-01')).toMatchObject({ stock: 4, version: 0 });
    expect((await cart(client)).lines).toHaveLength(0);
    expect(await (await client.context.get('/api/v1/orders')).json()).toHaveLength(1);
  } finally {
    await client.context.dispose();
  }
});

test('two users racing for the last inventory produce one reservation and one backorder', async ({ playwright }) => {
  const alice = await login(playwright, 'alice');
  const aditya = await login(playwright, 'aditya');
  try {
    const aliceCart = await (await addItem(alice, 'K9-BD-01', 4, (await cart(alice)).version)).json();
    const adityaCart = await (await addItem(aditya, 'K9-BD-01', 4, (await cart(aditya)).version)).json();
    const responses = await Promise.all([
      checkout(alice, aliceCart.version, crypto.randomUUID()),
      checkout(aditya, adityaCart.version, crypto.randomUUID())
    ]);
    expect(responses.map(response => response.status())).toEqual([201, 201]);
    const placed = await Promise.all(responses.map(response => response.json()));
    expect(placed.map(order => order.status).sort()).toEqual(['BACKORDERED', 'PENDING']);
    expect(await product(alice.context, 'K9-BD-01')).toMatchObject({ stock: 0, version: 1 });
    const aliceOrders = await (await alice.context.get('/api/v1/orders')).json();
    const adityaOrders = await (await aditya.context.get('/api/v1/orders')).json();
    expect(aliceOrders.length + adityaOrders.length).toBe(2);
    const aliceLines = (await cart(alice)).lines;
    const adityaLines = (await cart(aditya)).lines;
    expect([aliceLines.length, adityaLines.length]).toEqual([0, 0]);
  } finally {
    await alice.context.dispose();
    await aditya.context.dispose();
  }
});

test('simultaneous retries with one idempotency key create and decrement exactly once', async ({ playwright }) => {
  const first = await login(playwright, 'alice');
  const second = await login(playwright, 'alice');
  try {
    const added = await (await addItem(first, 'AV-CB-01', 1, (await cart(first)).version)).json();
    const key = crypto.randomUUID();
    const responses = await Promise.all([
      checkout(first, added.version, key),
      checkout(second, added.version, key)
    ]);
    expect(responses.map(response => response.status())).toEqual([201, 201]);
    const orders = await Promise.all(responses.map(response => response.json()));
    expect(orders[0].id).toBe(orders[1].id);
    expect(await product(first.context, 'AV-CB-01')).toMatchObject({ stock: 9, version: 1 });
    expect(await (await first.context.get('/api/v1/orders')).json()).toHaveLength(1);
  } finally {
    await first.context.dispose();
    await second.context.dispose();
  }
});

test('idempotency keys and order history are scoped per user', async ({ playwright }) => {
  const alice = await login(playwright, 'alice');
  const aditya = await login(playwright, 'aditya');
  try {
    const key = 'same-key-for-two-customers';
    const aliceCart = await (await addItem(alice, 'AV-CB-01', 1, (await cart(alice)).version)).json();
    const adityaCart = await (await addItem(aditya, 'AV-CB-01', 1, (await cart(aditya)).version)).json();
    const aliceOrder = await (await checkout(alice, aliceCart.version, key)).json();
    const adityaOrder = await (await checkout(aditya, adityaCart.version, key)).json();
    expect(aliceOrder.id).not.toBe(adityaOrder.id);
    expect((await (await alice.context.get('/api/v1/orders')).json()).map(order => order.id)).toEqual([aliceOrder.id]);
    expect((await (await aditya.context.get('/api/v1/orders')).json()).map(order => order.id)).toEqual([adityaOrder.id]);
    expect(await product(alice.context, 'AV-CB-01')).toMatchObject({ stock: 8, version: 2 });
  } finally {
    await alice.context.dispose();
    await aditya.context.dispose();
  }
});

test('admin approval lifecycle gates supplier handoff and denial restores stock exactly once', async ({ playwright, request }) => {
  const anonymous = await request.get('/api/v1/admin/orders', { maxRedirects: 0 });
  expect(anonymous.status()).toBe(302);
  const customer = await login(playwright, 'alice');
  const adminA = await login(playwright, 'admin');
  const adminB = await login(playwright, 'admin');
  const supplier = await login(playwright, 'supplier');
  try {
    expect((await customer.context.get('/api/v1/admin/orders')).status()).toBe(403);

    const approvalCart = await (await addItem(customer, 'K9-BD-01', 1, (await cart(customer)).version)).json();
    const pending = await (await checkout(customer, approvalCart.version, 'admin-approval-e2e')).json();
    expect(pending).toMatchObject({ status: 'PENDING', total: 850, reviewedAt: null, reviewedBy: null });
    expect((await (await supplier.context.get('/api/v1/supplier/purchase-orders')).json())
      .some(po => po.orderId === pending.id)).toBe(false);

    const queued = await adminA.context.get('/api/v1/admin/orders');
    expect(queued.status()).toBe(200);
    expect(await queued.json()).toContainEqual(expect.objectContaining({ id: pending.id, status: 'PENDING' }));
    const approve = client => client.context.post(`/api/v1/admin/orders/${pending.id}/decision`, {
      headers: client.csrf, data: { expectedVersion: pending.version, decision: 'APPROVED' }
    });
    const approvals = await Promise.all([approve(adminA), approve(adminB)]);
    expect(approvals.map(response => response.status())).toEqual([200, 200]);
    const approved = await Promise.all(approvals.map(response => response.json()));
    expect(approved[0]).toMatchObject({ id: pending.id, status: 'APPROVED', version: pending.version + 1, reviewedBy: USERS.admin });
    expect(approved[1]).toMatchObject({ id: pending.id, status: 'APPROVED', version: pending.version + 1 });
    const purchaseOrders = await (await supplier.context.get('/api/v1/supplier/purchase-orders')).json();
    expect(purchaseOrders.filter(po => po.orderId === pending.id)).toHaveLength(1);
    const opposite = await adminA.context.post(`/api/v1/admin/orders/${pending.id}/decision`, {
      headers: adminA.csrf, data: { expectedVersion: pending.version, decision: 'DENIED' }
    });
    expect(opposite.status()).toBe(409);

    const beforeDenied = await product(customer.context, 'K9-RT-01');
    const denialCart = await (await addItem(customer, 'K9-RT-01', 1, (await cart(customer)).version)).json();
    const toDeny = await (await checkout(customer, denialCart.version, 'admin-denial-e2e')).json();
    expect(toDeny.status).toBe('PENDING');
    expect(await product(customer.context, 'K9-RT-01')).toMatchObject({ stock: beforeDenied.stock - 1, version: beforeDenied.version + 1 });
    const deny = () => adminA.context.post(`/api/v1/admin/orders/${toDeny.id}/decision`, {
      headers: adminA.csrf, data: { expectedVersion: toDeny.version, decision: 'DENIED' }
    });
    const denied = await deny();
    const deniedReplay = await deny();
    expect(denied.status()).toBe(200);
    expect(deniedReplay.status()).toBe(200);
    expect(await denied.json()).toMatchObject({ status: 'DENIED', version: toDeny.version + 1, reviewedBy: USERS.admin });
    expect(await deniedReplay.json()).toMatchObject({ status: 'DENIED', version: toDeny.version + 1 });
    expect(await product(customer.context, 'K9-RT-01')).toMatchObject({ stock: beforeDenied.stock, version: beforeDenied.version + 2 });
    expect((await (await supplier.context.get('/api/v1/supplier/purchase-orders')).json())
      .some(po => po.orderId === toDeny.id)).toBe(false);
  } finally {
    await customer.context.dispose();
    await adminA.context.dispose();
    await adminB.context.dispose();
    await supplier.context.dispose();
  }
});

test('supplier role safely updates inventory and idempotently processes purchase orders', async ({ playwright, request }) => {
  const anonymousInventory = await request.get('/api/v1/supplier/inventory', { maxRedirects: 0 });
  expect(anonymousInventory.status()).toBe(302);
  const customer = await login(playwright, 'alice');
  const supplier = await login(playwright, 'supplier');
  try {
    expect((await customer.context.get('/api/v1/supplier/inventory')).status()).toBe(403);
    expect(await (await supplier.context.get('/api/v1/session')).json()).toMatchObject({
      authenticated: true, username: USERS.supplier, supplier: true, admin: false
    });

    const inventoryResponse = await supplier.context.get('/api/v1/supplier/inventory');
    expect(inventoryResponse.status()).toBe(200);
    const initial = (await inventoryResponse.json()).find(item => item.id === 'AV-CB-01');
    const inventoryKey = crypto.randomUUID();
    const firstUpdate = await supplier.context.put('/api/v1/supplier/inventory/AV-CB-01', {
      headers: { ...supplier.csrf, 'Idempotency-Key': inventoryKey }, data: { expectedVersion: initial.version, quantity: 12 }
    });
    expect(firstUpdate.status()).toBe(200);
    const updated = await firstUpdate.json();
    expect(updated).toMatchObject({ stock: 12, version: 1 });
    const identicalReplay = await supplier.context.put('/api/v1/supplier/inventory/AV-CB-01', {
      headers: { ...supplier.csrf, 'Idempotency-Key': inventoryKey }, data: { expectedVersion: initial.version, quantity: 12 }
    });
    expect(identicalReplay.status()).toBe(200);
    expect(await identicalReplay.json()).toMatchObject({ stock: 12, version: 1 });
    const staleCompetingUpdate = await supplier.context.put('/api/v1/supplier/inventory/AV-CB-01', {
      headers: { ...supplier.csrf, 'Idempotency-Key': inventoryKey }, data: { expectedVersion: initial.version, quantity: 13 }
    });
    expect(staleCompetingUpdate.status()).toBe(409);

    const added = await (await addItem(customer, 'FI-SW-02', 1, (await cart(customer)).version)).json();
    const placed = await (await checkout(customer, added.version)).json();
    const purchaseOrdersResponse = await supplier.context.get('/api/v1/supplier/purchase-orders');
    expect(purchaseOrdersResponse.status()).toBe(200);
    const purchaseOrder = (await purchaseOrdersResponse.json()).find(item => item.orderId === placed.id);
    expect(purchaseOrder).toMatchObject({ id: placed.id, status: 'READY' });
    expect(purchaseOrder.version).toBeGreaterThanOrEqual(0);

    const process = () => supplier.context.post(`/api/v1/supplier/purchase-orders/${purchaseOrder.id}/process`, {
      headers: supplier.csrf, data: { expectedVersion: purchaseOrder.version }
    });
    const processedResponses = await Promise.all([process(), process()]);
    expect(processedResponses.map(response => response.status())).toEqual([200, 200]);
    const processed = await Promise.all(processedResponses.map(response => response.json()));
    expect(processed[0]).toMatchObject({ id: purchaseOrder.id, status: 'PROCESSED' });
    expect(processed[1]).toMatchObject({ id: purchaseOrder.id, status: 'PROCESSED' });
    expect(processed[0].version).toBeGreaterThan(0);
    expect(processed[1].version).toBe(processed[0].version);
    const history = await (await customer.context.get('/api/v1/orders')).json();
    expect(history.find(order => order.id === placed.id).status).toBe('COMPLETED');

    const missingCsrf = await supplier.context.put('/api/v1/supplier/inventory/AV-CB-01', {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      data: { expectedVersion: 1, quantity: 10 }
    });
    expect(missingCsrf.status()).toBe(403);
  } finally {
    await customer.context.dispose();
    await supplier.context.dispose();
  }
});

test('supplier replenishment releases a backorder once and preserves its customer timeline', async ({ playwright }) => {
  const customer = await login(playwright, 'alice');
  const supplierA = await login(playwright, 'supplier');
  const supplierB = await login(playwright, 'supplier');
  try {
    const initial = await product(customer.context, 'FI-SW-02');
    const quantity = initial.stock + 1;
    const changed = await (await addItem(customer, initial.id, quantity, (await cart(customer)).version)).json();
    const backordered = await (await checkout(customer, changed.version, 'backorder-e2e')).json();
    expect(backordered.status).toBe('BACKORDERED');
    expect(await product(customer.context, initial.id)).toMatchObject({ stock: initial.stock, version: initial.version });

    const waiting = await supplierA.context.get('/api/v1/supplier/backorders');
    expect(waiting.status()).toBe(200);
    expect(await waiting.json()).toContainEqual(expect.objectContaining({ id: backordered.id, status: 'BACKORDERED' }));

    const commandKey = crypto.randomUUID();
    const replenish = client => client.context.put(`/api/v1/supplier/inventory/${initial.id}`, {
      headers: { ...client.csrf, 'Idempotency-Key': commandKey },
      data: { expectedVersion: initial.version, quantity }
    });
    const responses = await Promise.all([replenish(supplierA), replenish(supplierB)]);
    expect(responses.map(response => response.status())).toEqual([200, 200]);
    const results = await Promise.all(responses.map(response => response.json()));
    expect(results[0]).toMatchObject({ stock: 0 });
    expect(results[1]).toEqual(results[0]);

    const history = await (await customer.context.get('/api/v1/orders')).json();
    expect(history.find(order => order.id === backordered.id).status).toBe('APPROVED');
    expect((await (await supplierA.context.get('/api/v1/supplier/backorders')).json())
      .some(order => order.id === backordered.id)).toBe(false);
    const purchaseOrders = await (await supplierA.context.get('/api/v1/supplier/purchase-orders')).json();
    expect(purchaseOrders.filter(po => po.orderId === backordered.id)).toHaveLength(1);
    const inbox = await (await customer.context.get('/api/v1/notifications')).json();
    expect(inbox.filter(item => item.orderId === backordered.id).map(item => item.type).sort())
      .toEqual(['ORDER_APPROVED', 'ORDER_BACKORDERED', 'ORDER_INVENTORY_ALLOCATED']);
  } finally {
    await customer.context.dispose();
    await supplierA.context.dispose();
    await supplierB.context.dispose();
  }
});

test('customer notification outbox tracks approval and fulfilment without duplicates', async ({ playwright, request }) => {
  const anonymous = await request.get('/api/v1/notifications', { maxRedirects: 0 });
  expect(anonymous.status()).toBe(302);
  const customer = await login(playwright, 'alice');
  const otherCustomer = await login(playwright, 'aditya');
  const admin = await login(playwright, 'admin');
  const supplier = await login(playwright, 'supplier');
  try {
    expect((await admin.context.get('/api/v1/notifications')).status()).toBe(403);
    const changed = await (await addItem(customer, 'K9-BD-01', 1, (await cart(customer)).version)).json();
    const pending = await (await checkout(customer, changed.version, 'notification-lifecycle-e2e')).json();
    expect(pending.status).toBe('PENDING');
    await checkout(customer, changed.version, 'notification-lifecycle-e2e');

    let inbox = await (await customer.context.get('/api/v1/notifications')).json();
    expect(inbox).toHaveLength(1);
    expect(inbox[0]).toMatchObject({
      id: `${pending.id}:ORDER_PENDING`, orderId: pending.id, customerId: USERS.alice,
      type: 'ORDER_PENDING', deliveryStatus: expect.stringMatching(/PENDING|DELIVERED/), readAt: null
    });
    expect(await (await otherCustomer.context.get('/api/v1/notifications')).json()).toEqual([]);

    const approvedResponse = await admin.context.post(`/api/v1/admin/orders/${pending.id}/decision`, {
      headers: admin.csrf, data: { expectedVersion: pending.version, decision: 'APPROVED' }
    });
    expect(approvedResponse.status()).toBe(200);
    await admin.context.post(`/api/v1/admin/orders/${pending.id}/decision`, {
      headers: admin.csrf, data: { expectedVersion: pending.version, decision: 'APPROVED' }
    });

    let purchaseOrders = await (await supplier.context.get('/api/v1/supplier/purchase-orders')).json();
    const purchaseOrder = purchaseOrders.find(item => item.orderId === pending.id);
    const processed = await supplier.context.post(`/api/v1/supplier/purchase-orders/${purchaseOrder.id}/process`, {
      headers: supplier.csrf, data: { expectedVersion: purchaseOrder.version }
    });
    expect(processed.status()).toBe(200);
    await supplier.context.post(`/api/v1/supplier/purchase-orders/${purchaseOrder.id}/process`, {
      headers: supplier.csrf, data: { expectedVersion: purchaseOrder.version }
    });

    inbox = await (await customer.context.get('/api/v1/notifications')).json();
    expect(inbox.map(item => item.type).sort()).toEqual(['ORDER_APPROVED', 'ORDER_COMPLETED', 'ORDER_PENDING']);
    expect(new Set(inbox.map(item => item.id)).size).toBe(3);
    const completed = inbox.find(item => item.type === 'ORDER_COMPLETED');
    const marked = await customer.context.post(`/api/v1/notifications/${encodeURIComponent(completed.id)}/read`, {
      headers: customer.csrf, data: { expectedVersion: completed.version }
    });
    expect(marked.status()).toBe(200);
    const read = await marked.json();
    expect(read.readAt).not.toBeNull();
    const replay = await customer.context.post(`/api/v1/notifications/${encodeURIComponent(completed.id)}/read`, {
      headers: customer.csrf, data: { expectedVersion: completed.version }
    });
    expect(replay.status()).toBe(200);
    expect((await replay.json()).version).toBe(read.version);
    expect((await otherCustomer.context.post(`/api/v1/notifications/${encodeURIComponent(completed.id)}/read`, {
      headers: otherCustomer.csrf, data: { expectedVersion: read.version }
    })).status()).toBe(404);
  } finally {
    await customer.context.dispose();
    await otherCustomer.context.dispose();
    await admin.context.dispose();
    await supplier.context.dispose();
  }
});

test('customer account registration, login, profile read/update, validation, and duplicates work end to end', async ({ playwright, request }) => {
  const username = 'api-account';
  const registration = {
    username, password: 'a-safe-test-password', fullName: 'API Account', email: 'api-account@example.test',
    phone: '+1-555-0199', address: { ...ADDRESS, fullName: 'API Account' }, preferredLanguage: 'en',
    favoriteCategory: 'CATS', myListPreference: true, bannerPreference: false
  };
  const invalid = await request.post('/api/v1/accounts', { data: { ...registration, username: 'x', password: 'short' } });
  expect(invalid.status()).toBe(400);

  const created = await request.post('/api/v1/accounts', { data: registration });
  expect(created.status()).toBe(200);
  const createdBody = await created.json();
  expect(createdBody).toMatchObject({ username, fullName: 'API Account', favoriteCategory: 'CATS' });
  expect(createdBody).not.toHaveProperty('password');
  expect(createdBody).not.toHaveProperty('passwordHash');
  const duplicate = await request.post('/api/v1/accounts', { data: registration });
  expect(duplicate.status()).toBe(409);

  const account = await login(playwright, username, registration.password);
  try {
    const me = await account.context.get('/api/v1/accounts/me');
    expect(me.status()).toBe(200);
    expect(await me.json()).toMatchObject(createdBody);
    const update = await account.context.put('/api/v1/accounts/me', {
      headers: account.csrf,
      data: {
        fullName: 'Updated API Account', email: 'updated@example.test', phone: '+1-555-0101',
        address: { ...ADDRESS, fullName: 'Updated API Account', city: 'Mumbai' }, preferredLanguage: 'fr',
        favoriteCategory: 'DOGS', myListPreference: false, bannerPreference: true
      }
    });
    expect(update.status()).toBe(200);
    expect(await update.json()).toMatchObject({
      username, fullName: 'Updated API Account', preferredLanguage: 'fr', favoriteCategory: 'DOGS',
      myListPreference: false, bannerPreference: true, address: { city: 'Mumbai' }
    });
    expect((await account.context.put('/api/v1/accounts/me', { data: registration })).status()).toBe(403);
  } finally { await account.context.dispose(); }
});

test('admin sales analytics separates recognized revenue, pipeline, category drilldown, and invalid ranges', async ({ playwright, request }) => {
  const anonymous = await request.get('/api/v1/admin/analytics/sales', { maxRedirects: 0 });
  expect([302, 401]).toContain(anonymous.status());
  const customer = await login(playwright, 'alice');
  const admin = await login(playwright, 'admin');
  try {
    expect((await customer.context.get('/api/v1/admin/analytics/sales')).status()).toBe(403);

    let changed = await (await addItem(customer, 'AV-CB-01', 2, (await cart(customer)).version)).json();
    const accepted = await (await checkout(customer, changed.version, 'analytics-accepted')).json();
    expect(accepted.status).toBe('APPROVED');
    changed = await (await addItem(customer, 'K9-BD-01', 1, (await cart(customer)).version)).json();
    const pending = await (await checkout(customer, changed.version, 'analytics-pending')).json();
    expect(pending.status).toBe('PENDING');

    const reportResponse = await admin.context.get('/api/v1/admin/analytics/sales');
    expect(reportResponse.status()).toBe(200);
    const report = await reportResponse.json();
    expect(report.dimension).toBe('CATEGORY');
    expect(report.summary).toEqual({
      acceptedOrders: 1, unitsSold: 2, revenue: 250, averageOrderValue: 250, pendingValue: 850
    });
    expect(report.breakdown).toEqual([
      { key: 'BIRDS', label: 'Birds', orderCount: 1, unitsSold: 2, revenue: 250 }
    ]);
    expect(report.statuses).toEqual(expect.arrayContaining([
      expect.objectContaining({ status: 'APPROVED', orderCount: 1, value: 250 }),
      expect.objectContaining({ status: 'PENDING', orderCount: 1, value: 850 })
    ]));

    const dogsResponse = await admin.context.get('/api/v1/admin/analytics/sales', { params: { category: 'dogs' } });
    expect(dogsResponse.status()).toBe(200);
    const dogs = await dogsResponse.json();
    expect(dogs).toMatchObject({ categoryId: 'DOGS', dimension: 'ITEM', summary: { revenue: 0, pendingValue: 850 } });
    expect(dogs.breakdown).toEqual([]);
    expect(dogs.statuses).toContainEqual(expect.objectContaining({ status: 'PENDING', orderCount: 1, value: 850 }));

    expect((await admin.context.get('/api/v1/admin/analytics/sales', {
      params: { from: '2026-08-27', to: '2026-08-26' }
    })).status()).toBe(400);
    expect((await admin.context.get('/api/v1/admin/analytics/sales', {
      params: { from: '2024-01-01', to: '2026-08-26' }
    })).status()).toBe(400);
    expect((await admin.context.get('/api/v1/admin/analytics/sales', { params: { category: 'HORSES' } })).status()).toBe(404);
    expect((await admin.context.get('/admin/sales')).status()).toBe(200);
  } finally {
    await customer.context.dispose();
    await admin.context.dispose();
  }
});

test('admin catalog lifecycle is idempotent, concurrency-safe, audited, and preserves quoted cart prices', async ({ playwright, request }) => {
  const anonymous = await request.get('/api/v1/admin/catalog/items', { maxRedirects: 0 });
  expect([302, 401]).toContain(anonymous.status());
  const admin = await login(playwright, 'admin');
  const customer = await login(playwright, 'alice');
  const supplier = await login(playwright, 'supplier');
  const item = {
    id: 'CT-API-01', productGroupId: 'CT-API', variantName: 'Female Adult', categoryId: 'CATS',
    categoryName: 'Cats', name: 'Siamese', description: 'A catalog-managed companion', price: 425, active: true
  };
  try {
    expect((await customer.context.get('/api/v1/admin/catalog/items')).status()).toBe(403);
    expect((await supplier.context.get('/api/v1/admin/catalog/items')).status()).toBe(403);

    const create = () => admin.context.post('/api/v1/admin/catalog/items', { headers: admin.csrf, data: item });
    const createdResponse = await create();
    expect(createdResponse.status()).toBe(201);
    const created = await createdResponse.json();
    expect(created).toMatchObject({ ...item, stock: 0, version: 0 });
    const createReplay = await create();
    expect(createReplay.status()).toBe(201);
    expect((await createReplay.json()).version).toBe(created.version);

    const stockResponse = await supplier.context.put(`/api/v1/supplier/inventory/${item.id}`, {
      headers: { ...supplier.csrf, 'Idempotency-Key': 'catalog-api-stock' },
      data: { expectedVersion: created.version, quantity: 2 }
    });
    expect(stockResponse.status()).toBe(200);
    const stocked = await stockResponse.json();

    const customerCart = await cart(customer);
    const quotedCart = await (await addItem(customer, item.id, 1, customerCart.version)).json();
    expect(quotedCart.lines[0]).toMatchObject({ productId: item.id, unitPrice: 425 });

    const update = {
      expectedVersion: stocked.version, productGroupId: item.productGroupId, variantName: item.variantName,
      categoryId: item.categoryId, categoryName: item.categoryName, name: item.name,
      description: item.description, price: 450, active: true
    };
    const updatedResponse = await admin.context.put(`/api/v1/admin/catalog/items/${item.id}`, {
      headers: admin.csrf, data: update
    });
    expect(updatedResponse.status()).toBe(200);
    const updated = await updatedResponse.json();
    expect(updated).toMatchObject({ price: 450, stock: 2, active: true });

    const updateReplay = await admin.context.put(`/api/v1/admin/catalog/items/${item.id}`, {
      headers: admin.csrf, data: update
    });
    expect(updateReplay.status()).toBe(200);
    expect((await updateReplay.json()).version).toBe(updated.version);
    const staleCompeting = await admin.context.put(`/api/v1/admin/catalog/items/${item.id}`, {
      headers: admin.csrf, data: { ...update, price: 451, description: 'Competing change' }
    });
    expect(staleCompeting.status()).toBe(409);

    const archivedResponse = await admin.context.put(`/api/v1/admin/catalog/items/${item.id}`, {
      headers: admin.csrf, data: { ...update, expectedVersion: updated.version, active: false }
    });
    expect(archivedResponse.status()).toBe(200);
    const archived = await archivedResponse.json();
    expect(archived).toMatchObject({ active: false, price: 450, stock: 2 });
    expect((await request.get(`/api/v1/catalog/products/${item.id}`)).status()).toBe(404);
    expect((await (await admin.context.get('/api/v1/admin/catalog/items')).json()))
      .toContainEqual(expect.objectContaining({ id: item.id, active: false }));

    const order = await (await checkout(customer, quotedCart.version, 'catalog-price-snapshot')).json();
    expect(order.lines[0]).toMatchObject({ productId: item.id, unitPrice: 425 });
    const changes = await (await admin.context.get('/api/v1/admin/catalog/changes')).json();
    expect(changes.filter(change => change.productId === item.id)).toMatchObject([
      { action: 'UPDATED', previousPrice: 450, newPrice: 450, previousActive: true, newActive: false },
      { action: 'UPDATED', previousPrice: 425, newPrice: 450, previousActive: true, newActive: true },
      { action: 'CREATED', previousPrice: null, newPrice: 425, previousActive: null, newActive: true }
    ]);
    expect((await admin.context.get('/admin/catalog')).status()).toBe(200);
  } finally {
    await admin.context.dispose();
    await customer.context.dispose();
    await supplier.context.dispose();
  }
});
