import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FiCheck, FiStar, FiZap, FiInfo, FiLoader, FiAlertCircle, FiCheckCircle } from "react-icons/fi";

import { useAuth } from "../../context/auth";
import { useSubscription } from "../../context/subscription";
import PlanBadge from "../../components/common/PlanBadge";
import { formatDate, formatPaise, getPlans, perPeriod } from "../../services/billingService";
import { CheckoutCancelledError, CheckoutFailedError, startProCheckout } from "../../services/razorpayService";
import { toApiError } from "../../services/api";

/* =====================================================
   PRICING — FREE and PRO from GET /api/billing/plans (limits and prices are decided by the
   backend). PRO is bought with Razorpay Checkout and activated only after the backend has
   verified the payment. PRO is a paid access period (no automatic renewal).
===================================================== */

/** A PENDING payment is normally confirmed by Razorpay's webhook shortly after: re-check a few times. */
const PENDING_RECHECK_MS = [10_000, 30_000, 60_000];

const FEATURE_ORDER = [
  "AI_TEXT_INTERVIEW", "AI_VOICE_INTERVIEW", "AI_COACH_MESSAGE",
  "RESUME_ATS_ANALYSIS", "RESUME_JOB_MATCH", "RESUME_AI_IMPROVEMENT", "RESUME_INTERVIEW_PREP",
  "CODING_RUN", "CODING_SUBMIT", "SQL_RUN", "SQL_SUBMIT"
];

const ACCESS_LABELS = {
  PERFORMANCE_ANALYTICS: { BASIC: "Basic performance analytics", FULL: "Full performance analytics" },
  SKILL_GAP: { AVAILABLE: "Skill gap analysis", FULL: "Full skill gap analysis" },
  MY_PLAN: { AVAILABLE: "My Plan", FULL: "Full My Plan" }
};

function featureLines(plan) {
  const lines = FEATURE_ORDER
    .map((key) => plan.features[key])
    .filter(Boolean)
    .map((feature) => `${feature.limit} ${feature.label.toLowerCase()}${perPeriod(feature.period)}`);
  Object.entries(ACCESS_LABELS).forEach(([key, labels]) => {
    const level = plan.access[key];
    if (level && labels[level]) lines.push(labels[level]);
  });
  return lines;
}

function Pricing() {
  const { isRegistered, isGuest } = useAuth();
  const { subscription, isPro, displayPlan, refresh } = useSubscription();
  const [catalog, setCatalog] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [cycle, setCycle] = useState("MONTHLY");
  const [checkout, setCheckout] = useState({ state: "idle", message: "" }); // idle | working | verifying | success | pending | info | error

  const pending = checkout.state === "pending";
  useEffect(() => {
    if (!pending || isPro) return undefined;
    const timers = PENDING_RECHECK_MS.map((delay) => setTimeout(() => refresh(), delay));
    return () => timers.forEach(clearTimeout);
  }, [pending, isPro, refresh]);

  useEffect(() => {
    let cancelled = false;
    getPlans()
      .then((data) => !cancelled && setCatalog(data))
      .catch((error) => !cancelled && setLoadError(toApiError(error).message));
    return () => {
      cancelled = true;
    };
  }, []);

  const free = catalog?.plans.find((plan) => plan.plan === "FREE");
  const pro = catalog?.plans.find((plan) => plan.plan === "PRO");
  const monthly = pro?.pricesPaise.MONTHLY;
  const annual = pro?.pricesPaise.ANNUAL;
  const annualSaving = monthly && annual ? monthly * 12 - annual : 0;
  const paymentsAvailable = catalog?.paymentsAvailable;

  const buy = async () => {
    setCheckout({ state: "working", message: "Opening secure checkout…" });
    try {
      const result = await startProCheckout(cycle, {
        onVerifying: () => setCheckout({ state: "verifying", message: "Verifying your payment…" })
      });
      await refresh();
      if (result.result === "PENDING") {
        setCheckout({ state: "pending", message: result.message });
      } else {
        setCheckout({ state: "success", message: `PRO access until ${formatDate(result.accessUntil)}` });
      }
    } catch (error) {
      if (error instanceof CheckoutCancelledError) setCheckout({ state: "info", message: error.message });
      else if (error instanceof CheckoutFailedError) setCheckout({ state: "error", message: error.message });
      else setCheckout({ state: "error", message: error?.response ? toApiError(error).message : error.message });
    }
  };

  // Once the backend reports PRO, a pending payment has been confirmed.
  const status = pending && isPro ? { state: "success", message: `PRO access until ${formatDate(subscription?.expiresAt)}` } : checkout;
  const busy = status.state === "working" || status.state === "verifying";

  const proAction = () => {
    if (isGuest || !isRegistered) {
      return <Link to="/register" className="ua-button primary full">Create an account to upgrade</Link>;
    }
    if (!paymentsAvailable) {
      return (
        <>
          <button type="button" className="ua-button primary full" disabled>
            <FiZap aria-hidden="true" />
            Upgrade to PRO
          </button>
          <p className="pricing-note">Payments are temporarily unavailable.</p>
        </>
      );
    }
    return (
      <button type="button" className="ua-button primary full" onClick={buy} disabled={busy}>
        {busy ? <FiLoader className="resume-spin" aria-hidden="true" /> : <FiZap aria-hidden="true" />}
        {busy ? "Processing…" : isPro ? `Extend PRO (${cycle === "ANNUAL" ? "1 year" : "1 month"})` : "Upgrade to PRO"}
      </button>
    );
  };

  return (
    <div className="ua-page">

      <header className="ua-page-header">
        <div>
          <span className="ua-eyebrow">PLANS</span>
          <h1>Choose your CareerAI plan</h1>
          <p>Practise for free, or upgrade to PRO for much higher limits.</p>
        </div>
        <div className="pricing-current">
          <span>Current Plan</span>
          <PlanBadge plan={displayPlan} />
          {isPro && subscription?.expiresAt && <small>PRO access until {formatDate(subscription.expiresAt)}</small>}
        </div>
      </header>

      {status.state === "success" && (
        <div className="pricing-status success" role="status">
          <FiCheckCircle aria-hidden="true" />
          <div>
            <strong>Payment successful — CareerAI PRO activated</strong>
            <p>{status.message}</p>
          </div>
        </div>
      )}
      {["pending", "info", "error", "verifying", "working"].includes(status.state) && (
        <div className={`pricing-status ${status.state === "error" ? "error" : "info"}`} role={status.state === "error" ? "alert" : "status"}>
          {status.state === "error" ? <FiAlertCircle aria-hidden="true" /> : busy ? <FiLoader className="resume-spin" aria-hidden="true" /> : <FiInfo aria-hidden="true" />}
          <p>{status.message}</p>
        </div>
      )}
      {loadError && (
        <div className="pricing-status error" role="alert">
          <FiAlertCircle aria-hidden="true" />
          <p>{loadError}</p>
        </div>
      )}

      <div className="pricing-toggle" role="radiogroup" aria-label="Billing period">
        {["MONTHLY", "ANNUAL"].map((value) => (
          <button
            key={value}
            type="button"
            role="radio"
            aria-checked={cycle === value}
            className={cycle === value ? "active" : ""}
            onClick={() => setCycle(value)}
            disabled={busy}
          >
            {value === "MONTHLY" ? "Monthly" : "Annual"}
            {value === "ANNUAL" && annualSaving > 0 && <span>Save {formatPaise(annualSaving, catalog.currency)}</span>}
          </button>
        ))}
      </div>

      {catalog && (
        <section className="pricing-grid two">
          <article className={`pricing-card${!isPro ? " current" : ""}`}>
            <div className="pricing-card-head">
              <span className="pricing-icon" aria-hidden="true"><FiStar /></span>
              <div>
                <h2>Free</h2>
                {!isPro && isRegistered && <span className="ua-chip success">Current plan</span>}
              </div>
            </div>
            <p className="pricing-price">{formatPaise(0, catalog.currency)}</p>
            <p className="pricing-tagline">Everyday interview and practice preparation.</p>
            <ul className="pricing-feature-list">
              {featureLines(free).map((line) => (
                <li key={line}><FiCheck aria-hidden="true" />{line}</li>
              ))}
            </ul>
            <div className="pricing-action">
              {!isPro && isRegistered
                ? <button type="button" className="ua-button full" disabled><FiCheck aria-hidden="true" />Current Plan</button>
                : !isRegistered && <Link to="/register" className="ua-button full">Create free account</Link>}
            </div>
          </article>

          <article className={`pricing-card highlight${isPro ? " current" : ""}`}>
            <span className="pricing-ribbon">High limits</span>
            <div className="pricing-card-head">
              <span className="pricing-icon" aria-hidden="true"><FiZap /></span>
              <div>
                <h2>Pro</h2>
                {isPro && <span className="ua-chip success">Current plan</span>}
              </div>
            </div>
            <p className="pricing-price">
              {formatPaise(cycle === "ANNUAL" ? annual : monthly, catalog.currency)}
              <small>{cycle === "ANNUAL" ? "/year" : "/month"}</small>
            </p>
            <p className="pricing-tagline">
              {cycle === "ANNUAL"
                ? `One payment for 12 months of PRO access (${formatPaise(Math.round(annual / 12), catalog.currency)}/month).`
                : "One payment for 1 month of PRO access."}
              {" "}No automatic renewal.
            </p>
            <ul className="pricing-feature-list">
              {featureLines(pro).map((line) => (
                <li key={line}><FiCheck aria-hidden="true" />{line}</li>
              ))}
            </ul>
            <div className="pricing-action">{proAction()}</div>
          </article>
        </section>
      )}

      <p className="pricing-footnote">
        <FiInfo aria-hidden="true" />
        Payments are processed securely by Razorpay — CareerAI never sees your card details. Buying PRO while PRO is active
        adds the new period after your current access ends. Your interviews, resumes and practice history are kept if PRO ends.
      </p>

    </div>
  );
}

export default Pricing;
