import React, { useState } from "react";
import type { Space } from "../lib/types";

type SpaceCreateFormProps = {
  onCancel: () => void;
  onCreate: (name: string) => Promise<void>;
};

export const SpaceCreateForm: React.FC<SpaceCreateFormProps> = ({ onCancel, onCreate }) => {
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

type SpaceRenameFormProps = {
  space: Space;
  onCancel: () => void;
  onRename: (name: string) => Promise<void>;
};

export const SpaceRenameForm: React.FC<SpaceRenameFormProps> = ({ space, onCancel, onRename }) => {
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

type SpaceDeleteFormProps = {
  space: Space;
  onCancel: () => void;
  onDelete: () => Promise<void>;
};

export const SpaceDeleteForm: React.FC<SpaceDeleteFormProps> = ({ space, onCancel, onDelete }) => {
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
