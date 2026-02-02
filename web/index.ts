import index from "./public/index.html";

const port = Number(process.env.PORT ?? 3000);
const convexUrl = process.env.CONVEX_URL ?? "";
const e2eToken = process.env.E2E_BYPASS_TOKEN ?? "";
const e2eEmail = process.env.E2E_EMAIL ?? "";
const envFile = new URL("./public/env.js", import.meta.url);

const envScript = [
  `window.__CONVEX_URL__ = ${JSON.stringify(convexUrl)};`,
  `window.__E2E_BYPASS_TOKEN__ = ${JSON.stringify(e2eToken)};`,
  `window.__E2E_EMAIL__ = ${JSON.stringify(e2eEmail)};`,
].join("\n");

await Bun.write(envFile, envScript);

Bun.serve({
  port,
  routes: {
    "/": index,
  },
  development: {
    hmr: true,
    console: true,
  },
});

console.log(`LinkStash web running on http://localhost:${port}`);
