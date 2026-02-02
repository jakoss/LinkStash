import React, { useEffect } from "react";
import { useAuthActions } from "@convex-dev/auth/react";

type AuthErrorFallbackProps = {
  error: Error;
};

export const AuthErrorFallback: React.FC<AuthErrorFallbackProps> = ({ error }) => {
  const { signOut } = useAuthActions();
  const isAuthError = /not authenticated/i.test(error.message);

  useEffect(() => {
    if (isAuthError) {
      signOut();
    }
  }, [isAuthError, signOut]);

  return (
    <div className="page">
      <main className="main">
        <section className="card">
          <div className="section-title">Session</div>
          <h1>Sign in again</h1>
          <p className="muted">
            {isAuthError
              ? "Your session expired. Please sign in again."
              : "We hit an unexpected error. Please try again."}
          </p>
          <button className="button button-primary" type="button" onClick={() => location.reload()}>
            Reload
          </button>
        </section>
      </main>
    </div>
  );
};
