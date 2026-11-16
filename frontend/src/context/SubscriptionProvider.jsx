import { useCallback, useEffect, useMemo, useState } from "react";
import { useAuth } from "./AuthContext";
import { SubscriptionContext } from "./subscription";
import { PLAN_LIMIT_EVENT, getSubscription } from "../services/billingService";
import { toApiError } from "../services/api";

/**
 * Loads the signed-in user's plan and usage once (GET /api/billing/me) and refreshes it after a
 * payment or a PLAN_LIMIT_REACHED response. Guests have no backend plan (FREE presentation only).
 */
function SubscriptionProvider({ children }) {
  const { user, isRegistered } = useAuth();
  const accountKey = isRegistered ? user?.id ?? "registered" : null;
  // Results are stored with the account they belong to, so another account never sees them.
  const [state, setState] = useState({ key: null, subscription: null, error: "" });
  const [refreshing, setRefreshing] = useState(false);

  const refresh = useCallback(async () => {
    if (accountKey === null) return null;
    setRefreshing(true);
    try {
      const data = await getSubscription();
      setState({ key: accountKey, subscription: data, error: "" });
      return data;
    } catch (err) {
      setState((current) => ({ ...current, key: accountKey, error: toApiError(err).message }));
      return null;
    } finally {
      setRefreshing(false);
    }
  }, [accountKey]);

  useEffect(() => {
    if (accountKey === null) return undefined;
    let cancelled = false;
    getSubscription()
      .then((data) => {
        if (!cancelled) setState({ key: accountKey, subscription: data, error: "" });
      })
      .catch((err) => {
        if (!cancelled) setState({ key: accountKey, subscription: null, error: toApiError(err).message });
      });
    return () => {
      cancelled = true;
    };
  }, [accountKey]);

  // A limit response means our usage copy is stale.
  useEffect(() => {
    const onLimit = () => refresh();
    window.addEventListener(PLAN_LIMIT_EVENT, onLimit);
    return () => window.removeEventListener(PLAN_LIMIT_EVENT, onLimit);
  }, [refresh]);

  const value = useMemo(() => {
    const current = accountKey !== null && state.key === accountKey ? state : null;
    const subscription = current?.subscription || null;
    const plan = subscription?.plan || "FREE";
    return {
      subscription,
      loading: refreshing || (accountKey !== null && !current),
      error: current?.error || "",
      plan,
      isPro: plan === "PRO",
      refresh,
      featureUsage: (feature) => subscription?.features?.[feature] || null,
      // Convenience only: the backend always decides.
      canUseFeature: (feature) => {
        const usage = subscription?.features?.[feature];
        return !usage || usage.remaining > 0;
      }
    };
  }, [accountKey, state, refreshing, refresh]);

  return <SubscriptionContext.Provider value={value}>{children}</SubscriptionContext.Provider>;
}

export default SubscriptionProvider;
