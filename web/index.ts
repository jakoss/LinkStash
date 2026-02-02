import { fileURLToPath } from "node:url";

const port = Number(process.env.PORT ?? 3000);
const entrypoint = fileURLToPath(new URL("./public/index.html", import.meta.url));
const outdir = fileURLToPath(new URL("./public/.dist", import.meta.url));
const distBase = new URL("./public/.dist/", import.meta.url);

const result = await Bun.build({
  entrypoints: [entrypoint],
  outdir,
  target: "browser",
  env: "inline",
});

if (!result.success) {
  for (const log of result.logs) {
    console.error(log);
  }
  throw new Error("Failed to bundle frontend assets.");
}

Bun.serve({
  port,
  fetch: async (req) => {
    const url = new URL(req.url);
    let pathname = decodeURIComponent(url.pathname);
    if (pathname === "/") {
      pathname = "/index.html";
    }
    if (pathname.includes("..")) {
      return new Response("Not found", { status: 404 });
    }
    const file = Bun.file(new URL(`.${pathname}`, distBase));
    if (await file.exists()) {
      return new Response(file);
    }
    return new Response("Not found", { status: 404 });
  },
});

console.log(`LinkStash web running on http://localhost:${port}`);
