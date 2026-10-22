import { defineConfig, devices } from '@playwright/test';

// The packaged-artifact suite must NOT start Vite: Vite's fallback and proxy
// could turn a broken Spring JAR into a false-positive browser test.
const packagedBaseURL = process.env.HAMCO_PACKAGED_BASE_URL;

export default defineConfig({
  testDir: './tests/e2e',
  use: { baseURL: packagedBaseURL ?? 'http://127.0.0.1:5173', trace: 'on-first-retry' },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'mobile', use: { ...devices['iPhone 13'], defaultBrowserType: 'chromium' } },
  ],
  webServer: packagedBaseURL ? undefined : {
    command: 'npm run dev -- --port 5173',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
