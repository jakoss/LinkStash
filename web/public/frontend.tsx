import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
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
  archived: boolean;
  createdAt: number;
  spaceId: string;
};

type ModalState =
  | { kind: "none" }
  | { kind: "createSpace" }
  | { kind: "renameSpace"; space: Space }
  | { kind: "deleteSpace"; space: Space };

const apiFetch = async <T,>(path: string, options?: RequestInit) => {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
    },
    ...options,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => ({ error: "Request failed" }));
    throw new Error(body.error || "Request failed");
  }

  return (await response.json()) as T;
};

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

const App = () => {
  const [spaces, setSpaces] = useState<Space[]>([]);
  const [currentSpaceId, setCurrentSpaceId] = useState<string>("");
  const [archived, setArchived] = useState(false);
  const [links, setLinks] = useState<Link[]>([]);
  const [status, setStatus] = useState("");
  const [modal, setModal] = useState<ModalState>({ kind: "none" });
  const [urlInput, setUrlInput] = useState("");

  const currentSpace = useMemo(
    () => spaces.find((space) => space._id === currentSpaceId),
    [spaces, currentSpaceId],
  );

  const fetchSpaces = async () => {
    const { spaces: data } = await apiFetch<{ spaces: Space[] }>("/api/spaces");
    setSpaces(data);
    if (!currentSpaceId) {
      const defaultSpace = data.find((space) => space.isDefault) ?? data[0];
      if (defaultSpace) {
        setCurrentSpaceId(defaultSpace._id);
      }
    }
  };

  const fetchLinks = async (spaceId: string, isArchived: boolean) => {
    if (!spaceId) {
      setLinks([]);
      return;
    }
    const { links: data } = await apiFetch<{ links: Link[] }>(
      `/api/links?spaceId=${spaceId}&archived=${isArchived}`,
    );
    setLinks(data);
  };

  const refresh = async () => {
    try {
      setStatus("Syncing...");
      await fetchSpaces();
      if (currentSpaceId) {
        await fetchLinks(currentSpaceId, archived);
      }
      setStatus("");
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Something went wrong");
    }
  };

  useEffect(() => {
    apiFetch("/api/spaces/ensure-default", { method: "POST" })
      .then(refresh)
      .catch((error) => {
        setStatus(error instanceof Error ? error.message : "Something went wrong");
      });
  }, []);

  useEffect(() => {
    if (currentSpaceId) {
      fetchLinks(currentSpaceId, archived).catch((error) => {
        setStatus(error instanceof Error ? error.message : "Something went wrong");
      });
    }
  }, [currentSpaceId, archived]);

  const handleAddLink = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const url = urlInput.trim();
    if (!url || !currentSpaceId) {
      return;
    }
    await apiFetch("/api/links", {
      method: "POST",
      body: JSON.stringify({ url, spaceId: currentSpaceId }),
    });
    setUrlInput("");
    await fetchLinks(currentSpaceId, archived);
  };

  const handleArchiveToggle = async (link: Link) => {
    await apiFetch(`/api/links/${link._id}/archive`, {
      method: "PATCH",
      body: JSON.stringify({ archived: !link.archived }),
    });
    await fetchLinks(currentSpaceId, archived);
  };

  const handleMove = async (link: Link, spaceId: string) => {
    await apiFetch(`/api/links/${link._id}/move`, {
      method: "PATCH",
      body: JSON.stringify({ spaceId }),
    });
    await fetchLinks(currentSpaceId, archived);
  };

  const handleExport = async () => {
    if (!currentSpaceId) {
      return;
    }
    try {
      const { links: data } = await apiFetch<{ links: Link[] }>(
        `/api/links?spaceId=${currentSpaceId}&archived=false`,
      );
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
                            setStatus(error instanceof Error ? error.message : "Something went wrong");
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
                await apiFetch("/api/spaces", {
                  method: "POST",
                  body: JSON.stringify({ name }),
                });
                setModal({ kind: "none" });
                await refresh();
              }}
            />
          ) : null}
          {modal.kind === "renameSpace" ? (
            <SpaceRenameForm
              space={modal.space}
              onCancel={() => setModal({ kind: "none" })}
              onRename={async (name) => {
                await apiFetch(`/api/spaces/${modal.space._id}`, {
                  method: "PATCH",
                  body: JSON.stringify({ name }),
                });
                setModal({ kind: "none" });
                await refresh();
              }}
            />
          ) : null}
          {modal.kind === "deleteSpace" ? (
            <SpaceDeleteForm
              space={modal.space}
              onCancel={() => setModal({ kind: "none" })}
              onDelete={async () => {
                await apiFetch(`/api/spaces/${modal.space._id}`, { method: "DELETE" });
                setModal({ kind: "none" });
                setCurrentSpaceId("");
                await refresh();
              }}
            />
          ) : null}
        </Modal>
      ) : null}
    </div>
  );
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

if (rootElement) {
  createRoot(rootElement).render(<App />);
}
