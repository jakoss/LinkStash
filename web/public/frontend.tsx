import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  ConvexReactClient,
  useConvex,
  useConvexAuth,
  useMutation,
  useQuery,
} from "convex/react";
import { ConvexAuthProvider, useAuthActions, useAuthToken } from "@convex-dev/auth/react";
import { api } from "../convex/_generated/api";
import "./styles.css";

type Space = {
  _id: string;
  name: string;
  isDefault: boolean;
};

type Link = {
  _id: string;
  url: string;
  title?: string;
  description?: string;
  imageUrl?: string;
  siteName?: string;
  faviconUrl?: string;
  archived: boolean;
  createdAt: number;
  spaceId: string;
};

type ModalState =
  | { kind: "none" }
  | { kind: "createSpace" }
  | { kind: "renameSpace"; space: Space }
  | { kind: "deleteSpace"; space: Space };

const getConvexUrl = () => {
  const metaEnv = (import.meta as { env?: Record<string, string> }).env;
  const fromMeta = metaEnv?.CONVEX_URL;
  const fromProcess = (globalThis as { process?: { env?: Record<string, string> } }).process?.env
    ?.CONVEX_URL;
  const fromWindow = (globalThis as { __CONVEX_URL__?: string }).__CONVEX_URL__;
  return fromMeta || fromProcess || fromWindow || "";
};

const convexUrl = getConvexUrl();
const convex = convexUrl ? new ConvexReactClient(convexUrl) : null;

const Modal: React.FC<{ children: React.ReactNode; onClose: () => void }> = ({
  children,
  onClose,
}) => {
  return (
    <div
      className="modal-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div className="modal">{children}</div>
    </div>
  );
};

const SignIn: React.FC = () => {
  const { signIn } = useAuthActions();
  const [email, setEmail] = useState("");
  const [status, setStatus] = useState("");
  const e2eToken = (globalThis as { __E2E_BYPASS_TOKEN__?: string }).__E2E_BYPASS_TOKEN__;
  const e2eEmail = (globalThis as { __E2E_EMAIL__?: string }).__E2E_EMAIL__;

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

class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { error: Error | null }
> {
  state = { error: null } as { error: Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error) {
    console.error(error);
  }

  render() {
    if (this.state.error) {
      return <AuthErrorFallback error={this.state.error} />;
    }
    return this.props.children;
  }
}

const AuthErrorFallback: React.FC<{ error: Error }> = ({ error }) => {
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

const AuthedApp = () => {
  const { signOut } = useAuthActions();
  const convexClient = useConvex();
  const spaces = useQuery(api.spaces.listSpaces, {}) ?? [];
  const [currentSpaceId, setCurrentSpaceId] = useState<string>("");
  const [archived, setArchived] = useState(false);
  const [modal, setModal] = useState<ModalState>({ kind: "none" });
  const [urlInput, setUrlInput] = useState("");
  const [status, setStatus] = useState("");

  const links =
    useQuery(
      api.links.listLinks,
      currentSpaceId
        ? {
            spaceId: currentSpaceId as never,
            archived,
          }
        : "skip",
    ) ?? [];

  const ensureDefaultSpace = useMutation(api.spaces.ensureDefaultSpace);
  const createSpace = useMutation(api.spaces.createSpace);
  const renameSpace = useMutation(api.spaces.renameSpace);
  const deleteSpace = useMutation(api.spaces.deleteSpace);
  const addLink = useMutation(api.links.addLink);
  const moveLink = useMutation(api.links.moveLink);
  const setLinkArchived = useMutation(api.links.setLinkArchived);

  const currentSpace = useMemo(
    () => spaces.find((space) => space._id === currentSpaceId),
    [spaces, currentSpaceId],
  );

  useEffect(() => {
    ensureDefaultSpace().catch((error) => {
      setStatus(error instanceof Error ? error.message : "Failed to initialize.");
    });
  }, [ensureDefaultSpace]);

  useEffect(() => {
    if (!currentSpaceId && spaces.length > 0) {
      const defaultSpace = spaces.find((space) => space.isDefault) ?? spaces[0];
      if (defaultSpace) {
        setCurrentSpaceId(defaultSpace._id);
      }
    }
  }, [currentSpaceId, spaces]);

  const handleAddLink = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const url = urlInput.trim();
    if (!url || !currentSpaceId) {
      return;
    }
    await addLink({
      spaceId: currentSpaceId as never,
      url,
    });
    setUrlInput("");
  };

  const handleArchiveToggle = async (link: Link) => {
    await setLinkArchived({
      linkId: link._id as never,
      archived: !link.archived,
    });
  };

  const handleMove = async (link: Link, spaceId: string) => {
    await moveLink({
      linkId: link._id as never,
      spaceId: spaceId as never,
    });
  };

  const handleExport = async () => {
    if (!currentSpaceId) {
      return;
    }
    try {
      const data = await convexClient.query(api.links.listLinks, {
        spaceId: currentSpaceId as never,
        archived: false,
      });
      const payload = data.map((link) => link.url).join("\n");
      await navigator.clipboard.writeText(payload);
      setStatus(payload ? "Copied active links to clipboard." : "No active links to export.");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Failed to export links.");
    }
  };

  const countText = `${links.length} ${archived ? "archived" : "active"} link${
    links.length === 1 ? "" : "s"
  }`;

  return (
    <div className="page">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">LinkStash</span>
          <span className="brand-sub">Quick capture, calm review</span>
        </div>

        <button className="button button-primary" onClick={() => setModal({ kind: "createSpace" })}>
          New space
        </button>

        <div className="section-title">Spaces</div>
        <div className="space-list">
          {spaces.map((space) => (
            <div
              key={space._id}
              className={`space-item${space._id === currentSpaceId ? " is-active" : ""}`}
              onClick={() => setCurrentSpaceId(space._id)}
            >
              <span>{space.name}</span>
              <div className="space-actions" onClick={(event) => event.stopPropagation()}>
                <button type="button" onClick={() => setModal({ kind: "renameSpace", space })}>
                  Rename
                </button>
                <button
                  type="button"
                  disabled={space.isDefault}
                  onClick={() => setModal({ kind: "deleteSpace", space })}
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
        <button className="button button-ghost" type="button" onClick={() => signOut()}>
          Sign out
        </button>
      </aside>

      <main className="main">
        <header className="header">
          <div>
            <div className="eyebrow">Current space</div>
            <h1>{currentSpace?.name ?? ""}</h1>
            <p className="muted">{countText}</p>
          </div>
          <div className="header-actions">
            <button className="button button-secondary" type="button" onClick={handleExport}>
              Export active links
            </button>
            <div className="toggle" role="tablist">
              <button
                className={`toggle-button${!archived ? " is-active" : ""}`}
                type="button"
                onClick={() => setArchived(false)}
              >
                Active
              </button>
              <button
                className={`toggle-button${archived ? " is-active" : ""}`}
                type="button"
                onClick={() => setArchived(true)}
              >
                Archived
              </button>
            </div>
          </div>
        </header>

        <section className="card">
          <form className="add-link" onSubmit={handleAddLink}>
            <div>
              <label htmlFor="url-input">Paste a link</label>
              <input
                id="url-input"
                name="url"
                type="url"
                placeholder="https://example.com"
                required
                value={urlInput}
                onChange={(event) => setUrlInput(event.target.value)}
              />
            </div>
            <button className="button button-primary" type="submit">
              Add link
            </button>
          </form>
        </section>

        <section className="card">
          <div className="section-title">Links</div>
          <div className="link-list">
            {links.length === 0 ? (
              <div className="muted">
                {archived ? "No archived links yet." : "No links here yet. Add your first URL."}
              </div>
            ) : (
              links.map((link) => (
                <article key={link._id} className="link-item">
                  <div className="link-header">
                    {link.faviconUrl ? (
                      <img className="link-favicon" src={link.faviconUrl} alt="" loading="lazy" />
                    ) : (
                      <div className="link-favicon link-favicon--placeholder">URL</div>
                    )}
                    <div>
                      <div className="link-title">{link.title || link.siteName || link.url}</div>
                      <div className="link-url">{link.url}</div>
                    </div>
                  </div>
                  {link.imageUrl ? (
                    <img className="link-image" src={link.imageUrl} alt="" loading="lazy" />
                  ) : null}
                  {link.description ? <div className="muted">{link.description}</div> : null}
                  <div className="link-actions">
                    <a
                      className="button button-success"
                      href={link.url}
                      target="_blank"
                      rel="noreferrer"
                    >
                      Open
                    </a>
                    <button
                      className={`button ${link.archived ? "button-secondary" : "button-danger"}`}
                      type="button"
                      onClick={() => handleArchiveToggle(link)}
                    >
                      {link.archived ? "Restore" : "Archive"}
                    </button>
                    <select
                      value=""
                      onChange={(event) => {
                        if (event.target.value) {
                          handleMove(link, event.target.value).catch((error) => {
                            setStatus(
                              error instanceof Error ? error.message : "Something went wrong",
                            );
                          });
                          event.target.value = "";
                        }
                      }}
                    >
                      <option value="" disabled>
                        Move to...
                      </option>
                      {spaces
                        .filter((space) => space._id !== link.spaceId)
                        .map((space) => (
                          <option key={space._id} value={space._id}>
                            {space.name}
                          </option>
                        ))}
                    </select>
                  </div>
                </article>
              ))
            )}
          </div>
        </section>

        <div className="status" aria-live="polite">
          {status}
        </div>
      </main>

      {modal.kind !== "none" ? (
        <Modal onClose={() => setModal({ kind: "none" })}>
          {modal.kind === "createSpace" ? (
            <SpaceCreateForm
              onCancel={() => setModal({ kind: "none" })}
              onCreate={async (name) => {
                await createSpace({ name });
                setModal({ kind: "none" });
              }}
            />
          ) : null}
          {modal.kind === "renameSpace" ? (
            <SpaceRenameForm
              space={modal.space}
              onCancel={() => setModal({ kind: "none" })}
              onRename={async (name) => {
                await renameSpace({ spaceId: modal.space._id as never, name });
                setModal({ kind: "none" });
              }}
            />
          ) : null}
          {modal.kind === "deleteSpace" ? (
            <SpaceDeleteForm
              space={modal.space}
              onCancel={() => setModal({ kind: "none" })}
              onDelete={async () => {
                await deleteSpace({ spaceId: modal.space._id as never });
                setModal({ kind: "none" });
                if (currentSpaceId === modal.space._id) {
                  setCurrentSpaceId("");
                }
              }}
            />
          ) : null}
        </Modal>
      ) : null}
    </div>
  );
};

const App = () => {
  const { isAuthenticated, isLoading } = useConvexAuth();
  const authToken = useAuthToken();

  if (isLoading) {
    return <div className="status">Loading...</div>;
  }

  if (!isAuthenticated || !authToken) {
    return <SignIn />;
  }

  return <AuthedApp />;
};

const SpaceCreateForm: React.FC<{
  onCancel: () => void;
  onCreate: (name: string) => Promise<void>;
}> = ({ onCancel, onCreate }) => {
  const [value, setValue] = useState("");

  return (
    <>
      <h3>New space</h3>
      <input
        placeholder="Space name"
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
      <div className="modal-actions">
        <button className="button button-ghost" type="button" onClick={onCancel}>
          Cancel
        </button>
        <button
          className="button button-primary"
          type="button"
          onClick={() => {
            if (value.trim()) {
              onCreate(value.trim());
            }
          }}
        >
          Create
        </button>
      </div>
    </>
  );
};

const SpaceRenameForm: React.FC<{
  space: Space;
  onCancel: () => void;
  onRename: (name: string) => Promise<void>;
}> = ({ space, onCancel, onRename }) => {
  const [value, setValue] = useState(space.name);

  return (
    <>
      <h3>Rename space</h3>
      <input value={value} onChange={(event) => setValue(event.target.value)} />
      <div className="modal-actions">
        <button className="button button-ghost" type="button" onClick={onCancel}>
          Cancel
        </button>
        <button
          className="button button-primary"
          type="button"
          onClick={() => {
            if (value.trim()) {
              onRename(value.trim());
            }
          }}
        >
          Save
        </button>
      </div>
    </>
  );
};

const SpaceDeleteForm: React.FC<{
  space: Space;
  onCancel: () => void;
  onDelete: () => Promise<void>;
}> = ({ space, onCancel, onDelete }) => {
  const [value, setValue] = useState("");
  const matches = value.trim() === space.name;

  return (
    <>
      <h3>Delete space</h3>
      <div className="muted">Type "{space.name}" to confirm deletion.</div>
      <input
        placeholder={space.name}
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
      <div className="modal-actions">
        <button className="button button-ghost" type="button" onClick={onCancel}>
          Cancel
        </button>
        <button
          className="button button-primary"
          type="button"
          disabled={!matches}
          onClick={() => {
            if (matches) {
              onDelete();
            }
          }}
        >
          Delete
        </button>
      </div>
    </>
  );
};

const rootElement = document.querySelector<HTMLDivElement>("#root");
const globalRoot = globalThis as typeof globalThis & { __LINKSTASH_ROOT__?: ReturnType<typeof createRoot> };

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
