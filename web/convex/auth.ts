import { convexAuth, createAccount, retrieveAccount } from "@convex-dev/auth/server";
import { ConvexCredentials } from "@convex-dev/auth/providers/ConvexCredentials";
import Resend from "@auth/core/providers/resend";

const dayMs = 1000 * 60 * 60 * 24;

export const { auth, signIn, signOut, store, isAuthenticated } = convexAuth({
  providers: [
    Resend({
      from: process.env.AUTH_EMAIL_FROM ?? "no-reply@linkstash.app",
    }),
    ...(process.env.E2E_BYPASS_TOKEN
      ? [
          ConvexCredentials({
            id: "e2e",
            authorize: async (credentials, ctx) => {
              const token = credentials?.token;
              const email =
                typeof credentials?.email === "string" && credentials.email.trim()
                  ? credentials.email.trim()
                  : "e2e@linkstash.dev";

              if (!process.env.E2E_BYPASS_TOKEN || token !== process.env.E2E_BYPASS_TOKEN) {
                return null;
              }

              let existing: Awaited<ReturnType<typeof retrieveAccount>> | null = null;
              try {
                existing = await retrieveAccount(ctx, {
                  provider: "e2e",
                  account: { id: email },
                });
              } catch (error) {
                if (error instanceof Error && error.message === "InvalidAccountId") {
                  existing = null;
                } else {
                  throw error;
                }
              }

              if (existing) {
                return { userId: existing.user._id };
              }

              const created = await createAccount(ctx, {
                provider: "e2e",
                account: { id: email },
                profile: { email },
              });

              return { userId: created.user._id };
            },
          }),
        ]
      : []),
  ],
  session: {
    inactiveDurationMs: 30 * dayMs,
    totalDurationMs: 3650 * dayMs,
  },
});
