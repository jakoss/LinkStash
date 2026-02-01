import { ConvexHttpClient } from "convex/browser";
import index from "./public/index.html";
import { api } from "./convex/_generated/api";

const port = Number(process.env.PORT ?? 3000);
const convexUrl = process.env.CONVEX_URL ?? "";
const convexClient = convexUrl ? new ConvexHttpClient(convexUrl) : null;

const jsonResponse = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json",
    },
  });

const errorResponse = (message: string, status = 400) =>
  jsonResponse({ error: message }, status);

const parseJson = async <T>(request: Request) => {
  try {
    return (await request.json()) as T;
  } catch {
    return null;
  }
};

Bun.serve({
  port,
  routes: {
    "/": index,
    "/api/spaces": {
      GET: async () => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const spaces = await convexClient.query(api.spaces.listSpaces, {});
        return jsonResponse({ spaces });
      },
      POST: async (request) => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const body = await parseJson<{ name?: string }>(request);
        if (!body?.name) {
          return errorResponse("Space name is required.");
        }
        const spaceId = await convexClient.mutation(api.spaces.createSpace, {
          name: body.name,
        });
        return jsonResponse({ spaceId });
      },
    },
    "/api/spaces/ensure-default": {
      POST: async () => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const space = await convexClient.mutation(api.spaces.ensureDefaultSpace, {});
        return jsonResponse({ space });
      },
    },
    "/api/spaces/:id": {
      PATCH: async (request) => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const body = await parseJson<{ name?: string }>(request);
        if (!body?.name) {
          return errorResponse("Space name is required.");
        }
        await convexClient.mutation(api.spaces.renameSpace, {
          spaceId: request.params.id as never,
          name: body.name,
        });
        return jsonResponse({ ok: true });
      },
      DELETE: async (request) => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        await convexClient.mutation(api.spaces.deleteSpace, {
          spaceId: request.params.id as never,
        });
        return jsonResponse({ ok: true });
      },
    },
    "/api/links": {
      GET: async (request) => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const url = new URL(request.url);
        const spaceId = url.searchParams.get("spaceId");
        const archived = url.searchParams.get("archived");
        if (!spaceId || archived === null) {
          return errorResponse("spaceId and archived are required.");
        }
        const links = await convexClient.query(api.links.listLinks, {
          spaceId: spaceId as never,
          archived: archived === "true",
        });
        return jsonResponse({ links });
      },
      POST: async (request) => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const body = await parseJson<{ url?: string; spaceId?: string }>(request);
        if (!body?.url || !body?.spaceId) {
          return errorResponse("url and spaceId are required.");
        }
        const linkId = await convexClient.mutation(api.links.addLink, {
          spaceId: body.spaceId as never,
          url: body.url,
        });
        return jsonResponse({ linkId });
      },
    },
    "/api/links/:id/archive": {
      PATCH: async (request) => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const body = await parseJson<{ archived?: boolean }>(request);
        if (typeof body?.archived !== "boolean") {
          return errorResponse("archived is required.");
        }
        await convexClient.mutation(api.links.setLinkArchived, {
          linkId: request.params.id as never,
          archived: body.archived,
        });
        return jsonResponse({ ok: true });
      },
    },
    "/api/links/:id/move": {
      PATCH: async (request) => {
        if (!convexClient) {
          return errorResponse("CONVEX_URL is not configured.", 500);
        }
        const body = await parseJson<{ spaceId?: string }>(request);
        if (!body?.spaceId) {
          return errorResponse("spaceId is required.");
        }
        await convexClient.mutation(api.links.moveLink, {
          linkId: request.params.id as never,
          spaceId: body.spaceId as never,
        });
        return jsonResponse({ ok: true });
      },
    },
  },
  development: {
    hmr: true,
    console: true,
  },
});

console.log(`LinkStash web running on http://localhost:${port}`);
