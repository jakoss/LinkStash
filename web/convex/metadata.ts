"use node";

import { action, internalAction } from "./_generated/server";
import { v } from "convex/values";
import { internal } from "./_generated/api";

const metadataValidator = v.object({
  title: v.optional(v.string()),
  description: v.optional(v.string()),
  imageUrl: v.optional(v.string()),
  siteName: v.optional(v.string()),
  faviconUrl: v.optional(v.string()),
});

const pickMeta = (html: string, key: string) => {
  const patterns = [
    new RegExp(`<meta[^>]+property=["']${key}["'][^>]+content=["']([^"']+)["'][^>]*>`, "i"),
    new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+property=["']${key}["'][^>]*>`, "i"),
    new RegExp(`<meta[^>]+name=["']${key}["'][^>]+content=["']([^"']+)["'][^>]*>`, "i"),
    new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+name=["']${key}["'][^>]*>`, "i"),
  ];

  for (const pattern of patterns) {
    const match = html.match(pattern);
    if (match?.[1]) {
      return match[1].trim();
    }
  }

  return undefined;
};

const pickTitle = (html: string) => {
  const match = html.match(/<title[^>]*>([^<]+)<\/title>/i);
  return match?.[1]?.trim();
};

const pickFavicon = (html: string) => {
  const match = html.match(
    /<link[^>]+rel=["'](?:shortcut icon|icon)["'][^>]+href=["']([^"']+)["'][^>]*>/i,
  );
  return match?.[1]?.trim();
};

const resolveUrl = (base: string, value?: string) => {
  if (!value) {
    return undefined;
  }
  try {
    return new URL(value, base).toString();
  } catch {
    return value;
  }
};

export const fetchOpenGraph = action({
  args: {
    url: v.string(),
  },
  returns: metadataValidator,
  handler: async (_ctx, args) => {
    const response = await fetch(args.url, {
      headers: {
        "User-Agent": "LinkStash/1.0",
        Accept: "text/html,application/xhtml+xml",
      },
    });

    if (!response.ok) {
      return {
        title: undefined,
        description: undefined,
        imageUrl: undefined,
        siteName: undefined,
        faviconUrl: undefined,
      };
    }

    const html = await response.text();

    const title = pickMeta(html, "og:title") ?? pickTitle(html);
    const description = pickMeta(html, "og:description") ?? pickMeta(html, "description");
    const imageUrl = resolveUrl(args.url, pickMeta(html, "og:image"));
    const siteName = pickMeta(html, "og:site_name");
    const faviconUrl = resolveUrl(args.url, pickFavicon(html));

    return {
      title,
      description,
      imageUrl,
      siteName,
      faviconUrl,
    };
  },
});

export const fetchAndStoreMetadata = internalAction({
  args: {
    linkId: v.id("links"),
    url: v.string(),
  },
  returns: v.null(),
  handler: async (ctx, args) => {
    const response = await fetch(args.url, {
      headers: {
        "User-Agent": "LinkStash/1.0",
        Accept: "text/html,application/xhtml+xml",
      },
    });

    if (!response.ok) {
      return null;
    }

    const html = await response.text();

    const title = pickMeta(html, "og:title") ?? pickTitle(html);
    const description = pickMeta(html, "og:description") ?? pickMeta(html, "description");
    const imageUrl = resolveUrl(args.url, pickMeta(html, "og:image"));
    const siteName = pickMeta(html, "og:site_name");
    const faviconUrl = resolveUrl(args.url, pickFavicon(html));

    await ctx.runMutation(internal.links.updateLinkMetadata, {
      linkId: args.linkId,
      title,
      description,
      imageUrl,
      siteName,
      faviconUrl,
    });

    return null;
  },
});
