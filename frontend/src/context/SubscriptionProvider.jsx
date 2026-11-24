import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "./auth";
import { CLOCK_SKEW_RETRY_MS, FOCUS_REFRESH_MS, MAX_EXPIRY_TIMER_MS, SubscriptionContext } from "./subscription";
import { PLAN_LIMIT_EVENT, getSubscription } from "../services/billingService";
import { toApiError } from "../services/api";

const EMPTY_STATE = { key: null, subscription: null, error: "" };

/** Only a well-formed /billing/me body counts; anything else is treated as unavailable (never PRO). */
function validated(data) {
  if (!data || typeof data !== "object" || typeof data.plan !== "string") {
    throw new Error("Your plan could not be loaded.");
  }
  return data;
}

/**
 * The signed-in user's plan and usage from GET /api/billing/me — the ONLY source for plan labels.
 * The plan stored at login (users.plan / localStorage) is never used for display or access.
 *
 * Loaded on sign-in and page load; refreshed after a payment (callers use refresh()), after a
 * PLAN_LIMIT_REACHED response, when the tab regains focus (throttled), and when the current PRO
 * period ends (one timer, cleaned up on change). The backend always enforces the real limits.
 */
function SubscriptionProvider({ children }) {
  const { user, isRegistered } = useAuth();
  const accountKey = isRegistered ? user?.id ?? "registered" : null;
  // Results are stored with the account they belong to, so another account never sees them.
  const [state, setState] = useState(EMPTY_STATE);
  const [refreshing, setRefreshing] = useState(false);
  // The subscription object whose PRO period ended on this device (cleared by the next response).
  const [expiredFor, setExpiredFor] = useState(null);
  const [rearm, setRearm] = useState(0);
  const lastFetchRef = useRef(0);

  // Signing out (or switching account) drops the previous session's plan immediately.
  const [seenKey, setSeenKey] = useState(accountKey);
  if (seenKey !== accountKey) {
    setSeenKey(accountKey);
    setState(EMPTY_STATE);
    setExpiredFor(null);
  }

  const refresh = useCallback(async () => {
    if (accountKey === null) return null;
    lastFetchRef.current = Date.now();
    setRefreshing(true);
    try {
      const data = validated(await getSubscription());
      setState({ key: accountKey, subscription: data, error: "" });
      return data;
    } catch (err) {
      // Keep this account's last verified copy (its expiry timer still applies); never borrow another's.
      setState((current) => current.key === accountKey
        ? { ...current, error: toApiError(err).message }
        : { key: accountKey, subscription: null, error: toApiError(err).message });
      return null;
    } finally {
      setRefreshing(false);
    }
  }, [accountKey]);

  useEffect(() => {
    if (accountKey === null) return undefined;
    let cancelled = false;
    lastFetchRef.current = Date.now();
    getSubscription()
      .then((data) => {
        if (!cancelled) setState({ key: accountKey, subscription: validated(data), error: "" });
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

  // Returning to the tab picks up changes made elsewhere (webhook activation, expiry, another tab).
  useEffect(() => {
    if (accountKey === null) return undefined;
    const onVisible = () => {
      if (document.visibilityState === "hidden") return;
      if (Date.now() - lastFetchRef.current < FOCUS_REFRESH_MS) return;
      refresh();
    };
    window.addEventListener("focus", onVisible);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      window.removeEventListener("focus", onVisible);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [accountKey, refresh]);

  const current = accountKey !== null && state.key === accountKey ? state : null;
  const subscription = current?.subscription || null;
  const proUntil = subscription?.plan === "PRO" ? subscription.expiresAt : null;

  // One timer for the end of the current PRO access: show FREE at that moment and re-check once.
  useEffect(() => {
    if (!proUntil || expiredFor === subscription) return undefined;
    const end = Date.parse(proUntil);
    if (Number.isNaN(end)) return undefined;
    const remaining = end - Date.now();
    if (remaining <= 0) {
      const retry = setTimeout(() => refresh(), CLOCK_SKEW_RETRY_MS);
      return () => clearTimeout(retry);
    }
    const timer = setTimeout(() => {
      if (Date.now() >= end) {
        setExpiredFor(subscription);
        refresh();
      } else {
        setRearm((n) => n + 1); // long period: wait again, no API call
      }
    }, Math.min(remaining + 1000, MAX_EXPIRY_TIMER_MS));
    return () => clearTimeout(timer);
  }, [proUntil, subscription, expiredFor, refresh, rearm]);

  const value = useMemo(() => {
    const isPro = subscription?.plan === "PRO" && expiredFor !== subscription;
    let displayPlan;
    if (accountKey === null) displayPlan = "GUEST";
    else if (subscription) displayPlan = isPro ? "PRO" : "FREE";
    else if (current?.error) displayPlan = "FREE"; // unavailable: never promote
    else displayPlan = null; // still loading: show no plan rather than a guess
    return {
      subscription,
      loading: refreshing || (accountKey !== null && !current),
      error: current?.error || "",
      plan: isPro ? "PRO" : "FREE",
      displayPlan,
      isPro,
      refresh,
      featureUsage: (feature) => subscription?.features?.[feature] || null,
      // Convenience only: the backend always decides.
      canUseFeature: (feature) => {
        const usage = subscription?.features?.[feature];
        return !usage || usage.remaining > 0;
      }
    };
  }, [accountKey, current, subscription, expiredFor, refreshing, refresh]);

  return <SubscriptionContext.Provider value={value}>{children}</SubscriptionContext.Provider>;
}

export default SubscriptionProvider;
