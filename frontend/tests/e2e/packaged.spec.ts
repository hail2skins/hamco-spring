import { expect, test } from '@playwright/test';

// This separate acceptance test points Playwright at the bootable Spring JAR.
// Vite's dev proxy can mask missing static resources and deep-link fallback.
test('packaged Spring JAR serves browser routes and keeps API/assets separate', async ({ page, request }) => {
  test.skip(!process.env.HAMCO_PACKAGED_BASE_URL, 'Start the packaged JAR and set HAMCO_PACKAGED_BASE_URL.');
  const baseURL = process.env.HAMCO_PACKAGED_BASE_URL!;

  const login = await page.goto('/auth/login?verified=1');
  expect(login?.url()).toBe(`${baseURL}/auth/login?verified=1`);
  await expect(page.getByRole('heading', { level: 1, name: 'Login' })).toBeVisible();
  await expect(page.locator('footer svg')).toHaveCount(6);
  await expect(page.locator('script[src^="http"]')).toHaveCount(0);

  // A browser refresh triggers a new server request. If the server lacks an
  // SPA fallback, the page would become 404 even though client navigation works.
  await page.goto('/notes/1');
  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: 'Local Spring Boot demo' })).toBeVisible();
  await page.goto('/admin/notes/edit/1');
  await expect(page.getByRole('heading', { level: 1, name: 'Edit Note' })).toBeVisible();

  const brokenAsset = await request.get('/assets/no-such-file.js');
  expect(brokenAsset.status()).toBe(404);
  expect(await brokenAsset.text()).not.toContain('<!doctype html>');
  expect((await request.get('/api/unknown')).status()).toBe(401);
  expect((await request.get('/api/admin/api-keys')).status()).toBe(401);
});
