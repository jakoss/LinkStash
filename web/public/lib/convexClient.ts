import { ConvexReactClient } from "convex/react";

const getConvexUrl = () => {
  return process.env.CONVEX_URL ?? "";
};

const convexUrl = getConvexUrl();
const convex = convexUrl ? new ConvexReactClient(convexUrl) : null;

export { convex, convexUrl, getConvexUrl };
