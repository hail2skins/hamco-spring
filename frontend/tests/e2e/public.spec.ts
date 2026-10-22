import { expect, test } from '@playwright/test';

// These requests go through the local Vite proxy to Spring, never to the live
// ASP.NET service. Direct navigation proves that pasted URLs resolve as pages.
test('public Home, Notes, About and detail routes load with the live design', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1, name: 'Hamco Internet Solutions' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recent Notes' })).toBeVisible();
  await page.goto('/notes');
  await expect(page.getByRole('heading', { name: 'Technical Notes & Articles' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Deep Thoughts' })).toBeVisible();
  await page.goto('/about');
  await expect(page.getByRole('heading', { level: 1, name: 'About Hamco', exact: true })).toBeVisible();
  await page.goto('/notes/1');
  await expect(page.getByRole('heading', { level: 1, name: 'Local Spring Boot demo' })).toBeVisible();
});

test('the responsive menu opens and navigates with keyboard-friendly controls', async ({ page, isMobile }) => {
  test.skip(!isMobile, 'Desktop navigation does not collapse.');
  await page.goto('/');
  const toggle = page.getByRole('button', { name: 'Toggle navigation' });
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await toggle.click();
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  await page.getByRole('navigation').getByRole('link', { name: 'Notes' }).click();
  await expect(page.getByRole('heading', { name: 'Deep Thoughts' })).toBeVisible();
});

test('desktop navigation mirrors the reference without a Menu button', async ({ page, isMobile }) => {
  test.skip(isMobile, 'Mobile navigation intentionally has a Menu button.');
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Toggle navigation' })).toBeHidden();
  await expect(page.getByRole('navigation').getByRole('link', { name: 'Notes' })).toBeVisible();
});
