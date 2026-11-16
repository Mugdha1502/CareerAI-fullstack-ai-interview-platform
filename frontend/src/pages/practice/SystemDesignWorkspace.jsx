import { useState } from "react";
import {
  FiLayers,
  FiCheckCircle,
  FiAlertCircle,
  FiXCircle,
  FiHelpCircle,
  FiArrowRight,
  FiArrowLeft,
  FiClipboard
} from "react-icons/fi";
import { getSystemDesignChallenge, reviewPracticeDesign } from "./practiceProvider";
import { systemDesignSections } from "./practiceMockData";
import { PracticeSourceNotice } from "./PracticeSourceNotice";

/** Maps a 0-100 AI rubric score onto the existing covered/partial/missing styles. */
function aiStatus(score) {
  if (score >= 70) return { status: "covered", statusLabel: `${score} / 100` };
  if (score >= 40) return { status: "partial", statusLabel: `${score} / 100` };
  return { status: "missing", statusLabel: `${score} / 100` };
}

const statusIcon = {
  covered: <FiCheckCircle />,
  partial: <FiAlertCircle />,
  missing: <FiXCircle />
};

function SystemDesignWorkspace({ config, onComplete }) {
  const [challenge] = useState(() => getSystemDesignChallenge(config.topic));
  const [activeId, setActiveId] = useState(systemDesignSections[0].id);
  const [answers, setAnswers] = useState({});
  const [showHint, setShowHint] = useState(false);
  const [review, setReview] = useState(null);
  const [reviewing, setReviewing] = useState(false);
  const [reviewError, setReviewError] = useState("");

  const activeIndex = systemDesignSections.findIndex((section) => section.id === activeId);
  const section = systemDesignSections[activeIndex];
  const wordsIn = (id) => (answers[id] || "").trim().split(/\s+/).filter(Boolean).length;

  const goTo = (id) => {
    setActiveId(id);
    setShowHint(false);
  };

  // AI rubric review when available; demo completeness rubric otherwise (labelled).
  const handleReview = async () => {
    setReviewing(true);
    setReviewError("");
    setReview(null);

    const result = await reviewPracticeDesign(config, systemDesignSections, answers);

    setReviewing(false);
    if (result.source === "invalid") {
      setReviewError(result.notice);
      return;
    }
    setReview(result);
  };

  const isAiReview = review?.source === "ai";
  const rubricRows = !review
    ? []
    : isAiReview
      ? review.rubric.map((item) => ({ id: item.criterion, label: item.label, feedback: item.feedback, ...aiStatus(item.score) }))
      : review.rubric;

  const handleFinish = () => {
    const covered = rubricRows.filter((item) => item.status === "covered");
    const weak = rubricRows.find((item) => item.status === "missing") ||
      rubricRows.find((item) => item.status === "partial");

    onComplete({
      title: "Practice Complete",
      scoreLabel: isAiReview ? "Design score" : "Sections covered",
      score: isAiReview ? review.overallScore : review.score,
      total: isAiReview ? 100 : review.total,
      strongArea: covered[0]?.label || "Keep practising",
      weakArea: weak?.label || "None in this session",
      note: isAiReview
        ? "Rubric scored by the CareerAI AI reviewer. Use it as guidance, not a final grade."
        : "Demo rubric based on section completeness because AI design review was unavailable.",
      xpActivity: null
    });
  };

  return (
    <div className="sd-workspace">

      <div className="sd-brief">

        <div className="sd-brief-icon">
          <FiLayers />
        </div>

        <div>
          <span className="quiz-runner-heading">
            System Design • {config.difficulty}
          </span>
          <h2>Design a {config.topic}</h2>
          <p>{challenge.summary}</p>
          <span className="sd-scale">{challenge.scale}</span>
        </div>

      </div>


      <div className="sd-layout">

        <nav
          className="sd-sections"
          aria-label="Design sections"
        >
          {systemDesignSections.map((item, index) => {
            const words = wordsIn(item.id);

            return (
              <button
                type="button"
                key={item.id}
                className={`sd-section-link${item.id === activeId ? " active" : ""}${words >= 25 ? " done" : words > 0 ? " started" : ""}`}
                aria-current={item.id === activeId ? "step" : undefined}
                onClick={() => goTo(item.id)}
              >
                <span className="sd-section-number">{index + 1}</span>
                <span>{item.label}</span>
                {words > 0 && <small>{words}w</small>}
              </button>
            );
          })}
        </nav>


        <section className="sd-editor">

          <div className="sd-editor-head">
            <div>
              <h3>{section.label}</h3>
              <p>{section.guide}</p>
            </div>

            <button
              type="button"
              className="sql-hint-toggle"
              aria-expanded={showHint}
              onClick={() => setShowHint((value) => !value)}
            >
              <FiHelpCircle />
              {showHint ? "Hide hint" : "Show hint"}
            </button>
          </div>

          {showHint && (
            <p className="sd-hint">{challenge.hints[section.id]}</p>
          )}

          <textarea
            aria-label={`${section.label} answer`}
            className="practice-textarea sd-textarea"
            rows={12}
            placeholder={`Describe the ${section.label.toLowerCase()} for your ${config.topic}...`}
            value={answers[section.id] || ""}
            onChange={(event) => {
              setAnswers((current) => ({ ...current, [section.id]: event.target.value }));
              setReview(null);
            }}
          />

          <div className="sd-editor-actions">

            <button
              type="button"
              className="secondary-button"
              onClick={() => goTo(systemDesignSections[activeIndex - 1].id)}
              disabled={activeIndex === 0}
            >
              <FiArrowLeft />
              Previous
            </button>

            {activeIndex < systemDesignSections.length - 1 ? (
              <button
                type="button"
                className="secondary-button"
                onClick={() => goTo(systemDesignSections[activeIndex + 1].id)}
              >
                Next section
                <FiArrowRight />
              </button>
            ) : (
              <span></span>
            )}

            <button
              type="button"
              className="primary-button"
              onClick={handleReview}
              disabled={reviewing || Object.values(answers).every((value) => !value.trim())}
            >
              <FiClipboard />
              {reviewing ? "Reviewing..." : "Review My Design"}
            </button>

          </div>

          {reviewError && (
            <p className="practice-inline-note" role="alert">
              <FiAlertCircle />
              {reviewError}
            </p>
          )}

        </section>

      </div>


      {review && (
        <section
          className="sd-review"
          aria-live="polite"
        >

          <div className="sd-review-head">
            <div>
              <span className="practice-demo-label">
                {isAiReview ? "AI review" : "Demo review · section completeness"}
              </span>
              <h3>Design rubric</h3>
            </div>
            <strong>
              {isAiReview
                ? `${review.overallScore} / 100 overall`
                : `${review.score} / ${review.total} sections covered`}
            </strong>
          </div>

          <PracticeSourceNotice source={review.source} notice={review.notice} />

          <ul className="sd-rubric">
            {rubricRows.map((item) => (
              <li
                key={item.id}
                className={item.status}
              >
                {statusIcon[item.status]}
                <span>
                  {item.label}
                  {item.feedback && <small className="sd-rubric-feedback">{item.feedback}</small>}
                </span>
                <strong>{item.statusLabel}</strong>
              </li>
            ))}
          </ul>

          {isAiReview && (
            <div className="sd-ai-summary">
              <p>{review.summary}</p>

              {review.strengths.length > 0 && (
                <>
                  <h4>Strengths</h4>
                  <ul>
                    {review.strengths.map((item) => <li key={item}>{item}</li>)}
                  </ul>
                </>
              )}

              <h4>Recommendations</h4>
              <ul>
                {review.recommendations.map((item) => <li key={item}>{item}</li>)}
              </ul>
            </div>
          )}

          {!isAiReview && (
            <p className="sd-review-note">
              This demo checks how completely each section is written, not the quality of the design.
            </p>
          )}

          <div className="quiz-actions">
            <button
              type="button"
              className="primary-button"
              onClick={handleFinish}
            >
              Finish Practice
              <FiArrowRight />
            </button>
          </div>

        </section>
      )}

    </div>
  );
}

export default SystemDesignWorkspace;
