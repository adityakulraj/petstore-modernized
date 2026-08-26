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
    await expect(page.locator('#inventory-body tr')).toHaveCount(7);
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
