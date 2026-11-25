import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  timeout: 45_000,
  expect: {
    timeout: 15_000
  },
  reporter: [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL || "http://localhost:9527",
    headless: true,
    viewport: { width: 1440, height: 900 },
    trace: "retain-on-failure"
  }
});
