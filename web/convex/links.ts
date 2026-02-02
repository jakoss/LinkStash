import { internalMutation, mutation, query } from "./_generated/server";
import { v } from "convex/values";
import { internal } from "./_generated/api";
import { getAuthUserId } from "@convex-dev/auth/server";

const metadataFields = {
  title: v.optional(v.string()),
  description: v.optional(v.string()),
  imageUrl: v.optional(v.string()),
  siteName: v.optional(v.string()),
  faviconUrl: v.optional(v.string()),
};

const linkValidator = v.object({
  _id: v.id("links"),
  _creationTime: v.number(),
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
});

const normalizeUrl = (rawUrl: string) => rawUrl.trim();

export const listLinks = query({
  args: {
    spaceId: v.id("spaces"),
    archived: v.boolean(),
  },
  returns: v.array(linkValidator),
  handler: async (ctx, args) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    return ctx.db
      .query("links")
      .withIndex("by_userId_and_spaceId_and_archived_and_createdAt", (q) =>
        q.eq("userId", userId).eq("spaceId", args.spaceId).eq("archived", args.archived),
      )
      .order("desc")
      .collect();
  },
});

export const addLink = mutation({
  args: {
    spaceId: v.id("spaces"),
    url: v.string(),
    dedupe: v.optional(v.boolean()),
    ...metadataFields,
  },
  returns: v.id("links"),
  handler: async (ctx, args) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const url = normalizeUrl(args.url);

    const space = await ctx.db.get(args.spaceId);
    if (!space || space.userId !== userId) {
      throw new Error("Space not found");
    }

    if (args.dedupe) {
      const existing = await ctx.db
        .query("links")
        .withIndex("by_userId_and_spaceId_and_url", (q) =>
          q.eq("userId", userId).eq("spaceId", args.spaceId).eq("url", url),
        )
        .first();

      if (existing) {
        return existing._id;
      }
    }

    const now = Date.now();
    const linkId = await ctx.db.insert("links", {
      userId,
      spaceId: args.spaceId,
      url,
      title: args.title,
      description: args.description,
      imageUrl: args.imageUrl,
      siteName: args.siteName,
      faviconUrl: args.faviconUrl,
      archived: false,
      createdAt: now,
      updatedAt: now,
    });

    await ctx.scheduler.runAfter(0, internal.metadata.fetchAndStoreMetadata, {
      linkId,
      url,
    });

    return linkId;
  },
});

export const moveLink = mutation({
  args: {
    linkId: v.id("links"),
    spaceId: v.id("spaces"),
  },
  returns: v.null(),
  handler: async (ctx, args) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const link = await ctx.db.get(args.linkId);
    if (!link || link.userId !== userId) {
      throw new Error("Link not found");
    }
    const space = await ctx.db.get(args.spaceId);
    if (!space || space.userId !== userId) {
      throw new Error("Space not found");
    }
    await ctx.db.patch(args.linkId, {
      spaceId: args.spaceId,
      updatedAt: Date.now(),
    });
    return null;
  },
});

export const setLinkArchived = mutation({
  args: {
    linkId: v.id("links"),
    archived: v.boolean(),
  },
  returns: v.null(),
  handler: async (ctx, args) => {
    const userId = await getAuthUserId(ctx);
    if (!userId) {
      throw new Error("Not authenticated");
    }
    const link = await ctx.db.get(args.linkId);
    if (!link || link.userId !== userId) {
      throw new Error("Link not found");
    }
    await ctx.db.patch(args.linkId, {
      archived: args.archived,
      updatedAt: Date.now(),
    });
    return null;
  },
});

export const updateLinkMetadata = internalMutation({
  args: {
    linkId: v.id("links"),
    ...metadataFields,
  },
  returns: v.null(),
  handler: async (ctx, args) => {
    const link = await ctx.db.get(args.linkId);
    if (!link) {
      return null;
    }
    await ctx.db.patch(args.linkId, {
      title: args.title,
      description: args.description,
      imageUrl: args.imageUrl,
      siteName: args.siteName,
      faviconUrl: args.faviconUrl,
      updatedAt: Date.now(),
    });
    return null;
  },
});
