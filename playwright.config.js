import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'src/test/e2e',
  timeout: 30_000,
  use: {
    baseURL: 'http://127.0.0.1:8080'
  },
  webServer: {
    command: './gradlew bootRun',
    port: 8080,
    reuseExistingServer: true,
    timeout: 180_000
  }
});
