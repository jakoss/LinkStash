import { defineConfig } from "@playwright/test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

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
const webBaseUrl = process.env.E2E_WEB_BASE_URL ?? "http://127.0.0.1:8081";
const browserChannel = process.env.E2E_BROWSER_CHANNEL;
const testRoot = fileURLToPath(new URL("./", import.meta.url));

export default defineConfig({
  testDir: "./specs",
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  retries: process.env.CI ? 2 : 0,
  reporter: [["list"]],
  webServer: [
    {
      command: "./scripts/start-api.sh",
      cwd: testRoot,
      env: process.env,
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
      url: `${apiBaseUrl}/healthz`,
    },
    {
      command: "./scripts/serve-web.sh",
      cwd: testRoot,
      env: process.env,
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
      url: webBaseUrl,
    },
  ],
  use: {
    baseURL: webBaseUrl,
    browserName: "chromium",
    channel: browserChannel,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
