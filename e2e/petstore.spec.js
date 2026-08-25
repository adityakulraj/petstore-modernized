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
