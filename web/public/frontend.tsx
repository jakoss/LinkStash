import { createRoot } from "react-dom/client";
import { ConvexAuthProvider } from "@convex-dev/auth/react";
import { ErrorBoundary } from "./components/Auth/ErrorBoundary";
import { App } from "./app/App";
import { convex } from "./lib/convexClient";
import "./styles.css";

const rootElement = document.querySelector<HTMLDivElement>("#root");
const globalRoot = globalThis as typeof globalThis & {
  __LINKSTASH_ROOT__?: ReturnType<typeof createRoot>;
};

if (rootElement) {
  if (!convex) {
    rootElement.innerHTML = "<p>CONVEX_URL is not configured.</p>";
  } else {
    if (!globalRoot.__LINKSTASH_ROOT__) {
      globalRoot.__LINKSTASH_ROOT__ = createRoot(rootElement);
    }
    globalRoot.__LINKSTASH_ROOT__.render(
      <ConvexAuthProvider client={convex}>
        <ErrorBoundary>
          <App />
        </ErrorBoundary>
      </ConvexAuthProvider>,
    );
  }
}
