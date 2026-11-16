import { createCheckoutOrder, verifyCheckoutPayment } from "./billingService";

/* =====================================================
   RAZORPAY CHECKOUT (browser side)
   1. the backend creates the order and decides the amount (we only send MONTHLY / ANNUAL)
   2. Razorpay Checkout collects the payment (CareerAI never sees card details)
   3. the payment ids + signature go to the backend, which verifies them and activates PRO
   A Checkout "success" callback alone never changes the plan. Only the public key id
   (returned by the order endpoint) is used here; the key secret never reaches the browser.
===================================================== */

const CHECKOUT_SRC = "https://checkout.razorpay.com/v1/checkout.js";
export const CHECKOUT_LOAD_ERROR = "Payment service could not be loaded.";

export class CheckoutCancelledError extends Error {
  constructor() {
    super("Checkout was closed. You have not been charged.");
    this.name = "CheckoutCancelledError";
  }
}

export class CheckoutFailedError extends Error {
  constructor(message) {
    super(message || "The payment failed. You have not been charged.");
    this.name = "CheckoutFailedError";
  }
}

let loader = null;

/** Loads checkout.js once (a single <script>), only when a checkout starts. */
export function loadRazorpayCheckout(doc = typeof document !== "undefined" ? document : null) {
  if (typeof window !== "undefined" && window.Razorpay) return Promise.resolve(window.Razorpay);
  if (!doc) return Promise.reject(new Error(CHECKOUT_LOAD_ERROR));
  if (!loader) {
    loader = new Promise((resolve, reject) => {
      const existing = doc.querySelector(`script[src="${CHECKOUT_SRC}"]`);
      const script = existing || doc.createElement("script");
      const fail = () => {
        loader = null; // allow a later retry
        reject(new Error(CHECKOUT_LOAD_ERROR));
      };
      script.addEventListener("load", () => (window.Razorpay ? resolve(window.Razorpay) : fail()));
      script.addEventListener("error", fail);
      if (!existing) {
        script.src = CHECKOUT_SRC;
        script.async = true;
        doc.body.appendChild(script);
      }
    });
  }
  return loader;
}

/** Opens Checkout for a backend order; resolves with Razorpay's handler response. */
export function openCheckout(Razorpay, order) {
  return new Promise((resolve, reject) => {
    let lastFailure = null;
    const checkout = new Razorpay({
      key: order.keyId,
      amount: order.amount,
      currency: order.currency,
      name: "CareerAI",
      description: order.description,
      order_id: order.razorpayOrderId,
      prefill: { name: order.user?.name || "", email: order.user?.email || "" },
      theme: { color: "#6c63ff" },
      handler: (response) => resolve(response),
      modal: {
        // A failed attempt keeps Checkout open for a retry; only closing ends the flow.
        ondismiss: () => reject(lastFailure ? new CheckoutFailedError(lastFailure) : new CheckoutCancelledError())
      }
    });
    checkout.on("payment.failed", (response) => {
      lastFailure = response?.error?.description || "The payment failed.";
    });
    checkout.open();
  });
}

/**
 * Full PRO purchase: backend order → Checkout → backend verification.
 * Resolves with the verification result ({ result, accessUntil, subscription, ... }).
 */
export async function startProCheckout(billingCycle, { onVerifying } = {}) {
  const order = await createCheckoutOrder(billingCycle);
  const Razorpay = await loadRazorpayCheckout();
  const payment = await openCheckout(Razorpay, order);
  onVerifying?.();
  return verifyCheckoutPayment({
    razorpayOrderId: payment.razorpay_order_id,
    razorpayPaymentId: payment.razorpay_payment_id,
    razorpaySignature: payment.razorpay_signature
  });
}
