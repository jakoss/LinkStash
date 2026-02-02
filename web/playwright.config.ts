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
  } catch (error) {
    return {};
  }
};

const fileEnv = loadDotEnv();

for (const [key, value] of Object.entries(fileEnv)) {
  if (!process.env[key]) {
    process.env[key] = value;
  }
}

const envValue = (key: string) => process.env[key] ?? fileEnv[key] ?? "";

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
      CONVEX_URL: envValue("CONVEX_URL"),
      E2E_BYPASS_TOKEN: envValue("E2E_BYPASS_TOKEN"),
      E2E_EMAIL: envValue("E2E_EMAIL"),
    },
  },
});
