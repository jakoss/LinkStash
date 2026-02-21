import { expect, test } from "@playwright/test";

const raindropToken = process.env.E2E_RAINDROP_TOKEN ?? "";

test.describe("auth e2e", () => {
  test.skip(!raindropToken, "Set E2E_RAINDROP_TOKEN.");

  test("token exchange issues and revokes bearer session", async ({ request }) => {
    const health = await request.get("/healthz");
    expect(health.status()).toBe(200);

    const exchangeResponse = await request.post("/v1/auth/raindrop/token", {
      data: {
        accessToken: raindropToken,
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
