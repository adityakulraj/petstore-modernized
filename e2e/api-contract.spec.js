const crypto = require('node:crypto');
const { test, expect } = require('./support/fixtures');
const { SEED_STOCK } = require('./support/store-state');

const USERS = Object.freeze({
  alice: process.env.DEMO_USERNAME || 'alice',
  aditya: process.env.DEMO_ADDITIONAL_USERNAME || 'aditya',
  admin: process.env.ADMIN_USERNAME || 'admin'
});
const PASSWORDS = Object.freeze({
  alice: process.env.DEMO_PASSWORD || 'petstore-demo',
  aditya: process.env.DEMO_ADDITIONAL_PASSWORD || 'password',
  admin: process.env.ADMIN_PASSWORD || 'admin'
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
    store: process.env.E2E_STORE
  });

  const catalog = await request.get('/api/v1/catalog/products');
  expect(catalog.status()).toBe(200);
  const products = await catalog.json();
  expect(products.map(item => item.id)).toEqual([
    'AV-CB-01', 'FI-SW-01', 'FI-SW-02', 'FL-DSH-01', 'K9-BD-01', 'K9-RT-01', 'RP-IG-01'
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
  expect(await blanks.json()).toHaveLength(7);

  const item = await request.get('/api/v1/catalog/products/AV-CB-01');
  expect(item.status()).toBe(200);
  expect(await item.json()).toMatchObject({ id: 'AV-CB-01', name: 'Canary', price: 125, stock: 10 });
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

test('protected APIs reject anonymous requests and mutations without CSRF', async ({ request, playwright }) => {
  for (const path of ['/api/v1/cart', '/api/v1/orders', '/api/v1/csrf']) {
    const response = await request.get(path, { maxRedirects: 0 });
    expect(response.status(), path).toBe(401);
    expect(response.headers()['www-authenticate'], path).toMatch(/^Basic/);
  }

  const anonymousMutation = await request.post('/api/v1/cart/items', {
    data: { productId: 'AV-CB-01', quantity: 1, expectedVersion: 0 }
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
        store: process.env.E2E_STORE
      });
      const logout = await client.context.post('/logout', { headers: client.csrf, maxRedirects: 0 });
      expect(logout.status()).toBe(204);
      expect(await (await client.context.get('/api/v1/session')).json()).toMatchObject({ authenticated: false });
    } finally {
      await client.context.dispose();
    }
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
      status: 'PLACED',
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

test('insufficient inventory rolls back earlier decrements and preserves the cart', async ({ playwright }) => {
  const client = await login(playwright);
  try {
    let current = await cart(client);
    current = await (await addItem(client, 'FI-SW-01', 1, current.version)).json();
    current = await (await addItem(client, 'K9-BD-01', 5, current.version)).json();
    const response = await checkout(client, current.version);
    expect(response.status()).toBe(409);
    expect((await response.json()).detail).toContain('Insufficient stock');
    expect(await product(client.context, 'FI-SW-01')).toMatchObject({ stock: 25, version: 0 });
    expect(await product(client.context, 'K9-BD-01')).toMatchObject({ stock: 4, version: 0 });
    expect((await cart(client)).lines).toHaveLength(2);
    expect(await (await client.context.get('/api/v1/orders')).json()).toEqual([]);
  } finally {
    await client.context.dispose();
  }
});

test('two users racing for the last inventory produce one order and never negative stock', async ({ playwright }) => {
  const alice = await login(playwright, 'alice');
  const aditya = await login(playwright, 'aditya');
  try {
    const aliceCart = await (await addItem(alice, 'K9-BD-01', 4, (await cart(alice)).version)).json();
    const adityaCart = await (await addItem(aditya, 'K9-BD-01', 4, (await cart(aditya)).version)).json();
    const responses = await Promise.all([
      checkout(alice, aliceCart.version, crypto.randomUUID()),
      checkout(aditya, adityaCart.version, crypto.randomUUID())
    ]);
    expect(responses.map(response => response.status()).sort()).toEqual([201, 409]);
    expect(await product(alice.context, 'K9-BD-01')).toMatchObject({ stock: 0, version: 1 });
    const aliceOrders = await (await alice.context.get('/api/v1/orders')).json();
    const adityaOrders = await (await aditya.context.get('/api/v1/orders')).json();
    expect(aliceOrders.length + adityaOrders.length).toBe(1);
    const aliceLines = (await cart(alice)).lines;
    const adityaLines = (await cart(aditya)).lines;
    expect([aliceLines.length, adityaLines.length].sort()).toEqual([0, 1]);
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
