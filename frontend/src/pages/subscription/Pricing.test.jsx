import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Pricing from "./Pricing";
import SubscriptionProvider from "../../context/SubscriptionProvider";
import { getPlans, getSubscription } from "../../services/billingService";
import { startProCheckout } from "../../services/razorpayService";

/* Pricing refreshes the backend plan after checkout — Razorpay and the API are mocked (no network). */

const auth = { user: { id: 7, plan: "FREE", accountType: "REGISTERED" }, isRegistered: true, isGuest: false };

vi.mock("../../context/AuthContext", () => ({ useAuth: () => auth }));
vi.mock("../../services/billingService", async (importOriginal) => ({
  ...(await importOriginal()),
  getSubscription: vi.fn(),
  getPlans: vi.fn()
}));
vi.mock("../../services/razorpayService", async (importOriginal) => ({
  ...(await importOriginal()),
  startProCheckout: vi.fn()
}));

const NOW = new Date("2026-11-23T10:00:00Z").getTime();
const plan = (name) => ({ plan: name, features: {}, access: {}, pricesPaise: name === "PRO" ? { MONTHLY: 29900, ANNUAL: 249900 } : {} });
const catalog = { plans: [plan("FREE"), plan("PRO")], paymentsAvailable: true, currency: "INR" };
const free = { plan: "FREE", status: "NONE", expiresAt: null, features: {} };
const pro = { plan: "PRO", status: "ACTIVE", expiresAt: new Date(NOW + 30 * 86_400_000).toISOString(), features: {} };

async function flush(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

const currentBadge = () => document.querySelector(".pricing-current .plan-badge").textContent;

function renderPricing() {
  return render(
    <MemoryRouter>
      <SubscriptionProvider>
        <Pricing />
      </SubscriptionProvider>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
  getPlans.mockResolvedValue(catalog);
  getSubscription.mockReset();
  startProCheckout.mockReset();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("Pricing plan refresh", () => {
  it("verified payment refreshes the plan to PRO immediately", async () => {
    getSubscription.mockResolvedValueOnce(free).mockResolvedValueOnce(pro);
    startProCheckout.mockResolvedValue({ result: "ACTIVATED", accessUntil: pro.expiresAt });
    renderPricing();
    await flush();
    expect(currentBadge()).toBe("FREE");
    await act(async () => fireEvent.click(screen.getByRole("button", { name: /Upgrade to PRO/ })));
    await flush();
    expect(currentBadge()).toBe("PRO");
    expect(getSubscription).toHaveBeenCalledTimes(2);
  });

  it("a PENDING payment is re-checked a few times and shows PRO once the webhook confirms it", async () => {
    getSubscription.mockResolvedValueOnce(free).mockResolvedValueOnce(free).mockResolvedValueOnce(pro);
    startProCheckout.mockResolvedValue({ result: "PENDING", message: "Payment received — confirming…" });
    renderPricing();
    await flush();
    await act(async () => fireEvent.click(screen.getByRole("button", { name: /Upgrade to PRO/ })));
    await flush();
    expect(screen.getByText("Payment received — confirming…")).toBeTruthy();
    expect(currentBadge()).toBe("FREE");
    await flush(10_000); // webhook confirmed meanwhile
    expect(currentBadge()).toBe("PRO");
    expect(screen.queryByText("Payment received — confirming…")).toBeNull();
    expect(screen.getByText(/PRO access until/, { selector: ".pricing-status p, p" })).toBeTruthy();
    await flush(120_000); // re-checks stop once PRO
    expect(getSubscription).toHaveBeenCalledTimes(3);
  });

  it("pending re-checks are bounded and cleaned up on unmount", async () => {
    getSubscription.mockResolvedValue(free);
    startProCheckout.mockResolvedValue({ result: "PENDING", message: "Pending" });
    const { unmount } = renderPricing();
    await flush();
    await act(async () => fireEvent.click(screen.getByRole("button", { name: /Upgrade to PRO/ })));
    await flush();
    await flush(10 * 60_000);
    expect(getSubscription).toHaveBeenCalledTimes(5); // load + after checkout + 3 re-checks
    expect(currentBadge()).toBe("FREE");

    getSubscription.mockClear();
    unmount();
    await flush(10 * 60_000);
    expect(getSubscription).not.toHaveBeenCalled();
  });
});
