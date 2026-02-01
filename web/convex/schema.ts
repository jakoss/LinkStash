import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

export default defineSchema({
  spaces: defineTable({
    name: v.string(),
    isDefault: v.boolean(),
    createdAt: v.number(),
    updatedAt: v.optional(v.number()),
  })
    .index("by_isDefault", ["isDefault"])
    .index("by_createdAt", ["createdAt"])
    .index("by_name", ["name"]),
  links: defineTable({
    url: v.string(),
    title: v.optional(v.string()),
    description: v.optional(v.string()),
    imageUrl: v.optional(v.string()),
    siteName: v.optional(v.string()),
    faviconUrl: v.optional(v.string()),
    spaceId: v.id("spaces"),
    archived: v.boolean(),
    createdAt: v.number(),
    updatedAt: v.optional(v.number()),
  })
    .index("by_spaceId", ["spaceId"])
    .index("by_spaceId_and_archived_and_createdAt", ["spaceId", "archived", "createdAt"])
    .index("by_spaceId_and_url", ["spaceId", "url"])
    .index("by_createdAt", ["createdAt"]),
});
