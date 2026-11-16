import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FiZap, FiAlertCircle, FiLoader, FiCreditCard } from "react-icons/fi";

import PlanBadge from "../../components/common/PlanBadge";
import UsageMeter from "../../components/billing/UsageMeter";
import { useSubscription } from "../../context/subscription";
import { formatDate, formatPaise, getBillingHistory } from "../../services/billingService";
import { toApiError } from "../../services/api";

/* =====================================================
   BILLING — current plan, PRO access period, usage and payment history (all from the backend).
===================================================== */

const STATUS_LABELS = {
  ACTIVE: "Paid",
  EXPIRED: "Paid · ended",
  PENDING: "Not completed",
  FAILED: "Failed",
  CANCELLED: "Cancelled"
};

function Billing() {
  const { subscription, isPro, loading, error } = useSubscription();
  const [history, setHistory] = useState(null);
  const [historyError, setHistoryError] = useState("");

  useEffect(() => {
    let cancelled = false;
    getBillingHistory()
      .then((data) => !cancelled && setHistory(data))
      .catch((err) => !cancelled && setHistoryError(toApiError(err).message));
    return () => {
      cancelled = true;
    };
  }, [subscription?.expiresAt]);

  const features = subscription ? Object.entries(subscription.features) : [];

  return (
    <div className="ua-page">

      <header className="ua-page-header">
        <div>
          <span className="ua-eyebrow">BILLING</span>
          <h1>Plan and billing</h1>
          <p>Your current plan, usage and payments.</p>
        </div>
      </header>

      <section className="ua-card billing-current">
        <div className="billing-current-main">
          <span className="ua-card-kicker">CURRENT PLAN</span>
          <h2><PlanBadge plan={isPro ? "PRO" : "FREE"} /> {isPro ? "CareerAI PRO" : "CareerAI Free"}</h2>
          <p>
            {isPro && subscription?.expiresAt
              ? `PRO access until ${formatDate(subscription.expiresAt)}`
              : "Free plan — upgrade any time for higher limits."}
          </p>
        </div>
        <Link to="/pricing" className="ua-button primary">
          <FiZap aria-hidden="true" />
          {isPro ? "Extend PRO" : "Upgrade to PRO"}
        </Link>
      </section>

      {error && <p className="pricing-status error" role="alert"><FiAlertCircle aria-hidden="true" /> {error}</p>}

      <section className="ua-card">
        <div className="ua-card-header">
          <div>
            <span className="ua-card-kicker">USAGE</span>
            <h2>Your {isPro ? "PRO" : "Free"} limits</h2>
          </div>
        </div>
        {loading && !subscription && <p><FiLoader className="resume-spin" aria-hidden="true" /> Loading usage…</p>}
        <div className="usage-meter-grid">
          {features.map(([key, usage]) => <UsageMeter key={key} usage={usage} />)}
        </div>
      </section>

      <section className="ua-card">
        <div className="ua-card-header">
          <div>
            <span className="ua-card-kicker">PAYMENT HISTORY</span>
            <h2>Purchases</h2>
          </div>
        </div>
        {historyError && <p className="pricing-status error" role="alert">{historyError}</p>}
        {history && history.length === 0 && (
          <p className="billing-empty"><FiCreditCard aria-hidden="true" /> No payments yet.</p>
        )}
        {history && history.length > 0 && (
          <div className="practice-table-wrap">
            <table className="billing-table">
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Plan</th>
                  <th scope="col">Amount</th>
                  <th scope="col">Status</th>
                  <th scope="col">Access</th>
                  <th scope="col">Payment</th>
                </tr>
              </thead>
              <tbody>
                {history.map((item) => (
                  <tr key={item.id}>
                    <td>{formatDate(item.date)}</td>
                    <td>{item.plan} · {item.billingCycle === "ANNUAL" ? "Annual" : "Monthly"}</td>
                    <td>{formatPaise(item.amountPaise, item.currency)}</td>
                    <td><span className={`billing-status ${item.status.toLowerCase()}`}>{STATUS_LABELS[item.status] || item.status}</span></td>
                    <td>{item.accessFrom ? `${formatDate(item.accessFrom)} – ${formatDate(item.accessUntil)}` : "—"}</td>
                    <td>{item.paymentId || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

    </div>
  );
}

export default Billing;
