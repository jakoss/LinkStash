export type Space = {
  _id: string;
  name: string;
  isDefault: boolean;
};

export type Link = {
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

export type ModalState =
  | { kind: "none" }
  | { kind: "createSpace" }
  | { kind: "renameSpace"; space: Space }
  | { kind: "deleteSpace"; space: Space };
