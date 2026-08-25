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
