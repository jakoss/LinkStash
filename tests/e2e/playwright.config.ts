import { defineConfig } from "@playwright/test";
import { readFileSync } from "node:fs";

const loadDotEnv = () => {
  try {
    const envFile = new URL("./.env", import.meta.url);
    const content = readFileSync(envFile, "utf8");
    const entries: Record<string, string> = {};

    for (const line of content.split("\n")) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) {
        continue;
      }

      const [key, ...rest] = trimmed.split("=");
      if (!key) {
        continue;
      }

      const rawValue = rest.join("=").trim();
      entries[key] = rawValue.replace(/^"(.*)"$/, "$1").replace(/^'(.*)'$/, "$1");
    }

    return entries;
  } catch (_error) {
    return {};
  }
};

const fileEnv = loadDotEnv();
for (const [key, value] of Object.entries(fileEnv)) {
  if (!process.env[key]) {
    process.env[key] = value;
  }
}

const apiBaseUrl = process.env.E2E_API_BASE_URL ?? "http://127.0.0.1:8080";
const browserChannel = process.env.E2E_BROWSER_CHANNEL;

export default defineConfig({
  testDir: "./specs",
  fullyParallel: false,
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  retries: process.env.CI ? 2 : 0,
  reporter: [["list"]],
  use: {
    baseURL: apiBaseUrl,
    browserName: "chromium",
    channel: browserChannel,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
