import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 30_000,
  use: {
    baseURL: "http://127.0.0.1:3000",
    trace: "on-first-retry",
  },
  webServer: {
    command: "bun index.ts",
    port: 3000,
    reuseExistingServer: false,
    env: {
      PORT: "3000",
      CONVEX_URL: process.env.CONVEX_URL ?? "",
      E2E_BYPASS_TOKEN: process.env.E2E_BYPASS_TOKEN ?? "",
      E2E_EMAIL: process.env.E2E_EMAIL ?? "",
    },
  },
});
