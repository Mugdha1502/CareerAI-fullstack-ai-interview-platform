import { Link } from "react-router-dom";
import {
  FiAward,
  FiTrendingUp,
  FiAlertCircle,
  FiRefreshCw,
  FiBarChart2,
  FiArrowLeft,
  FiZap
} from "react-icons/fi";
import { percentage } from "./practiceUtils";

/**
 * result: {
 *   title, score, total, scoreLabel, stats[{ label, value }],
 *   strongArea, weakArea, breakdown[{ label, value }], note
 * }
 * xp: { amount, status: "pending" | "saved" | "already-earned" | "guest" | "none" }
 *   "pending" = shown only, not saved (regular practice until backend progress exists)
 */
function PracticeResult({ result, xp, retryLabel = "Practice Again", onRetry, extra }) {
  const accuracy = result.accuracy ?? percentage(result.score, result.total);

  return (
    <section
      className="practice-result"
      aria-live="polite"
    >

      <div className="practice-result-hero">

        <div className="practice-result-badge">
          <FiAward />
        </div>

        <span className="section-tag">
          {result.kicker || "SESSION SUMMARY"}
        </span>

        <h2>{result.title || "Practice Complete"}</h2>

        {result.note && <p>{result.note}</p>}

      </div>


      <div className="practice-result-stats">

        <div className="practice-result-stat">
          <span>{result.scoreLabel || "Score"}</span>
          <strong>
            {result.score} / {result.total}
          </strong>
        </div>

        <div className="practice-result-stat">
          <span>Accuracy</span>
          <strong>{accuracy}%</strong>
        </div>

        {(result.stats || []).map((stat) => (
          <div
            key={stat.label}
            className="practice-result-stat"
          >
            <span>{stat.label}</span>
            <strong>{stat.value}</strong>
          </div>
        ))}

        {xp && xp.status !== "none" && (
          <div className="practice-result-stat xp">
            <span>{xp.status === "pending" ? "XP Available" : "XP Earned"}</span>
            <strong>
              <FiZap />
              +{xp.amount} XP
            </strong>
            <small>
              {xp.status === "pending" && "XP rewards will be saved when CareerAI account progress is connected to the backend."}
              {xp.status === "saved" && "Added to your profile"}
              {xp.status === "already-earned" && "Already earned today — not added again"}
              {xp.status === "guest" && "Sign in to save XP to your profile"}
            </small>
          </div>
        )}

      </div>


      {(result.strongArea || result.weakArea) && (
        <div className="practice-result-areas">

          {result.strongArea && (
            <div className="practice-result-area strong">
              <FiTrendingUp />
              <div>
                <span>Strong Area</span>
                <strong>{result.strongArea}</strong>
              </div>
            </div>
          )}

          {result.weakArea && (
            <div className="practice-result-area weak">
              <FiAlertCircle />
              <div>
                <span>Needs Improvement</span>
                <strong>{result.weakArea}</strong>
              </div>
            </div>
          )}

        </div>
      )}


      {result.breakdown && result.breakdown.length > 0 && (
        <div className="practice-result-breakdown">

          <h3>Section breakdown</h3>

          {result.breakdown.map((item) => (
            <div
              key={item.label}
              className="practice-result-bar"
            >
              <div>
                <span>{item.label}</span>
                <strong>{item.value}%</strong>
              </div>
              <div className="practice-progress-track">
                <div style={{ width: `${item.value}%` }}></div>
              </div>
            </div>
          ))}

        </div>
      )}


      {extra}


      <div className="practice-result-actions">

        <button
          type="button"
          className="primary-button"
          onClick={onRetry}
        >
          <FiRefreshCw />
          {retryLabel}
        </button>

        <Link
          to="/performance"
          className="secondary-button"
        >
          <FiBarChart2 />
          View Performance
        </Link>

        <Link
          to="/practice"
          className="secondary-button"
        >
          <FiArrowLeft />
          Back to Practice
        </Link>

      </div>

    </section>
  );
}

export default PracticeResult;
