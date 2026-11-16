import api, { PLAN_LIMIT_EVENT } from "./api";

/* =====================================================
   BILLING SERVICE
   GET  /billing/plans            public plan catalogue (limits + server prices)
   GET  /billing/me               current plan, PRO access period, usage per feature
   POST /billing/checkout/order   { billingCycle: "MONTHLY" | "ANNUAL" } → Razorpay order (public key id only)
   POST /billing/checkout/verify  Razorpay Checkout ids + signature → verified activation
   GET  /billing/history          your own purchases

   The backend decides every price, limit and plan change. Nothing here is trusted
   for authorization; usage shown in the UI is a convenience copy of /billing/me.
===================================================== */

/** Window event fired for every HTTP 429 PLAN_LIMIT_REACHED (dispatched in api.js). */
export { PLAN_LIMIT_EVENT };

export async function getPlans() {
  const { data } = await api.get("/billing/plans");
  return data;
}

export async function getSubscription() {
  const { data } = await api.get("/billing/me");
  return data;
}

export async function createCheckoutOrder(billingCycle) {
  const { data } = await api.post("/billing/checkout/order", { billingCycle });
  return data;
}

export async function verifyCheckoutPayment({ razorpayOrderId, razorpayPaymentId, razorpaySignature }) {
  const { data } = await api.post("/billing/checkout/verify", { razorpayOrderId, razorpayPaymentId, razorpaySignature });
  return data;
}

export async function getBillingHistory() {
  const { data } = await api.get("/billing/history");
  return data;
}

/** "₹299" from 29900 paise. */
export function formatPaise(paise, currency = "INR") {
  if (paise == null) return "";
  return new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 0 }).format(paise / 100);
}

/** "today" / "this month" for a usage period. */
export function periodPhrase(period) {
  return period === "MONTHLY" ? "this month" : "today";
}

/** "per day" / "per month". */
export function perPeriod(period) {
  return period === "MONTHLY" ? "/month" : "/day";
}

export function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

/** The PLAN_LIMIT_REACHED payload of an Axios error, or null. */
export function planLimitDetails(error) {
  const data = error?.response?.data;
  return error?.response?.status === 429 && data?.code === "PLAN_LIMIT_REACHED" ? data : null;
}
