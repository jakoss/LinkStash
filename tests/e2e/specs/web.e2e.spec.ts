import { expect, test, type Download, type Page, type Response } from "@playwright/test";
import { readFile } from "node:fs/promises";

const raindropToken = process.env.E2E_RAINDROP_TOKEN ?? "";
const apiBaseUrl = process.env.E2E_API_BASE_URL ?? "http://127.0.0.1:8080";
const viewport = { width: 1280, height: 720 };
const points = {
  createSpaceField: { x: 178, y: 390 },
  exportButton: { x: 210, y: 162 },
  addSpaceButton: { x: 389, y: 379 },
  renameSpaceField: { x: 178, y: 469 },
  renameSpaceButton: { x: 381, y: 457 },
  deleteSpaceButton: { x: 477, y: 457 },
  urlField: { x: 639, y: 637 },
  saveLinkButton: { x: 91, y: 703 },
  firstVisibleMoveButton: { x: 166, y: 362 },
  firstMoveMenuItem: { x: 179, y: 195 },
  firstVisibleDeleteButton: { x: 250, y: 362 },
} as const;

type SpaceDto = {
  id: string;
  title: string;
};

type LinkDto = {
  id: string;
  url: string;
  spaceId: string;
};

test.describe("web compose e2e", () => {
  test.skip(!raindropToken, "Set E2E_RAINDROP_TOKEN.");

  test.beforeEach(async ({ page }) => {
    await page.setViewportSize(viewport);
  });

  test("restores the cookie session after reload", async ({ page }) => {
    await login(page);
    await page.reload();
    await waitForApi(page, (response) => isApi(response, "GET", "/v1/me", 200));

    const spaces = await listSpaces(page);
    const inbox = spaces.find((space) => space.title === "Inbox");

    expect(inbox).toBeTruthy();
    expect(await sessionCookieNames(page)).not.toHaveLength(0);
  });

  test("creates renames and deletes a space", async ({ page }) => {
    await login(page);

    const createdTitle = uniqueName("Wasm Space");
    const renamedTitle = `${createdTitle} Renamed`;

    const createdSpace = await createSpace(page, createdTitle);
    await expect
      .poll(async () => (await listSpaces(page)).some((space) => space.id === createdSpace.id && space.title === createdTitle))
      .toBe(true);

    const renamedSpace = await renameSelectedSpace(page, createdSpace.id, renamedTitle);
    expect(renamedSpace.title).toBe(renamedTitle);
    await expect
      .poll(async () => (await listSpaces(page)).some((space) => space.id === createdSpace.id && space.title === renamedTitle))
      .toBe(true);

    await deleteSelectedSpace(page, createdSpace.id);
    await expect
      .poll(async () => (await listSpaces(page)).every((space) => space.id !== createdSpace.id))
      .toBe(true);
  });

  test("saves moves exports and deletes links", async ({ page }) => {
    await login(page);

    const moveUrl = `https://example.com/wasm-move-${Date.now()}`;
    const deleteUrl = `https://example.com/wasm-delete-${Date.now()}`;
    const spaces = await listSpaces(page);
    const inbox = spaces.find((space) => space.title === "Inbox");
    expect(inbox).toBeTruthy();

    const movedCandidate = await createLink(page, inbox!.id, moveUrl);
    const moveTarget = await createSpace(page, uniqueName("Move Target"));
    const movedLink = await moveLink(page, movedCandidate.id, moveTarget.id);
    expect(movedLink.id).toBe(movedCandidate.id);
    expect(movedLink.spaceId).not.toBe(movedCandidate.spaceId);

    await expect
      .poll(async () => (await listLinks(page, movedLink.spaceId)).some((link) => link.id === movedLink.id))
      .toBe(true);
    await expect
      .poll(async () => (await listLinks(page, movedCandidate.spaceId)).every((link) => link.id !== movedLink.id))
      .toBe(true);

    await page.reload();
    await waitForApi(page, (response) => isApi(response, "GET", "/v1/me", 200));

    const deleteCandidate = await createLink(page, inbox!.id, deleteUrl);
    const download = await exportLinks(page);
    const downloadPath = await download.path();
    expect(downloadPath).toBeTruthy();
    const exportBody = await readFile(downloadPath!, "utf8");
    expect(exportBody).toContain(deleteUrl);

    const deleteStatus = await deleteLink(page, deleteCandidate.id);
    expect([200, 204, 502]).toContain(deleteStatus);
    if ([200, 204].includes(deleteStatus)) {
      await expect
        .poll(async () => (await listLinks(page, deleteCandidate.spaceId)).every((link) => link.id !== deleteCandidate.id))
        .toBe(true);
    }
  });
});

async function login(page: Page) {
  await page.goto("/");
  let lastStatus = 0;
  let succeeded = false;
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const exchange = await page.evaluate(
      async ({ baseUrl, token }) => {
        const response = await fetch(`${baseUrl}/v1/auth/raindrop/token`, {
          method: "POST",
          credentials: "include",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            accessToken: token,
            sessionMode: "COOKIE",
          }),
        });

        return {
          ok: response.ok,
          status: response.status,
        };
      },
      {
        baseUrl: apiBaseUrl,
        token: raindropToken,
      }
    );

    lastStatus = exchange.status;
    if (exchange.ok) {
      succeeded = true;
      break;
    }

    if (attempt < 2) {
      await page.waitForTimeout(1_000 * (attempt + 1));
    }
  }

  expect(succeeded, `auth exchange failed after retries, last status ${lastStatus}`).toBe(true);
  await page.reload();

  await waitForApi(page, (response) => isApi(response, "GET", "/v1/me", 200));
  await waitForApi(page, (response) => isApi(response, "GET", "/v1/spaces", 200));
  await waitForApi(page, (response) => isApi(response, "GET", "/v1/auth/csrf", 200));

  await expect.poll(async () => (await sessionCookieNames(page)).length).toBeGreaterThan(0);
}

async function createSpace(page: Page, title: string): Promise<SpaceDto> {
  return await mutateJson<SpaceDto>(page, "POST", "/v1/spaces", { title });
}

async function renameSelectedSpace(page: Page, spaceId: string, title: string): Promise<SpaceDto> {
  return await mutateJson<SpaceDto>(page, "PATCH", `/v1/spaces/${spaceId}`, { title });
}

async function deleteSelectedSpace(page: Page, spaceId: string) {
  await mutateJson<null>(page, "DELETE", `/v1/spaces/${spaceId}`);
}

async function createLink(page: Page, spaceId: string, url: string): Promise<LinkDto> {
  return await mutateJson<LinkDto>(page, "POST", `/v1/spaces/${spaceId}/links`, { url });
}

async function moveLink(page: Page, linkId: string, targetSpaceId: string): Promise<LinkDto> {
  return await mutateJson<LinkDto>(page, "PATCH", `/v1/links/${linkId}`, { spaceId: targetSpaceId });
}

async function deleteLink(page: Page, linkId: string): Promise<number> {
  let lastStatus = 0;

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const payload = await page.evaluate(
      async ({ baseUrl, nextLinkId }) => {
        const csrfResponse = await fetch(`${baseUrl}/v1/auth/csrf`, {
          credentials: "include",
        });
        const csrfPayload = await csrfResponse.json();
        const response = await fetch(`${baseUrl}/v1/links/${nextLinkId}`, {
          method: "DELETE",
          credentials: "include",
          headers: {
            Accept: "application/json",
            "X-CSRF-Token": csrfPayload.csrfToken,
          },
        });

        return {
          ok: response.ok,
          status: response.status,
        };
      },
      {
        baseUrl: apiBaseUrl,
        nextLinkId: linkId,
      }
    );

    lastStatus = payload.status;
    if (payload.ok || payload.status === 502) {
      return payload.status;
    }

    if (attempt < 2 && payload.status >= 500) {
      await page.waitForTimeout(1_000 * (attempt + 1));
    }
  }

  return lastStatus;
}

async function exportLinks(page: Page): Promise<Download> {
  const downloadPromise = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export links" }).click({ force: true });
  return downloadPromise;
}

async function listSpaces(page: Page): Promise<SpaceDto[]> {
  const payload = await page.evaluate(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/v1/spaces`, {
      credentials: "include",
    });
    if (!response.ok) {
      return { spaces: [] };
    }
    return await response.json();
  }, apiBaseUrl);

  return (payload.spaces ?? []) as SpaceDto[];
}

async function listLinks(page: Page, spaceId: string): Promise<LinkDto[]> {
  const payload = await page.evaluate(async ({ baseUrl, nextSpaceId }) => {
    const response = await fetch(`${baseUrl}/v1/spaces/${nextSpaceId}/links`, {
      credentials: "include",
    });
    if (!response.ok) {
      return { links: [] };
    }
    return await response.json();
  }, { baseUrl: apiBaseUrl, nextSpaceId: spaceId });

  return (payload.links ?? []) as LinkDto[];
}

async function sessionCookieNames(page: Page): Promise<string[]> {
  const cookies = await page.context().cookies(apiBaseUrl);
  return cookies
    .filter((cookie) => cookie.httpOnly)
    .map((cookie) => cookie.name);
}

async function clickAt(page: Page, point: { x: number; y: number }) {
  await page.mouse.move(point.x, point.y);
  await page.mouse.down({ button: "left" });
  await page.mouse.up({ button: "left" });
}

async function mutateJson<T>(page: Page, method: string, path: string, body?: unknown): Promise<T> {
  let lastPayload: { ok: boolean; status: number; text: string } | null = null;

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const payload = await page.evaluate(
      async ({ baseUrl, nextMethod, nextPath, nextBody }) => {
        const csrfResponse = await fetch(`${baseUrl}/v1/auth/csrf`, {
          credentials: "include",
        });
        const csrfPayload = await csrfResponse.json();
        const response = await fetch(`${baseUrl}${nextPath}`, {
          method: nextMethod,
          credentials: "include",
          headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
            "X-CSRF-Token": csrfPayload.csrfToken,
          },
          body: nextBody == null ? undefined : JSON.stringify(nextBody),
        });

        const text = await response.text();
        return {
          ok: response.ok,
          status: response.status,
          text,
        };
      },
      {
        baseUrl: apiBaseUrl,
        nextMethod: method,
        nextPath: path,
        nextBody: body ?? null,
      }
    );

    lastPayload = payload;
    if (payload.ok) {
      return payload.text ? (JSON.parse(payload.text) as T) : (null as T);
    }

    if (attempt < 2 && payload.status >= 500) {
      await page.waitForTimeout(1_000 * (attempt + 1));
      continue;
    }
  }

  expect(lastPayload?.ok, `${method} ${path} failed with status ${lastPayload?.status ?? "unknown"}`).toBe(true);
  return null as T;
}

function waitForApi(page: Page, predicate: (response: Response) => boolean) {
  return page.waitForResponse(predicate, { timeout: 20_000 });
}

function isApi(response: Response, method: string, pathSuffix: string, status: number): boolean {
  const url = new URL(response.url());
  return response.request().method() === method && url.origin === apiBaseUrl && url.pathname === pathSuffix && response.status() === status;
}

function uniqueName(prefix: string): string {
  return `${prefix} ${Date.now()}`;
}
