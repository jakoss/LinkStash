import React, { useEffect, useMemo, useState } from "react";
import { useConvex, useMutation, useQuery } from "convex/react";
import { useAuthActions } from "@convex-dev/auth/react";
import { api } from "../../convex/_generated/api";
import { Modal } from "../components/Modal";
import { SpaceCreateForm, SpaceDeleteForm, SpaceRenameForm } from "../components/SpaceForms";
import type { Link, ModalState, Space } from "../lib/types";

export const AuthedApp: React.FC = () => {
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
