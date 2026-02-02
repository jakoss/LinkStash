import React from "react";
import { useConvexAuth } from "convex/react";
import { useAuthToken } from "@convex-dev/auth/react";
import { SignIn } from "../components/Auth/SignIn";
import { AuthedApp } from "./AuthedApp";

export const App: React.FC = () => {
  const { isAuthenticated, isLoading } = useConvexAuth();
  const authToken = useAuthToken();

  if (isLoading) {
    return <div className="status">Loading...</div>;
  }

  if (!isAuthenticated || !authToken) {
    return <SignIn />;
  }

  return <AuthedApp />;
};
