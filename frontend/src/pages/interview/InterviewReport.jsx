import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  FiAward,
  FiCheckCircle,
  FiAlertCircle,
  FiActivity,
  FiCode,
  FiRefreshCw,
  FiBarChart2,
  FiArrowRight,
  FiTarget,
  FiBookOpen,
  FiLoader,
  FiCalendar
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import { getInterviewReport } from "../../services/interviewService";
import { useAuth } from "../../context/auth";

const TYPE_LABELS = {
  TECHNICAL: "Technical",
  HR: "HR",
  BEHAVIORAL: "Behavioral",
  RESUME: "Resume-Based",
  JOB_SPECIFIC: "Job-Specific",
  MIXED: "Mixed"
};

const READINESS_LABELS = {
  READY: "Interview-ready for this role",
  DEVELOPING: "Developing — a little more practice needed",
  NOT_YET: "Not yet ready — focus on the weak areas below"
};

// Setup page source/category for "Retry Interview".
const RETRY_STATE = {
  TECHNICAL: { source: "Role-Based", category: "Technical" },
  HR: { source: "Role-Based", category: "HR" },
  BEHAVIORAL: { source: "Role-Based", category: "Behavioural" },
  MIXED: { source: "Role-Based", category: "Mixed" },
  RESUME: { source: "Resume-Based", category: "Technical" },
  JOB_SPECIFIC: { source: "Job-Based", category: "Technical" }
};

function getStatus(score) {
  if (score >= 85) return { label: "Strong", tone: "strong" };
  if (score >= 70) return { label: "Good", tone: "good" };
  return { label: "Needs improvement", tone: "weak" };
}

function title(text) {
  return text ? text.charAt(0) + text.slice(1).toLowerCase() : "";
}

function InterviewReport() {
  const [searchParams] = useSearchParams();
  const { isAuthenticated } = useAuth();
  const id = Number(searchParams.get("id")) || null;

  const [report, setReport] = useState(null);
  const [status, setStatus] = useState(id && isAuthenticated ? "loading" : "missing");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!id || !isAuthenticated) return undefined;
    let cancelled = false;

    getInterviewReport(id)
      .then((data) => {
        if (cancelled) return;
        setReport(data);
        setStatus("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(toApiError(err).message);
        setStatus("error");
      });

    return () => {
      cancelled = true;
    };
  }, [id, isAuthenticated]);

  if (status !== "ready") {
    return (
      <div className="ai-flow-page">
        <section className="ai-flow-body">
          <div className="section-container ai-flow-session-message" role={status === "loading" ? "status" : "alert"}>
            {status === "loading" ? <FiLoader className="resume-spin" /> : <FiAlertCircle />}
            <h1>{status === "loading" ? "Loading your report…" : "Report not available"}</h1>
            <p>
              {status === "loading" ? "Fetching your interview results."
                : status === "error" ? error
                  : "Complete an interview to see its report."}
            </p>
            {status !== "loading" && (
              <Link to="/ai-interview/setup" className="ai-primary-button">
                Start an Interview
                <FiArrowRight />
              </Link>
            )}
          </div>
        </section>
      </div>
    );
  }

  const overall = getStatus(report.overallScore);
  const primaryWeak = report.weakAreas[0];
  const weakRoute = report.recommendedSkills.find((skill) => skill.practiceRoute)?.practiceRoute || "/career/skill-gap";

  const dimensions = [
    { name: "Overall", score: report.overallScore, note: "Average of your answer scores" },
    report.technicalScore != null && { name: "Technical", score: report.technicalScore, note: "Technical questions only" },
    report.writtenCommunicationScore != null && {
      name: "Written communication",
      score: report.writtenCommunicationScore,
      note: "Clarity of typed answers (not voice)"
    }
  ].filter(Boolean);

  return (
    <div className="ai-flow-page">

      {/* HEADER */}

      <section className="ai-flow-report-hero">

        <div className="section-container ai-flow-report-hero-grid">

          <div>

            <ol className="ai-flow-stepper dark" aria-label="Interview progress">
              <li className="done">Setup</li>
              <li className="done">Device Check</li>
              <li className="done">Interview</li>
              <li className="active">Report</li>
            </ol>

            <span className="ai-dark-tag">
              {report.source === "AI" ? "INTERVIEW REPORT · AI-ASSISTED" : "INTERVIEW REPORT"}
            </span>

            <h1>Interview Complete</h1>

            <p>
              {report.targetRole} · {TYPE_LABELS[report.type] || report.type} · {report.mode === "VOICE" ? "Voice" : "Text"} · {title(report.difficulty)}
              {report.resumeTitle ? ` · ${report.resumeTitle}` : ""} · {report.answeredCount} of {report.questionCount} questions answered
            </p>

          </div>


          <div className="ai-flow-score-card">

            <div
              className="ai-flow-score-ring"
              role="img"
              aria-label={`Overall score ${report.overallScore} out of 100`}
            >
              <strong>{report.overallScore}</strong>
              <span>/ 100</span>
            </div>

            <div className="ai-flow-score-text">
              <span>OVERALL SCORE</span>
              <strong>
                <FiAward />
                {overall.label} performance
              </strong>
              <small>{READINESS_LABELS[report.roleReadiness]}</small>
            </div>

          </div>

        </div>

      </section>


      <section className="ai-flow-body">

        <div className="section-container">

          {report.mode === "VOICE" && (
            <p className="ai-flow-session-notice" role="note">
              <FiAlertCircle />
              Voice interview: answers were evaluated on the transcript content only. Voice, tone, pace and confidence are not scored.
            </p>
          )}

          {report.notice && (
            <p className="ai-flow-session-notice" role="status">
              <FiAlertCircle />
              {report.notice}
            </p>
          )}

          {/* SCORES (only dimensions that were actually measured) */}

          <div className="ai-flow-category-grid">

            {dimensions.map((item) => (
              <article
                key={item.name}
                className="ai-flow-category-card"
              >

                <div className="ai-flow-category-head">
                  <span>{item.name}</span>
                  <strong>{item.score}%</strong>
                </div>

                <div
                  className="ai-flow-progress-track"
                  role="progressbar"
                  aria-label={`${item.name} score`}
                  aria-valuenow={item.score}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <div style={{ width: `${item.score}%` }}></div>
                </div>

                <small className="ai-flow-category-note">{item.note}</small>

              </article>
            ))}

          </div>


          {/* SUMMARY + FOCUS AREA */}

          <div className="ai-flow-report-split">

            <article className="ai-flow-report-panel">

              <h2>Overall Feedback</h2>

              <p>{report.summary}</p>

            </article>


            <article className="ai-flow-weak-card">

              <div className="ai-flow-weak-icon">
                <FiActivity />
              </div>

              <span>PRIMARY IMPROVEMENT AREA</span>

              <strong>{primaryWeak || "No weak area found"}</strong>

              <p>
                {primaryWeak
                  ? "Practise this before your next interview, then retry to track your progress."
                  : "Every answered question scored well. Try a harder difficulty next."}
              </p>

              <Link
                to={weakRoute}
                className="ai-primary-button"
              >
                Practice Weak Skills
                <FiArrowRight />
              </Link>

            </article>

          </div>


          {/* STRENGTHS / IMPROVEMENTS */}

          <div className="ai-flow-report-split equal">

            <article className="ai-flow-report-panel positive">

              <h2>
                <FiCheckCircle />
                Strengths
              </h2>

              {report.strongAreas.length > 0 ? (
                <ul className="ai-flow-feedback-list">
                  {report.strongAreas.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : (
                <p>No topic scored 75% or more yet.</p>
              )}

            </article>


            <article className="ai-flow-report-panel negative">

              <h2>
                <FiAlertCircle />
                Areas to Improve
              </h2>

              {report.weakAreas.length > 0 ? (
                <ul className="ai-flow-feedback-list">
                  {report.weakAreas.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : (
                <p>No topic scored below 60%.</p>
              )}

            </article>

          </div>


          {/* QUESTION BY QUESTION */}

          <section className="ai-flow-report-section">

            <h2>Question-by-Question Review</h2>

            <div className="ai-flow-question-list">

              {report.questionResults.map((item) => {
                const itemStatus = getStatus(item.score);

                return (
                  <article
                    key={item.sequenceNumber}
                    className={`ai-flow-question-item ${itemStatus.tone}`}
                  >

                    <div className="ai-flow-question-head">

                      <span className="ai-flow-question-number">
                        Question {String(item.sequenceNumber).padStart(2, "0")}
                        {item.topic ? ` · ${item.topic}` : ""}
                      </span>

                      <span className={`ai-flow-question-status ${itemStatus.tone}`}>
                        {itemStatus.label}
                      </span>

                      <span className="ai-flow-question-score">
                        {item.score}%
                      </span>

                    </div>

                    <h3 className="ai-flow-question-text">
                      {item.question}
                    </h3>

                    <div className="ai-flow-question-details">

                      <div>
                        <span>Your answer</span>
                        <p>{item.answer}</p>
                      </div>

                      <div>
                        <span>{item.source === "AI" ? "Feedback" : "Feedback (key-point scoring)"}</span>
                        <p>{item.feedback}</p>
                        {item.missingPoints.length > 0 && (
                          <p><strong>Missing:</strong> {item.missingPoints.join("; ")}</p>
                        )}
                      </div>

                    </div>

                    {item.idealAnswer && (
                      <details className="ai-flow-model-answer">
                        <summary>Model answer</summary>
                        <p>{item.idealAnswer}</p>
                      </details>
                    )}

                  </article>
                );
              })}

            </div>

          </section>


          {/* RECOMMENDED PRACTICE */}

          {report.recommendedSkills.length > 0 && (
            <section className="ai-flow-report-section">

              <h2>Recommended Practice</h2>

              <div className="ai-flow-next-grid">

                {report.recommendedSkills.map((skill, index) => (
                  <Link
                    key={skill.name}
                    to={skill.practiceRoute || "/career/skill-gap"}
                    className={`ai-flow-next-card${index === 0 ? " primary" : ""}`}
                  >

                    <div className="ai-flow-option-icon">
                      {skill.practiceRoute ? <FiCode /> : <FiBookOpen />}
                    </div>

                    <h3>{skill.name}</h3>

                    <p>{skill.practiceRoute ? "Open targeted practice." : "Study it through your career track and Skill Gap."}</p>

                    <span>
                      Open
                      <FiArrowRight />
                    </span>

                  </Link>
                ))}

              </div>

            </section>
          )}


          {/* NEXT STEPS */}

          {report.nextSteps.length > 0 && (
            <section className="ai-flow-report-section">
              <h2>Next Steps</h2>
              <ol className="ai-flow-feedback-list">
                {report.nextSteps.map((step) => (
                  <li key={step}>{step}</li>
                ))}
              </ol>
            </section>
          )}


          {/* BOTTOM ACTIONS */}

          <div className="ai-flow-report-cta">

            <div className="ai-flow-report-cta-text">
              <FiTarget />
              <p>
                Keep the cycle going: practise your weak areas, then
                interview again to track your improvement.
              </p>
            </div>

            <div className="ai-flow-report-cta-actions">

              <Link to={weakRoute} className="ai-primary-button">
                Practice Weak Skills
                <FiArrowRight />
              </Link>

              <Link
                to="/ai-interview/setup"
                state={{ ...RETRY_STATE[report.type], targetRole: report.targetRole, difficulty: title(report.difficulty) }}
                className="ai-flow-outline-button"
              >
                <FiRefreshCw />
                Retry Interview
              </Link>

              <Link to="/performance" className="ai-flow-outline-button">
                <FiBarChart2 />
                View Performance
              </Link>

              <Link to="/my-plan" className="ai-flow-outline-button">
                <FiCalendar />
                Open My Plan
              </Link>

            </div>

          </div>

        </div>

      </section>

    </div>
  );
}

export default InterviewReport;
