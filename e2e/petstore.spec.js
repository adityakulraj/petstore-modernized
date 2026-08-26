const { test, expect } = require('./support/fixtures');

test('customer can browse, sign in, add to cart, checkout, and see order history', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Find a companion/ })).toBeVisible();
  await expect(page.locator('.product-card')).toHaveCount(7);
  await expect(page.locator('.product-card').first()).toContainText('10 AVAILABLE');

  await page.getByRole('link', { name: 'Sign in' }).click();
  await page.locator('input[name="username"]').fill(process.env.DEMO_USERNAME || 'alice');
  await page.locator('input[name="password"]').fill(process.env.DEMO_PASSWORD || 'petstore-demo');
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page.getByText(/Hi, alice/)).toBeVisible();

  await page.locator('.product-card').first().getByRole('button', { name: 'Add to cart' }).click();
  await expect(page.getByRole('status')).toContainText('Added to your cart');
  await page.getByRole('button', { name: /Cart/ }).click();
  await expect(page.getByRole('button', { name: 'Checkout' })).toBeVisible();

  await page.getByRole('button', { name: 'Checkout' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('button', { name: 'Place order once' }).click();
  await expect(page.getByText(/Order .* placed/)).toBeVisible();
  await expect(page.locator('.order-card')).toHaveCount(1);
  await expect(page.locator('.product-card').first()).toContainText('9 AVAILABLE');
});

test('catalog search filters without server-authoritative state changes', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('searchbox', { name: 'Search catalog' }).fill('iguana');
  await expect(page.locator('.product-card')).toHaveCount(1);
  await expect(page.getByRole('heading', { name: 'Green Iguana' })).toBeVisible();
});

test('customer selects a legacy item variant and gets MyList recommendations', async ({ page }) => {
  await page.goto('/login');
  await page.locator('input[name="username"]').fill('alice');
  await page.locator('input[name="password"]').fill('petstore-demo');
  await page.getByRole('button', { name: /sign in/i }).click();

  const bulldog = page.locator('.product-card').filter({ hasText: 'Bulldog' });
  await expect(bulldog).toHaveCount(1);
  await bulldog.getByLabel('Choose item variant').selectOption('K9-BD-02');
  await expect(bulldog).toContainText('Female Puppy');
  await bulldog.getByRole('button', { name: 'Add to MyList' }).click();
  await expect(page.getByRole('status')).toContainText('Saved to MyList');

  await page.getByRole('button', { name: /^MyList/ }).click();
  await expect(page.getByRole('heading', { name: 'Your favourites' })).toBeVisible();
  await expect(page.locator('#my-list-panel')).toContainText('Female Puppy Bulldog');
  await expect(page.locator('#my-list-panel')).toContainText('Male Adult Bulldog');
  const favorite = page.locator('.recommendation-card').filter({ hasText: 'Female Puppy Bulldog' });
  await favorite.getByRole('button', { name: 'Add to cart' }).click();
  await page.getByRole('button', { name: /Cart/ }).click();
  await expect(page.locator('#cart-panel')).toContainText('Female Puppy Bulldog');
});

test('additional demo customer can sign in', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'Sign in' }).click();
  await page.locator('input[name="username"]').fill('aditya');
  await page.locator('input[name="password"]').fill('password');
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page.getByText(/Hi, aditya/)).toBeVisible();
});

test('admin can open the live health dashboard and inspect database diagnostics', async ({ page }) => {
  await page.goto('/admin/health.html');
  await expect(page).toHaveURL(/\/login$/);
  await page.locator('input[name="username"]').fill(process.env.ADMIN_USERNAME || 'admin');
  await page.locator('input[name="password"]').fill(process.env.ADMIN_PASSWORD || 'admin');
  await page.getByRole('button', { name: /sign in/i }).click();

  await expect(page).toHaveURL(/\/admin\/health\.html(?:\?continue)?$/);
  await expect(page.getByRole('heading', { name: 'Application health' })).toBeVisible();
  await expect(page.locator('#health-status')).toHaveText('UP');
  await expect(page.locator('.metric-grid article')).toHaveCount(5);
  await expect(page.locator('svg.chart')).toHaveCount(3);
  await expect(page.getByRole('heading', { name: 'Database performance' })).toBeVisible();
  await expect(page.locator('#pool-provider')).not.toHaveText('—');
  await expect(page.locator('#operation-rows tr')).not.toHaveCount(0);
  await expect(page.locator('#plan-rows')).toContainText('orders.by_idempotency');
  await expect(page.getByRole('link', { name: 'Logs' })).toHaveAttribute('href', /\/api\/v1\/admin\/logs/);
});

test('admin can approve a high-value order from the approval queue', async ({ page, playwright }) => {
  const customer = await playwright.request.newContext({ baseURL: process.env.BASE_URL });
  try {
    const loginPage = await customer.get('/login');
    const token = (await loginPage.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)[1];
    await customer.post('/login', { form: { username: 'alice', password: 'petstore-demo', _csrf: token } });
    const csrf = await (await customer.get('/api/v1/csrf')).json();
    const cart = await (await customer.get('/api/v1/cart')).json();
    const changed = await (await customer.post('/api/v1/cart/items', {
      headers: { [csrf.headerName]: csrf.token }, data: { productId: 'K9-BD-01', quantity: 1, expectedVersion: cart.version }
    })).json();
    const pending = await (await customer.post('/api/v1/orders', {
      headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': 'admin-browser-order' },
      data: { expectedCartVersion: changed.version, address: ADDRESS_FOR_BROWSER }
    })).json();
    expect(pending.status).toBe('PENDING');

    await page.goto('/admin/orders.html');
    await expect(page).toHaveURL(/\/login$/);
    await page.locator('input[name="username"]').fill(process.env.ADMIN_USERNAME || 'admin');
    await page.locator('input[name="password"]').fill(process.env.ADMIN_PASSWORD || 'admin');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL(/\/admin\/orders\.html(?:\?continue)?$/);
    await expect(page.getByRole('heading', { name: 'Approval queue' })).toBeVisible();
    await expect(page.locator('#pending-count')).toHaveText('1');
    await expect(page.locator(`[data-order="${pending.id}"]`)).toContainText('$850.00');
    await page.locator(`[data-order="${pending.id}"]`).getByRole('button', { name: 'Approve for supplier' }).click();
    await expect(page.getByRole('status')).toContainText('released to the supplier');
    await expect(page.locator('#pending-count')).toHaveText('0');
    await expect(page.locator('#history-body')).toContainText('APPROVED');
  } finally { await customer.dispose(); }
});

test('supplier can open the portal, update inventory, and process a storefront purchase order', async ({ page, playwright }) => {
  const customer = await playwright.request.newContext({ baseURL: process.env.BASE_URL });
  try {
    const loginPage = await customer.get('/login');
    const token = (await loginPage.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)[1];
    await customer.post('/login', { form: { username: 'alice', password: 'petstore-demo', _csrf: token } });
    const csrf = await (await customer.get('/api/v1/csrf')).json();
    const cart = await (await customer.get('/api/v1/cart')).json();
    const changed = await (await customer.post('/api/v1/cart/items', {
      headers: { [csrf.headerName]: csrf.token }, data: { productId: 'FI-SW-01', quantity: 1, expectedVersion: cart.version }
    })).json();
    await customer.post('/api/v1/orders', {
      headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': 'supplier-browser-order' },
      data: { expectedCartVersion: changed.version, address: ADDRESS_FOR_BROWSER }
    });

    await page.goto('/supplier/');
    await expect(page).toHaveURL(/\/login$/);
    await page.locator('input[name="username"]').fill(process.env.SUPPLIER_USERNAME || 'supplier');
    await page.locator('input[name="password"]').fill(process.env.SUPPLIER_PASSWORD || 'supplier');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL(/\/supplier\/(?:\?continue)?$/);
    await expect(page.getByRole('heading', { name: 'Supplier portal' })).toBeVisible();
    await expect(page.locator('#inventory-body tr')).toHaveCount(8);
    const firstQuantity = page.locator('[data-quantity="AV-CB-01"]');
    await firstQuantity.fill('11');
    await page.locator('[data-save="AV-CB-01"]').click();
    await expect(page.getByRole('status')).toContainText('inventory updated');

    await page.getByRole('button', { name: 'Purchase orders' }).click();
    await expect(page.locator('.purchase-order')).toHaveCount(1);
    await page.getByRole('button', { name: 'Process purchase order' }).click();
    await expect(page.locator('.status.processed')).toHaveText('PROCESSED');
  } finally { await customer.dispose(); }
});

test('supplier replenishment visibly releases a customer backorder exactly once', async ({ page, browser }) => {
  await page.goto('/login');
  await page.locator('input[name="username"]').fill('alice');
  await page.locator('input[name="password"]').fill('petstore-demo');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.getByRole('searchbox', { name: 'Search catalog' }).fill('Tiger Shark');
  await page.locator('.product-card').getByRole('button', { name: 'Add to cart' }).click();
  await page.getByRole('button', { name: /Cart/ }).click();
  await page.locator('[data-qty="FI-SW-02"]').fill('13');
  await page.locator('[data-qty="FI-SW-02"]').blur();
  await expect(page.locator('[data-qty="FI-SW-02"]')).toHaveValue('13');
  await page.getByRole('button', { name: 'Checkout' }).click();
  await page.getByRole('button', { name: 'Place order once' }).click();
  await expect(page.getByRole('status')).toContainText('backordered');
  await expect(page.locator('.order-card')).toContainText('BACKORDERED');

  const supplierContext = await browser.newContext({ baseURL: process.env.BASE_URL });
  const supplierPage = await supplierContext.newPage();
  try {
    await supplierPage.goto('/supplier/');
    await supplierPage.locator('input[name="username"]').fill('supplier');
    await supplierPage.locator('input[name="password"]').fill('supplier');
    await supplierPage.getByRole('button', { name: /sign in/i }).click();
    await supplierPage.getByRole('button', { name: /Backorders/ }).click();
    await expect(supplierPage.locator('#backorder-count')).toHaveText('1');
    await expect(supplierPage.locator('#backorder-list')).toContainText('Tiger Shark × 13');

    await supplierPage.getByRole('button', { name: 'Inventory' }).click();
    await supplierPage.locator('[data-quantity="FI-SW-02"]').fill('13');
    const updateResponsePromise = supplierPage.waitForResponse(response =>
      response.url().includes('/api/v1/supplier/inventory/FI-SW-02') && response.request().method() === 'PUT');
    await supplierPage.locator('[data-save="FI-SW-02"]').click();
    const updateResponse = await updateResponsePromise;
    expect(updateResponse.status()).toBe(200);
    expect(await updateResponse.json()).toMatchObject({ stock: 0 });
    await expect(supplierPage.getByRole('status')).toContainText('waiting orders checked');
    await supplierPage.getByRole('button', { name: /Backorders/ }).click();
    await supplierPage.getByRole('button', { name: 'Refresh' }).click();
    await expect(supplierPage.locator('#backorder-count')).toHaveText('0');

    await supplierPage.getByRole('button', { name: 'Purchase orders' }).click();
    await expect(supplierPage.locator('.purchase-order')).toHaveCount(1);
    await supplierPage.getByRole('button', { name: 'Process purchase order' }).click();
    await expect(supplierPage.locator('.status.processed')).toHaveText('PROCESSED');
  } finally {
    await supplierContext.close();
  }

  await page.getByRole('button', { name: /Inbox/ }).click();
  await expect(page.locator('.notification-card')).toHaveCount(4);
  await expect(page.locator('#notifications-panel')).toContainText('Order backordered');
  await expect(page.locator('#notifications-panel')).toContainText('Inventory allocated');
  await expect(page.locator('#notifications-panel')).toContainText('Order completed');
});

test('customer sees a durable order timeline and can clear the inbox badge', async ({ page, playwright }) => {
  const admin = await playwright.request.newContext({ baseURL: process.env.BASE_URL });
  const supplier = await playwright.request.newContext({ baseURL: process.env.BASE_URL });
  try {
    await page.goto('/login');
    await page.locator('input[name="username"]').fill('alice');
    await page.locator('input[name="password"]').fill('petstore-demo');
    await page.getByRole('button', { name: /sign in/i }).click();
    const dog = page.locator('.product-card').filter({ hasText: 'Bulldog' });
    await dog.getByRole('button', { name: 'Add to cart' }).click();
    await page.getByRole('button', { name: /Cart/ }).click();
    await page.getByRole('button', { name: 'Checkout' }).click();
    await page.getByRole('button', { name: 'Place order once' }).click();
    await expect(page.locator('.order-card')).toContainText('PENDING');
    const orders = await (await page.request.get('/api/v1/orders')).json();
    const pending = orders[0];

    for (const [client, username, password] of [[admin, 'admin', 'admin'], [supplier, 'supplier', 'supplier']]) {
      const loginPage = await client.get('/login');
      const token = (await loginPage.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)[1];
      await client.post('/login', { form: { username, password, _csrf: token } });
    }
    const adminCsrf = await (await admin.get('/api/v1/csrf')).json();
    await admin.post(`/api/v1/admin/orders/${pending.id}/decision`, {
      headers: { [adminCsrf.headerName]: adminCsrf.token },
      data: { expectedVersion: pending.version, decision: 'APPROVED' }
    });
    const supplierCsrf = await (await supplier.get('/api/v1/csrf')).json();
    const purchaseOrders = await (await supplier.get('/api/v1/supplier/purchase-orders')).json();
    const purchaseOrder = purchaseOrders.find(item => item.orderId === pending.id);
    await supplier.post(`/api/v1/supplier/purchase-orders/${purchaseOrder.id}/process`, {
      headers: { [supplierCsrf.headerName]: supplierCsrf.token }, data: { expectedVersion: purchaseOrder.version }
    });

    await page.getByRole('button', { name: /Inbox/ }).click();
    await expect(page.locator('.notification-card')).toHaveCount(3);
    await expect(page.locator('#notifications-panel')).toContainText('Order awaiting review');
    await expect(page.locator('#notifications-panel')).toContainText('Order approved');
    await expect(page.locator('#notifications-panel')).toContainText('Order completed');
    const unreadBefore = Number(await page.locator('#notification-count').textContent());
    expect(unreadBefore).toBe(3);
    await page.locator('.notification-card').first().getByRole('button', { name: 'Mark read' }).click();
    await expect(page.locator('#notification-count')).toHaveText('2');
    await page.getByRole('button', { name: 'Orders' }).click();
    await expect(page.locator('.order-timeline li')).toHaveCount(3);
  } finally {
    await admin.dispose();
    await supplier.dispose();
  }
});

const ADDRESS_FOR_BROWSER = {
  fullName: 'Supplier Browser Test', line1: '100 Modernization Way', line2: '', city: 'Pune',
  state: 'Maharashtra', postalCode: '411001', country: 'India'
};

test('customer can register, sign in, view the persisted profile, and update it', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Create account' }).click();
  const form = page.locator('#account-form');
  await form.locator('[name="username"]').fill('browser-account');
  await form.locator('[name="password"]').fill('browser-safe-password');
  await form.locator('[name="fullName"]').fill('Browser Account');
  await form.locator('[name="email"]').fill('browser-account@example.test');
  await form.locator('[name="phone"]').fill('+1-555-0110');
  await form.locator('[name="line1"]').fill('10 Browser Lane');
  await form.locator('[name="city"]').fill('Pune');
  await form.locator('[name="state"]').fill('Maharashtra');
  await form.locator('[name="postalCode"]').fill('411001');
  await form.locator('[name="country"]').fill('India');
  await page.getByRole('button', { name: 'Create account' }).last().click();
  await expect(page).toHaveURL(/\/login$/);

  await page.locator('input[name="username"]').fill('browser-account');
  await page.locator('input[name="password"]').fill('browser-safe-password');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.getByRole('button', { name: 'Account' }).first().click();
  await expect(form.locator('[name="fullName"]')).toHaveValue('Browser Account');
  await form.locator('[name="fullName"]').fill('Updated Browser Account');
  await page.getByRole('button', { name: 'Save profile' }).click();
  await expect(page.getByRole('status')).toContainText('Account updated');
  await page.reload();
  await page.getByRole('button', { name: 'Account' }).first().click();
  await expect(form.locator('[name="fullName"]')).toHaveValue('Updated Browser Account');
});

test('admin can filter sales analytics and drill from category revenue into item variants', async ({ page, playwright }) => {
  const customer = await playwright.request.newContext({ baseURL: process.env.BASE_URL });
  try {
    const loginPage = await customer.get('/login');
    const token = (await loginPage.text()).match(/name="_csrf"[^>]*value="([^"]+)"/)[1];
    await customer.post('/login', { form: { username: 'alice', password: 'petstore-demo', _csrf: token } });
    const csrf = await (await customer.get('/api/v1/csrf')).json();

    let cart = await (await customer.get('/api/v1/cart')).json();
    let changed = await (await customer.post('/api/v1/cart/items', {
      headers: { [csrf.headerName]: csrf.token }, data: { productId: 'AV-CB-01', quantity: 1, expectedVersion: cart.version }
    })).json();
    await customer.post('/api/v1/orders', {
      headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': 'sales-browser-accepted' },
      data: { expectedCartVersion: changed.version, address: ADDRESS_FOR_BROWSER }
    });
    cart = await (await customer.get('/api/v1/cart')).json();
    changed = await (await customer.post('/api/v1/cart/items', {
      headers: { [csrf.headerName]: csrf.token }, data: { productId: 'K9-BD-02', quantity: 1, expectedVersion: cart.version }
    })).json();
    await customer.post('/api/v1/orders', {
      headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': 'sales-browser-pending' },
      data: { expectedCartVersion: changed.version, address: ADDRESS_FOR_BROWSER }
    });

    await page.goto('/admin/sales.html');
    await expect(page).toHaveURL(/\/login$/);
    await page.locator('input[name="username"]').fill('admin');
    await page.locator('input[name="password"]').fill('admin');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL(/\/admin\/sales\.html(?:\?continue)?$/);
    await expect(page.getByRole('heading', { name: 'Sales & revenue' })).toBeVisible();
    await expect(page.locator('#revenue-total')).toHaveText('$125.00');
    await expect(page.locator('#pending-value')).toHaveText('$850.00');
    await expect(page.locator('#breakdown-body')).toContainText('Birds');
    await expect(page.locator('#status-list')).toContainText('PENDING');
    await page.getByRole('button', { name: 'Birds' }).click();
    await expect(page.getByRole('heading', { name: 'Items in BIRDS' })).toBeVisible();
    await expect(page.locator('#breakdown-body')).toContainText('Canary');
    await expect(page.locator('#back-to-categories')).toBeVisible();
  } finally { await customer.dispose(); }
});

test('admin can create, reprice, and archive an item from the catalog dashboard', async ({ page }) => {
  await page.goto('/admin/catalog.html');
  await expect(page).toHaveURL(/\/login$/);
  await page.locator('input[name="username"]').fill('admin');
  await page.locator('input[name="password"]').fill('admin');
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/admin\/catalog\.html(?:\?continue)?$/);
  await expect(page.getByRole('heading', { name: 'Catalog & pricing' })).toBeVisible();
  await expect(page.locator('.catalog-card')).toHaveCount(8);

  await page.getByRole('button', { name: 'Add catalog item' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('[name="id"]').fill('CT-UI-01');
  await dialog.locator('[name="productGroupId"]').fill('CT-UI');
  await dialog.locator('[name="variantName"]').fill('Male Adult');
  await dialog.locator('[name="price"]').fill('375.00');
  await dialog.locator('[name="categoryId"]').fill('CATS');
  await dialog.locator('[name="categoryName"]').fill('Cats');
  await dialog.locator('[name="name"]').fill('Siamese');
  await dialog.locator('[name="description"]').fill('A UI-managed catalog item');
  await dialog.getByRole('button', { name: 'Create item' }).click();
  await expect(page.getByRole('status')).toContainText('supplier inventory set to 0');
  const card = page.locator('[data-item="CT-UI-01"]');
  await expect(card).toContainText('$375.00');
  await expect(card).toContainText('0 supplier-managed in stock');

  await card.getByRole('button', { name: 'Edit item' }).click();
  await dialog.locator('[name="price"]').fill('399.00');
  await dialog.locator('[name="active"]').uncheck();
  await dialog.getByRole('button', { name: 'Save catalog change' }).click();
  await expect(page.getByRole('status')).toContainText('Catalog item updated');
  await expect(card).toContainText('$399.00');
  await expect(card).toContainText('ARCHIVED');
  await expect(page.locator('#change-history')).toContainText('CT-UI-01');
  expect((await page.request.get('/api/v1/catalog/products/CT-UI-01')).status()).toBe(404);
});
