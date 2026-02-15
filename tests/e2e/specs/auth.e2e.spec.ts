import { expect, test } from "@playwright/test";

const redirectUri = process.env.E2E_OAUTH_REDIRECT_URI ?? "http://localhost:8080/v1/auth/raindrop/callback";
const raindropEmail = process.env.E2E_RAINDROP_EMAIL ?? "";
const raindropPassword = process.env.E2E_RAINDROP_PASSWORD ?? "";

test.describe("auth e2e", () => {
  test.skip(!raindropEmail || !raindropPassword, "Set E2E_RAINDROP_EMAIL and E2E_RAINDROP_PASSWORD.");

  test("oauth exchange issues and revokes bearer session", async ({ page, request }) => {
    const health = await request.get("/healthz");
    expect(health.status()).toBe(200);

    const startResponse = await request.get("/v1/auth/raindrop/start", {
      params: { redirectUri },
    });
    expect(startResponse.ok()).toBeTruthy();

    const startBody = (await startResponse.json()) as { url: string };
    expect(startBody.url).toContain("state=");

    const callbackRequestPromise = page.waitForRequest(
      (request) => request.url().startsWith(redirectUri) && request.url().includes("code="),
      { timeout: 45_000 }
    ).catch(() => null);

    await page.goto(startBody.url, { waitUntil: "domcontentloaded" });

    await maybeSignIn(page, raindropEmail, raindropPassword);
    await maybeConsent(page);

    let callbackUrl: URL | null = null;
    try {
      await page.waitForURL((url) => url.toString().startsWith(redirectUri), { timeout: 10_000 });
      callbackUrl = new URL(page.url());
    } catch (_navigationError) {
      const callbackRequest = await callbackRequestPromise;
      callbackUrl = callbackRequest ? new URL(callbackRequest.url()) : null;
    }

    expect(callbackUrl).toBeTruthy();
    const code = callbackUrl?.searchParams.get("code") ?? null;
    const state = callbackUrl?.searchParams.get("state") ?? null;

    expect(code).toBeTruthy();
    expect(state).toBeTruthy();

    const exchangeResponse = await request.post("/v1/auth/raindrop/exchange", {
      data: {
        code,
        state,
        redirectUri,
        sessionMode: "BEARER",
      },
    });
    expect(exchangeResponse.ok()).toBeTruthy();

    const exchangeBody = (await exchangeResponse.json()) as {
      user: { id: string; displayName?: string | null };
      bearerToken?: string;
    };

    expect(exchangeBody.user.id).toBeTruthy();
    expect(exchangeBody.bearerToken).toBeTruthy();

    const authHeaders = {
      Authorization: `Bearer ${exchangeBody.bearerToken}`,
    };

    const meResponse = await request.get("/v1/me", { headers: authHeaders });
    expect(meResponse.status()).toBe(200);

    const meBody = (await meResponse.json()) as { id: string; displayName?: string | null };
    expect(meBody.id).toBe(exchangeBody.user.id);

    const logoutResponse = await request.post("/v1/auth/logout", { headers: authHeaders });
    expect(logoutResponse.status()).toBe(204);

    const meAfterLogoutResponse = await request.get("/v1/me", { headers: authHeaders });
    expect(meAfterLogoutResponse.status()).toBe(401);
  });
});

async function maybeSignIn(page: import("@playwright/test").Page, email: string, password: string): Promise<void> {
  const emailInput = page.locator('input[name="email"]');
  const passwordInput = page.locator('input[name="password"]');

  const hasSignInForm = await emailInput.isVisible({ timeout: 5_000 }).catch(() => false);
  if (!hasSignInForm) {
    return;
  }

  await emailInput.fill(email);
  await passwordInput.fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

async function maybeConsent(page: import("@playwright/test").Page): Promise<void> {
  const agreeButton = page.getByRole("button", { name: "Agree", exact: true });
  const hasConsent = await agreeButton.isVisible({ timeout: 8_000 }).catch(() => false);

  if (hasConsent) {
    await agreeButton.click();
  }
}
