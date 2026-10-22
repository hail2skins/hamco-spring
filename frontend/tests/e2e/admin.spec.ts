import { expect, test, type Page } from '@playwright/test';

// These tests need the dedicated LOCAL hamco_spring_dev database and its
// throwaway administrator. Never point the Vite proxy at hamcois.com: the
// browser creates and deletes records to exercise real Spring write paths.
const password = process.env.HAMCO_E2E_ADMIN_PASSWORD;
test.skip(!password, 'Provide a local-only admin password via HAMCO_E2E_ADMIN_PASSWORD.');
test.use({ trace: 'off' }); // A key-creation response contains a one-time secret.

async function signIn(page: Page) {
  await page.goto('/auth/login');
  await page.getByRole('textbox', { name: 'Email address' }).fill('local-admin@hamco.test');
  await page.getByLabel('Password').fill(password!);
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL('/');
}

async function navigateAdmin(page: Page, name: string, isMobile: boolean) {
  // The source site's small-screen navigation is collapsed until Menu opens.
  if (isMobile) await page.getByRole('button', { name: 'Toggle navigation' }).click();
  await page.getByRole('navigation').getByRole('link', { name }).click();
}

test('local Spring login, note CRUD, and post-reload write protection', async ({ page, isMobile }) => {
  await signIn(page);
  await navigateAdmin(page, 'Notes', isMobile);
  await expect(page.getByRole('heading', { name: 'Manage Notes' })).toBeVisible();
  await page.getByRole('link', { name: 'Create New Note' }).click();
  const title = `Browser fixture ${Date.now()}`;
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Content (Markdown)').fill('A browser-tested local note.');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page).toHaveURL('/admin/notes');
  const row = page.getByRole('row', { name: new RegExp(title) });
  await expect(row).toBeVisible();
  await row.getByRole('link', { name: 'Edit' }).click();
  await page.getByLabel('Content (Markdown)').fill('An edited browser-tested note.');
  await page.getByRole('button', { name: 'Save Changes' }).click();
  await expect(page.getByRole('row', { name: new RegExp(title) })).toBeVisible();
  await page.getByRole('row', { name: new RegExp(title) }).getByRole('link', { name: 'Delete' }).click();
  await page.getByRole('button', { name: 'Delete Note' }).click();
  await expect(page.getByRole('row', { name: new RegExp(title) })).toHaveCount(0);

  // The HttpOnly cookie can restore the profile after a reload, but the JWT
  // is deliberately memory-only. Mutation controls must ask for sign-in.
  await page.reload();
  await expect(page.getByText('Sign in again to manage content.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Create New Note' })).toHaveCount(0);
});

test('local Spring slogan editing and API-key issuance/revocation', async ({ page, isMobile }) => {
  await signIn(page);
  await navigateAdmin(page, 'Slogans', isMobile);
  await expect(page.getByRole('heading', { name: 'Manage Slogans' })).toBeVisible();
  await page.getByRole('link', { name: 'Create New Slogan' }).click();
  const text = `Browser slogan ${Date.now()}`;
  await page.getByLabel('Content').fill(text);
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  const row = page.getByRole('row', { name: new RegExp(text) });
  await expect(row).toBeVisible();
  await row.getByRole('link', { name: 'Edit' }).click();
  await page.getByLabel('Content').fill(`${text} revised`);
  await page.getByRole('button', { name: 'Save Changes' }).click();
  await page.getByRole('row', { name: new RegExp(text) }).getByRole('link', { name: 'Delete' }).click();
  await page.getByRole('button', { name: 'Delete Slogan' }).click();
  await expect(page.getByRole('row', { name: new RegExp(text) })).toHaveCount(0);

  await navigateAdmin(page, 'API Keys', isMobile);
  await expect(page.getByRole('heading', { name: 'Manage API Keys' })).toBeVisible();
  await page.getByRole('link', { name: 'Create New API Key' }).click();
  const keyName = `Browser key ${Date.now()}`;
  await page.getByLabel('Key Name').fill(keyName);
  await page.getByRole('button', { name: 'Generate API Key' }).click();
  await expect(page.getByText("This is the only time you'll see this key!")).toBeVisible();
  await expect(page.getByLabel('Your API Key:')).not.toBeEmpty();
  await page.getByRole('link', { name: /Go to API Keys List/ }).click();
  const keyRow = page.getByRole('row', { name: new RegExp(keyName) });
  await expect(keyRow).toBeVisible();
  await keyRow.getByRole('button', { name: 'Revoke' }).click();
  await page.getByRole('button', { name: 'Confirm Revoke' }).click();
  await expect(keyRow.getByText('Revoked')).toBeVisible();
  await keyRow.getByRole('link', { name: keyName }).click();
  await expect(page.getByText('The full API key cannot be displayed again.')).toBeVisible();
});
