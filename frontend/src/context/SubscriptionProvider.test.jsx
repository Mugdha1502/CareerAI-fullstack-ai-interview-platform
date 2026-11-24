import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import SubscriptionProvider from "./SubscriptionProvider";
import { CLOCK_SKEW_RETRY_MS, FOCUS_REFRESH_MS, MAX_EXPIRY_TIMER_MS, useSubscription } from "./subscription";
import PlanBadge from "../components/common/PlanBadge";
import { getSubscription } from "../services/billingService";

/* Plan labels must come only from GET /api/billing/me (mocked here — no network). */

const auth = { user: null, isRegistered: false };

vi.mock("./auth", () => ({ useAuth: () => auth }));
vi.mock("../services/billingService", async (importOriginal) => ({
  ...(await importOriginal()),
  getSubscription: vi.fn()
}));

const NOW = new Date("2026-11-23T10:00:00Z").getTime();
const pro = (expiresAt) => ({ plan: "PRO", status: "ACTIVE", expiresAt: new Date(expiresAt).toISOString(), features: {} });
const free = () => ({ plan: "FREE", status: "NONE", expiresAt: null, features: {} });

function Probe() {
  const { displayPlan, isPro } = useSubscription();
  return (
    <div>
      <PlanBadge plan={displayPlan} />
      <span data-testid="is-pro">{String(isPro)}</span>
    </div>
  );
}

function signIn(id, storedPlan = "FREE") {
  auth.user = { id, plan: storedPlan, accountType: "REGISTERED" };
  auth.isRegistered = true;
}

function signOut() {
  auth.user = { id: "guest_1", plan: "GUEST", accountType: "GUEST" };
  auth.isRegistered = false;
}

const app = () => (
  <SubscriptionProvider>
    <Probe />
  </SubscriptionProvider>
);

/** Lets pending promises and React updates settle; advances fake time when asked. */
async function flush(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

const badge = () => document.querySelector(".plan-badge");
const isPro = () => screen.getByTestId("is-pro").textContent;

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  getSubscription.mockReset();
  signOut();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("plan label source", () => {
  it("ignores the plan saved at login and shows the backend plan", async () => {
    signIn(1, "PRO"); // stale users.plan from login
    getSubscription.mockResolvedValue(free());
    render(app());
    expect(badge().textContent).toBe("…"); // loading: no guess
    expect(badge().getAttribute("aria-busy")).toBe("true");
    await flush();
    expect(badge().textContent).toBe("FREE");
    expect(isPro()).toBe("false");
  });

  it("shows PRO only when the backend says PRO", async () => {
    signIn(1, "FREE");
    getSubscription.mockResolvedValue(pro(NOW + 86_400_000));
    render(app());
    await flush();
    expect(badge().textContent).toBe("PRO");
    expect(isPro()).toBe("true");
  });

  it("guests are GUEST even if localStorage-style data says PRO, and nothing is fetched", async () => {
    auth.user = { id: "guest_x", plan: "PRO", accountType: "GUEST" };
    render(app());
    await flush();
    expect(badge().textContent).toBe("GUEST");
    expect(getSubscription).not.toHaveBeenCalled();
  });
});

describe("unavailable subscription API never promotes to PRO", () => {
  it("network/server error on first load → FREE", async () => {
    signIn(1, "PRO");
    getSubscription.mockRejectedValue(new Error("down"));
    render(app());
    await flush();
    expect(badge().textContent).toBe("FREE");
    expect(isPro()).toBe("false");
  });

  it.each([
    ["an HTML page", "<!doctype html><html></html>"],
    ["an empty body", ""],
    ["null", null],
    ["a body without plan", { status: "ACTIVE" }],
    ["a lowercase plan", { plan: "pro", expiresAt: new Date(NOW + 86_400_000).toISOString() }]
  ])("malformed 200 response (%s) → FREE", async (_label, body) => {
    signIn(1, "PRO");
    getSubscription.mockResolvedValue(body);
    render(app());
    await flush();
    expect(badge().textContent).toBe("FREE");
    expect(isPro()).toBe("false");
  });
});

describe("expiry without logout", () => {
  it("switches to FREE when the PRO period ends and re-checks exactly once", async () => {
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 5_000)).mockResolvedValueOnce(free());
    render(app());
    await flush();
    expect(badge().textContent).toBe("PRO");
    await flush(4_000);
    expect(badge().textContent).toBe("PRO");
    await flush(3_000);
    expect(badge().textContent).toBe("FREE");
    expect(getSubscription).toHaveBeenCalledTimes(2);
    await flush(60 * 60 * 1000); // no further polling
    expect(getSubscription).toHaveBeenCalledTimes(2);
  });

  it("still shows FREE at expiry when the re-check fails (offline)", async () => {
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 5_000)).mockRejectedValueOnce(new Error("offline"));
    render(app());
    await flush();
    await flush(7_000);
    expect(badge().textContent).toBe("FREE");
    expect(isPro()).toBe("false");
  });

  it("keeps the last verified PRO through a failed refresh, then expires on time", async () => {
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 10 * 60_000)).mockRejectedValue(new Error("down"));
    render(app());
    await flush();
    await flush(FOCUS_REFRESH_MS + 1);
    await act(async () => window.dispatchEvent(new Event("focus")));
    await flush();
    expect(getSubscription).toHaveBeenCalledTimes(2);
    expect(badge().textContent).toBe("PRO");
    await flush(10 * 60_000);
    expect(badge().textContent).toBe("FREE");
  });

  it("long periods use one capped timer that re-arms without API calls", async () => {
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 30 * 86_400_000)).mockResolvedValueOnce(free());
    render(app());
    await flush();
    expect(vi.getTimerCount()).toBe(1);
    await flush(MAX_EXPIRY_TIMER_MS * 3);
    expect(vi.getTimerCount()).toBe(1);
    expect(getSubscription).toHaveBeenCalledTimes(1);
    expect(badge().textContent).toBe("PRO");
    // Real time passes in steps: each capped timer re-arms after React re-renders.
    for (let elapsed = MAX_EXPIRY_TIMER_MS * 3; elapsed <= 30 * 86_400_000 + MAX_EXPIRY_TIMER_MS; elapsed += MAX_EXPIRY_TIMER_MS) {
      await flush(MAX_EXPIRY_TIMER_MS);
    }
    expect(badge().textContent).toBe("FREE");
    expect(getSubscription).toHaveBeenCalledTimes(2); // 120+ re-arms, still only the one expiry re-check
    expect(vi.getTimerCount()).toBe(0);
  });

  it("trusts the server when this device's clock is already past expiresAt (no request loop)", async () => {
    signIn(1);
    getSubscription.mockResolvedValue(pro(NOW - 60_000)); // device clock ahead of the server
    render(app());
    await flush();
    expect(badge().textContent).toBe("PRO");
    await flush(CLOCK_SKEW_RETRY_MS - 1_000);
    expect(getSubscription).toHaveBeenCalledTimes(1);
    await flush(1_000);
    expect(getSubscription).toHaveBeenCalledTimes(2); // at most one re-check per 5 minutes
  });

  it("clears its timer on unmount and on sign-out", async () => {
    signIn(1);
    getSubscription.mockResolvedValue(pro(NOW + 86_400_000));
    const { rerender, unmount } = render(app());
    await flush();
    expect(vi.getTimerCount()).toBe(1);
    signOut();
    rerender(app());
    await flush();
    expect(vi.getTimerCount()).toBe(0);
    expect(badge().textContent).toBe("GUEST");
    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe("refresh triggers", () => {
  it("tab focus refreshes at most once per minute", async () => {
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 86_400_000)).mockResolvedValue(free());
    render(app());
    await flush();
    await act(async () => window.dispatchEvent(new Event("focus")));
    await flush();
    expect(getSubscription).toHaveBeenCalledTimes(1); // throttled
    await flush(FOCUS_REFRESH_MS);
    await act(async () => {
      window.dispatchEvent(new Event("focus"));
      document.dispatchEvent(new Event("visibilitychange"));
    });
    await flush();
    expect(getSubscription).toHaveBeenCalledTimes(2); // focus + visibility together = one request
    expect(badge().textContent).toBe("FREE"); // e.g. expired or changed elsewhere
  });

  it("a PLAN_LIMIT_REACHED event refreshes the plan", async () => {
    const { PLAN_LIMIT_EVENT } = await import("../services/billingService");
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 86_400_000)).mockResolvedValueOnce(free());
    render(app());
    await flush();
    await act(async () => window.dispatchEvent(new CustomEvent(PLAN_LIMIT_EVENT)));
    await flush();
    expect(getSubscription).toHaveBeenCalledTimes(2);
    expect(badge().textContent).toBe("FREE");
  });

  it("sign-out drops PRO at once; signing in again never shows the previous session's plan", async () => {
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 86_400_000));
    const { rerender } = render(app());
    await flush();
    expect(badge().textContent).toBe("PRO");

    signOut();
    rerender(app());
    expect(badge().textContent).toBe("GUEST");

    // Same account logs in again after its PRO ended elsewhere: loading, then the fresh FREE.
    let resolve;
    getSubscription.mockReturnValueOnce(new Promise((r) => { resolve = r; }));
    signIn(1);
    rerender(app());
    expect(badge().textContent).toBe("…");
    await act(async () => resolve(free()));
    expect(badge().textContent).toBe("FREE");
  });

  it("another account never sees the previous account's PRO", async () => {
    signIn(1);
    getSubscription.mockResolvedValueOnce(pro(NOW + 86_400_000));
    const { rerender } = render(app());
    await flush();
    let resolve;
    getSubscription.mockReturnValueOnce(new Promise((r) => { resolve = r; }));
    signIn(2);
    rerender(app());
    expect(badge().textContent).toBe("…");
    await act(async () => resolve(free()));
    expect(badge().textContent).toBe("FREE");
  });
});

describe("every plan label uses the backend subscription", () => {
  const files = [
    "../pages/dashboard/Dashboard.jsx",
    "../pages/profile/Profile.jsx",
    "../pages/settings/Settings.jsx",
    "../pages/settings/Billing.jsx",
    "../pages/subscription/Pricing.jsx",
    "../layouts/UserLayout.jsx",
    "../components/billing/PlanUsageCard.jsx"
  ];

  it.each(files)("%s renders <PlanBadge plan={displayPlan} /> and never reads user.plan", (file) => {
    const source = readFileSync(fileURLToPath(new URL(file, import.meta.url)), "utf8");
    expect(source).not.toMatch(/user\??\.plan/);
    const badges = source.match(/<PlanBadge[\s\S]*?\/>/g) || [];
    expect(badges.length).toBeGreaterThan(0);
    for (const tag of badges) expect(tag.replace(/\s+/g, " ")).toMatch(/plan=\{displayPlan\}/);
  });
});
