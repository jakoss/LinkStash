import React, { useState } from "react";
import { useAuthActions } from "@convex-dev/auth/react";

export const SignIn: React.FC = () => {
  const { signIn } = useAuthActions();
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState("");
  const e2eToken = process.env.E2E_BYPASS_TOKEN;
  const e2eEmail = process.env.E2E_EMAIL;

  return (
    <div className="page">
      <main className="main">
        <section className="card">
          <div className="section-title">Sign in</div>
          <h1>Welcome back</h1>
          <p className="muted">We will email you a magic link to sign in.</p>
          <form
            className="auth-form"
            onSubmit={async (event) => {
              event.preventDefault();
              setStatus("");
              if (!email.trim()) {
                setStatus("Enter your email to continue.");
                return;
              }
              const result = await signIn("resend", {
                email: email.trim(),
                redirectTo: "/",
              });
              if (!result.signingIn) {
                setStatus("Check your email for a sign-in link.");
              }
            }}
          >
            <div>
              <label htmlFor="email-input">Email address</label>
              <input
                id="email-input"
                name="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
              />
            </div>
            <button className="button button-primary" type="submit">
              Send magic link
            </button>
          </form>
          {e2eToken ? (
            <button
              className="button button-secondary"
              type="button"
              onClick={async () => {
                setStatus("");
                const result = await signIn("e2e", {
                  token: e2eToken,
                  email: e2eEmail || email || "e2e@linkstash.dev",
                });
                if (!result.signingIn) {
                  setStatus("E2E sign-in failed.");
                }
              }}
            >
              E2E sign in
            </button>
          ) : null}
          {status ? <div className="status">{status}</div> : null}
        </section>
      </main>
    </div>
  );
};
