import { periodPhrase } from "../../services/billingService";

/** "Voice interviews · 1 of 2 used today" with a bar. usage comes from GET /api/billing/me. */
function UsageMeter({ label, usage, compact = false }) {
  if (!usage) return null;
  const percent = usage.limit > 0 ? Math.min((usage.used / usage.limit) * 100, 100) : 100;
  const tone = usage.remaining === 0 ? " full" : percent >= 80 ? " high" : "";

  return (
    <div className={`usage-meter${compact ? " compact" : ""}${tone}`}>
      <div className="usage-meter-head">
        <span>{label || usage.label}</span>
        <strong>{usage.used} / {usage.limit}</strong>
      </div>
      <span
        className="usage-meter-bar"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={usage.limit}
        aria-valuenow={usage.used}
        aria-label={`${label || usage.label}: ${usage.used} of ${usage.limit} used ${periodPhrase(usage.period)}`}
      >
        <span style={{ width: `${percent}%` }}></span>
      </span>
      {!compact && (
        <small>
          {usage.remaining === 0
            ? `All ${usage.limit} used ${periodPhrase(usage.period)}`
            : `${usage.remaining} left ${periodPhrase(usage.period)}`}
        </small>
      )}
    </div>
  );
}

export default UsageMeter;
