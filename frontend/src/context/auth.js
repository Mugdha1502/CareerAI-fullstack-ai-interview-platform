import { createContext, useContext } from "react";

/* Auth state provided by AuthProvider (AuthContext.jsx). Kept in a plain module so the provider
   file only exports components, which React Fast Refresh needs. */

export const AuthContext = createContext(null);

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider.");
  }

  return context;
}
