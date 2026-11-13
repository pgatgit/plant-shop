import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// M0 placeholder: proves the compose stack serves the SPA. Replaced by story specs from M4 on.
test('the store shell loads', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle('Plant Shop');
  await expect(page.getByRole('heading', { level: 1, name: 'Plant Shop' })).toBeVisible();
});

test('the store shell has no serious accessibility violations', async ({ page }) => {
  await page.goto('/');

  const results = await new AxeBuilder({ page }).analyze();
  const serious = results.violations.filter(
    (v) => v.impact === 'serious' || v.impact === 'critical',
  );
  expect(serious).toEqual([]);
});

test('runtime config is served and never cached', async ({ request }) => {
  const response = await request.get('/config.json');

  expect(response.ok()).toBe(true);
  expect(response.headers()['cache-control']).toContain('no-store');
  const config = (await response.json()) as { apiBaseUrl?: unknown };
  expect(typeof config.apiBaseUrl).toBe('string');
});

test('security headers are present', async ({ request }) => {
  const response = await request.get('/');
  const headers = response.headers();

  expect(headers['content-security-policy']).toContain("default-src 'self'");
  expect(headers['referrer-policy']).toBe('no-referrer');
  expect(headers['x-content-type-options']).toBe('nosniff');
});
