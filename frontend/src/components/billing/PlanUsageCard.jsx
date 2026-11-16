import { Link } from "react-router-dom";
import { FiArrowRight, FiZap } from "react-icons/fi";
import PlanBadge from "../common/PlanBadge";
import UsageMeter from "./UsageMeter";
import { useSubscription } from "../../context/subscription";
import { formatDate } from "../../services/billingService";

const SUMMARY_FEATURES = ["AI_VOICE_INTERVIEW", "AI_TEXT_INTERVIEW", "CODING_RUN", "RESUME_ATS_ANALYSIS"];

/** Dashboard card: current plan, a few usage meters and Upgrade / View Plan. */
function PlanUsageCard() {
  const { subscription, isPro, loading } = useSubscription();

  return (
    <section className="dashboard-card usage-dashboard-card">
      <div className="dashboard-card-header">
        <div>
          <span>PLAN & USAGE</span>
          <h2>{isPro ? "PRO PLAN" : "FREE PLAN"}</h2>
        </div>
        <PlanBadge plan={isPro ? "PRO" : "FREE"} />
      </div>

      {isPro && subscription?.expiresAt && (
        <p className="plan-usage-note">PRO access until {formatDate(subscription.expiresAt)}</p>
      )}

      {loading && !subscription && <p className="plan-usage-note">Loading your usage…</p>}

      {subscription && (
        <div className="usage-meter-grid">
          {SUMMARY_FEATURES.map((feature) => (
            <UsageMeter key={feature} usage={subscription.features?.[feature]} />
          ))}
        </div>
      )}

      {isPro ? (
        <Link to="/settings/billing" className="dashboard-upgrade-link">
          View Plan
          <FiArrowRight />
        </Link>
      ) : (
        <Link to="/pricing" className="primary-button plan-upgrade-button">
          <FiZap />
          Upgrade to PRO
        </Link>
      )}
    </section>
  );
}

export default PlanUsageCard;
