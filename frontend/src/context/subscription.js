import { createContext, useContext } from "react";

/* Plan + usage for the signed-in user (from GET /api/billing/me). UI convenience only:
   every limit is enforced by the backend. */

export const SubscriptionContext = createContext(null);

/** Re-check on tab focus at most this often. */
export const FOCUS_REFRESH_MS = 60_000;
/** Longest single expiry timer (browsers overflow above ~24.8 days); it re-arms without an API call. */
export const MAX_EXPIRY_TIMER_MS = 6 * 60 * 60 * 1000;
/** Server still says PRO although this device's clock is past expiresAt: trust it, re-check later. */
export const CLOCK_SKEW_RETRY_MS = 5 * 60 * 1000;

const EMPTY = {
  subscription: null,
  loading: false,
  error: "",
  plan: "FREE",
  displayPlan: "FREE",
  isPro: false,
  refresh: () => Promise.resolve(null),
  featureUsage: () => null,
  canUseFeature: () => true
};

export function useSubscription() {
  return useContext(SubscriptionContext) || EMPTY;
}
