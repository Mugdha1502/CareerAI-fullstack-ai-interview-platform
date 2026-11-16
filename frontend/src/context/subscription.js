import { createContext, useContext } from "react";

/* Plan + usage for the signed-in user (from GET /api/billing/me). UI convenience only:
   every limit is enforced by the backend. */

export const SubscriptionContext = createContext(null);

const EMPTY = {
  subscription: null,
  loading: false,
  error: "",
  plan: "FREE",
  isPro: false,
  refresh: () => Promise.resolve(null),
  featureUsage: () => null,
  canUseFeature: () => true
};

export function useSubscription() {
  return useContext(SubscriptionContext) || EMPTY;
}
