import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";
import { authTables } from "@convex-dev/auth/server";

export default defineSchema({
  ...authTables,
  spaces: defineTable({
    userId: v.id("users"),
    name: v.string(),
    isDefault: v.boolean(),
    createdAt: v.number(),
    updatedAt: v.optional(v.number()),
  })
    .index("by_userId_and_isDefault", ["userId", "isDefault"])
    .index("by_userId_and_createdAt", ["userId", "createdAt"])
    .index("by_userId_and_name", ["userId", "name"]),
  links: defineTable({
    userId: v.id("users"),
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
    .index("by_userId_and_spaceId", ["userId", "spaceId"])
    .index("by_userId_and_spaceId_and_archived_and_createdAt", [
      "userId",
      "spaceId",
      "archived",
      "createdAt",
    ])
    .index("by_userId_and_spaceId_and_url", ["userId", "spaceId", "url"])
    .index("by_userId_and_createdAt", ["userId", "createdAt"]),
});
