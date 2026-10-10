import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1, timeout: 45000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env['E2E_URL'] ?? 'http://127.0.0.1:14200', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
