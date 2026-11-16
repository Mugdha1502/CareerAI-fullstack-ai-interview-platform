import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FiX, FiZap } from "react-icons/fi";
import { PLAN_LIMIT_EVENT, periodPhrase } from "../../services/billingService";

/**
 * Global plan-limit dialog (mounted once in App). Opens whenever any module receives
 * HTTP 429 PLAN_LIMIT_REACHED; all numbers come from the backend response.
 */
function UsageLimitModal() {
  const [limit, setLimit] = useState(null);

  useEffect(() => {
    const onLimit = (event) => setLimit(event.detail || null);
    window.addEventListener(PLAN_LIMIT_EVENT, onLimit);
    return () => window.removeEventListener(PLAN_LIMIT_EVENT, onLimit);
  }, []);

  useEffect(() => {
    if (!limit) return undefined;
    const onKey = (event) => {
      if (event.key === "Escape") setLimit(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [limit]);

  if (!limit) return null;

  const daily = limit.period !== "MONTHLY";
  const what = (limit.featureLabel || "uses").toLowerCase();
  const close = () => setLimit(null);

  return (
    <div className="limit-modal-overlay" role="presentation" onClick={close}>
      <div className="limit-modal" role="dialog" aria-modal="true" aria-labelledby="limit-modal-title" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="limit-modal-close" onClick={close} aria-label="Close">
          <FiX />
        </button>

        <div className="limit-modal-icon">
          <FiZap />
        </div>

        <h2 id="limit-modal-title">{daily ? "Daily limit reached" : "Monthly limit reached"}</h2>

        <p>
          You&apos;ve used all {limit.limit} {limit.plan} {what} {periodPhrase(limit.period)}.
        </p>

        {limit.plan !== "PRO" && limit.proLimit ? (
          <>
            <p>
              Upgrade to PRO for up to {limit.proLimit} {what} per {daily ? "day" : "month"}.
            </p>
            <Link to="/pricing" className="primary-button" onClick={close}>
              <FiZap />
              Upgrade to PRO
            </Link>
          </>
        ) : (
          <p>Your allowance resets {daily ? "tomorrow" : "next month"}.</p>
        )}
      </div>
    </div>
  );
}

export default UsageLimitModal;
