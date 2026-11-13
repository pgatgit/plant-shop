import { expect, test } from '@playwright/test';

// docs/plan/07-auth-design.md §8: Swagger UI "Authorize" with the local Authorization Server
// (client swagger-ui, Authorization Code + PKCE), then a secured call from "Try it out".
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8080';
const PASSWORD = process.env.E2E_USER_PASSWORD ?? 'plants-dev-only';

test('[US-01] Swagger UI authorizes via the local AS and calls a secured endpoint', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium-desktop', 'Swagger UI is a desktop tool');
  const username = `e2e-user-${String((testInfo.workerIndex % 4) + 1)}`;

  await page.goto(`${API_URL}/swagger-ui/index.html`);
  await page.getByRole('button', { name: 'Authorize' }).first().click();

  // Swagger UI's modal has no dialog role; scope locators to its container.
  const dialog = page.locator('.modal-ux');
  await expect(dialog.getByText('authorizationCode with PKCE').first()).toBeVisible();
  await expect(dialog.getByRole('textbox', { name: 'client_id:' })).toHaveValue('swagger-ui');
  await dialog.getByText('select all', { exact: true }).click();

  const popupPromise = page.waitForEvent('popup');
  await dialog.getByRole('button', { name: 'Apply given OAuth2 credentials' }).click();
  const popup = await popupPromise;
  await popup.getByLabel('Username').fill(username);
  await popup.getByLabel('Password').fill(PASSWORD);
  await popup.getByRole('button', { name: 'Sign in' }).click();
  await popup.waitForEvent('close');

  await expect(dialog.getByText('Logout', { exact: true })).toBeVisible();
  await dialog.getByRole('button', { name: 'Close' }).click();

  const getMe = page.locator('#operations-Account-getMe');
  await getMe.locator('.opblock-summary').click();
  await getMe.getByRole('button', { name: 'Try it out' }).click();
  await getMe.getByRole('button', { name: 'Execute' }).click();

  const live = getMe.locator('.live-responses-table');
  await expect(live.locator('tbody .response-col_status').first()).toHaveText('200');
  await expect(live.locator('.microlight').first()).toContainText(`"ownerId": "local:${username}"`);
});

test('[BR-01] the API rejects anonymous calls with a Problem Details 401', async ({ request }) => {
  const response = await request.get(`${API_URL}/api/v1/products`);

  expect(response.status()).toBe(401);
  expect(response.headers()['content-type']).toContain('application/problem+json');
  expect(response.headers()['www-authenticate']).toBe('Bearer');
});
