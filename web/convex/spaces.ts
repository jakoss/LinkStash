import { mutation, query } from "./_generated/server";
import { v } from "convex/values";
import { getAuthUserId } from "@convex-dev/auth/server";

const defaultSpaceName = "Inbox";

const spaceValidator = v.object({
  _id: v.id("spaces"),
  _creationTime: v.number(),
  userId: v.id("users"),
  name: v.string(),
  isDefault: v.boolean(),
  createdAt: v.number(),
  updatedAt: v.optional(v.number()),
});

export const listSpaces = query({
  args: {},
  returns: v.array(spaceValidator),
  handler: async (ctx) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    return ctx.db
      .query("spaces")
      .withIndex("by_userId_and_createdAt", (q) => q.eq("userId", userId))
      .collect();
  },
});

export const getDefaultSpace = query({
  args: {},
  returns: v.union(spaceValidator, v.null()),
  handler: async (ctx) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const space = await ctx.db
      .query("spaces")
      .withIndex("by_userId_and_isDefault", (q) =>
        q.eq("userId", userId).eq("isDefault", true),
      )
      .first();
    return space ?? null;
  },
});

export const ensureDefaultSpace = mutation({
  args: {},
  returns: spaceValidator,
  handler: async (ctx) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const existing = await ctx.db
      .query("spaces")
      .withIndex("by_userId_and_isDefault", (q) =>
        q.eq("userId", userId).eq("isDefault", true),
      )
      .first();

    if (existing) {
      return existing;
    }

    const now = Date.now();
    const id = await ctx.db.insert("spaces", {
      userId,
      name: defaultSpaceName,
      isDefault: true,
      createdAt: now,
      updatedAt: now,
    });

    const created = await ctx.db.get("spaces", id);
    if (!created) {
      throw new Error("Failed to create default space.");
    }
    return created;
  },
});

export const createSpace = mutation({
  args: {
    name: v.string(),
  },
  returns: v.id("spaces"),
  handler: async (ctx, args) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const now = Date.now();
    return ctx.db.insert("spaces", {
      userId,
      name: args.name,
      isDefault: false,
      createdAt: now,
      updatedAt: now,
    });
  },
});

export const renameSpace = mutation({
  args: {
    spaceId: v.id("spaces"),
    name: v.string(),
  },
  returns: v.null(),
  handler: async (ctx, args) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const space = await ctx.db.get(args.spaceId);
    if (!space || space.userId !== userId) {
      throw new Error("Space not found");
    }
    await ctx.db.patch(args.spaceId, {
      name: args.name,
      updatedAt: Date.now(),
    });
    return null;
  },
});

export const deleteSpace = mutation({
  args: {
    spaceId: v.id("spaces"),
  },
  returns: v.null(),
  handler: async (ctx, args) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const space = await ctx.db.get(args.spaceId);
    if (!space || space.userId !== userId) {
      return null;
    }

    if (space.isDefault) {
      throw new Error("Cannot delete the default space.");
    }

    const links = await ctx.db
      .query("links")
      .withIndex("by_userId_and_spaceId", (q) =>
        q.eq("userId", userId).eq("spaceId", args.spaceId),
      )
      .collect();

    await Promise.all(links.map((link) => ctx.db.delete(link._id)));
    await ctx.db.delete(args.spaceId);
    return null;
  },
});
