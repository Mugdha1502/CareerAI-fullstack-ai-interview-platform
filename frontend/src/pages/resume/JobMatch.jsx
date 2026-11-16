import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  FiFileText,
  FiBriefcase,
  FiTarget,
  FiLoader,
  FiCheckCircle,
  FiAlertCircle,
  FiArrowRight
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import {
  analyzeJobMatch,
  MIN_JOB_DESCRIPTION_LENGTH
} from "../../services/resumeService";
import {
  ResumeGuestNotice,
  ResumeLoadState,
  ResumePicker
} from "../../components/resume/ResumePicker";
import {
  formatDateTime,
  pickDefaultResume,
  useResumeList
} from "../../components/resume/useResumeList";

const ALIGNMENT_LABELS = {
  GOOD: "Good",
  PARTIAL: "Partial",
  LOW: "Low",
  MATCHED: "Matched",
  NOT_FOUND: "Not found",
  NOT_SPECIFIED: "Not specified in job"
};

function JobMatch() {
  const navigate = useNavigate();
  // Applications tracker passes the saved job description through router state.
  const { state: incoming } = useLocation();

  const { resumes, status: listStatus, error: listError, reload, upsert } = useResumeList();
  const [chosenId, setChosenId] = useState(null);
  const [jobTitle, setJobTitle] = useState(incoming?.jobTitle || incoming?.role || "");
  const [company, setCompany] = useState(incoming?.company || "");
  const [jobDescription, setJobDescription] = useState(incoming?.jobDescription || "");
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);

  const selectedId = chosenId ?? pickDefaultResume(resumes)?.id ?? null;
  const jobLength = jobDescription.trim().length;
  const isJobReady = jobLength >= MIN_JOB_DESCRIPTION_LENGTH;
  const canAnalyze = Boolean(selectedId) && isJobReady && status !== "loading";

  const resetResult = () => {
    setStatus("idle");
    setResult(null);
    setError("");
  };

  const handleUploaded = (resume) => {
    upsert(resume);
    setChosenId(resume.id);
    resetResult();
  };

  const handleAnalyze = async () => {
    setStatus("loading");
    setError("");

    try {
      const match = await analyzeJobMatch(selectedId, {
        jobTitle: jobTitle.trim() || null,
        company: company.trim() || null,
        jobDescription: jobDescription.trim()
      });
      setResult(match);
      setStatus("done");
    } catch (err) {
      setError(toApiError(err).message);
      setStatus("error");
    }
  };

  const handlePrepare = () => {
    navigate("/resume/interview-preparation", {
      state: {
        resumeId: result.resumeId,
        jobDescription: jobDescription.trim(),
        targetRole: result.jobTitle || "",
        matchScore: result.overallMatchScore,
        missingSkills: result.missingSkills
      }
    });
  };

  return (
    <div className="tool-page">

      <div className="section-container">

        <div className="tool-page-header">

          <span className="section-tag">
            JOB MATCH
          </span>

          <h1>Compare your resume with a job</h1>

          <p>
            Choose a resume and paste a job description to see matching
            skills, missing keywords and preparation gaps.
          </p>

        </div>


        {listStatus === "guest" && <ResumeGuestNotice />}

        {listStatus !== "guest" && (
          <>
            <div className="job-match-tool-grid">

              {/* RESUME */}

              <div className="job-match-input-card">

                <FiFileText />

                <h3>1. Resume</h3>

                <ResumeLoadState status={listStatus} error={listError} onRetry={reload} />

                {listStatus === "ready" && (
                  <ResumePicker
                    resumes={resumes}
                    selectedId={selectedId}
                    onSelect={(id) => {
                      setChosenId(id);
                      resetResult();
                    }}
                    onUploaded={handleUploaded}
                    name="job-match-resume"
                  />
                )}

              </div>


              {/* JOB DESCRIPTION */}

              <div className="job-match-input-card">

                <FiBriefcase />

                <h3>2. Job Description</h3>

                <div className="jm-job-fields">
                  <input
                    aria-label="Job title (optional)"
                    placeholder="Job title (optional)"
                    maxLength={120}
                    value={jobTitle}
                    onChange={(event) => setJobTitle(event.target.value)}
                  />
                  <input
                    aria-label="Company (optional)"
                    placeholder="Company (optional)"
                    maxLength={120}
                    value={company}
                    onChange={(event) => setCompany(event.target.value)}
                  />
                </div>

                <textarea
                  aria-label="Job description"
                  placeholder="Paste the full job description, including responsibilities and requirements..."
                  maxLength={20000}
                  value={jobDescription}
                  onChange={(event) => {
                    setJobDescription(event.target.value);
                    if (status === "done" || status === "error") resetResult();
                  }}
                ></textarea>

                <span className={`jm-hint${isJobReady ? " ok" : ""}`}>
                  {isJobReady
                    ? "Job description added."
                    : `Add at least ${MIN_JOB_DESCRIPTION_LENGTH} characters (${jobLength}/${MIN_JOB_DESCRIPTION_LENGTH}).`}
                </span>

              </div>

            </div>


            <button
              type="button"
              className="run-analysis-button"
              onClick={handleAnalyze}
              disabled={!canAnalyze}
            >
              {status === "loading" ? <FiLoader className="resume-spin" /> : <FiTarget />}
              {status === "loading" ? "Analyzing (keywords + AI, up to a minute)..." : "Analyze Match"}
            </button>

            {status === "error" && (
              <p className="resume-inline-error jm-error" role="alert">
                <FiAlertCircle />
                {error}
              </p>
            )}
          </>
        )}


        {status === "done" && result && (
          <section
            className="jm-results"
            aria-live="polite"
          >

            <div className="jm-results-header">

              <div>
                <span className="resume-demo-badge">
                  {result.semanticFeedback?.status === "AVAILABLE"
                    ? "Keyword matching + AI semantic feedback"
                    : `${result.label} · AI feedback unavailable`}
                </span>
                <h2>
                  {result.jobTitle ? `Match for ${result.jobTitle}` : "Job match result"}
                  {result.company ? ` at ${result.company}` : ""}
                </h2>
                <p>Compared with {result.resumeTitle} · {formatDateTime(result.createdAt)}</p>
              </div>

              <div className="jm-score">
                <strong>{result.overallMatchScore}%</strong>
                <span>Overall Match</span>
              </div>

            </div>


            <p className="jm-scoring-note">{result.scoringMethod}</p>

            <h3 className="jm-layer-title">Keyword-based result</h3>

            <div className="jm-results-grid">

              <div className="jm-panel">

                <h3>
                  <FiCheckCircle />
                  Matched Skills
                </h3>

                <SkillList skills={result.matchedSkills} tone="good" empty="No recognised job skills were found on your resume." />

              </div>

              <div className="jm-panel">

                <h3>
                  <FiAlertCircle />
                  Missing Skills
                </h3>

                <SkillList skills={result.missingSkills} tone="warning" empty="Every recognised job skill appears on your resume." />

              </div>

            </div>


            <div className="jm-metrics">

              <div className="jm-metric">
                <span>Keyword Match</span>
                <strong>{result.keywordMatchScore}%</strong>
                <small>Skills, keywords, experience and education found literally in your resume.</small>
              </div>

              <div className="jm-metric">
                <span>Semantic Match</span>
                <strong>{result.semanticMatchScore != null ? `${result.semanticMatchScore}%` : "Unavailable"}</strong>
                <small>
                  {result.semanticMatchScore != null
                    ? "AI reading of how your resume content fits the role."
                    : "AI feedback is temporarily unavailable; the keyword result stands on its own."}
                </small>
              </div>

              <div className="jm-metric">
                <span>Keyword Coverage</span>
                <strong>{result.keywordCoverage}%</strong>
                <div className="analysis-progress">
                  <div
                    className="medium"
                    style={{ width: `${result.keywordCoverage}%` }}
                  ></div>
                </div>
              </div>

              <div className="jm-metric">
                <span>Experience Alignment</span>
                <strong>{ALIGNMENT_LABELS[result.experienceAlignment] || result.experienceAlignment}</strong>
                {result.experienceNote && <small>{result.experienceNote}</small>}
              </div>

              <div className="jm-metric">
                <span>Education Alignment</span>
                <strong>{ALIGNMENT_LABELS[result.educationAlignment] || result.educationAlignment}</strong>
                {result.educationNote && <small>{result.educationNote}</small>}
              </div>

            </div>


            {result.missingKeywords.length > 0 && (
              <div className="jm-panel jm-keywords">
                <h3>Job keywords not found on your resume</h3>
                <SkillList skills={result.missingKeywords} tone="warning" />
              </div>
            )}

            {result.recommendations.length > 0 && (
              <div className="ats-demo-list info jm-recommendations">
                <h4>Recommendations</h4>
                <ul>
                  {result.recommendations.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </div>
            )}

            <SemanticSection feedback={result.semanticFeedback} />

            <p className="ats-disclaimer">{result.disclaimer}</p>


            <div className="jm-recommendation">

              <FiTarget />

              <div>
                <span>Recommended Preparation</span>
                <strong>
                  {result.missingSkills.length > 0
                    ? result.missingSkills.slice(0, 3).join(" + ")
                    : "Review the matched skills and your projects"}
                </strong>
              </div>

              <button
                type="button"
                className="primary-button"
                onClick={handlePrepare}
              >
                Prepare for this Job
                <FiArrowRight />
              </button>

            </div>

          </section>
        )}

      </div>

    </div>
  );
}


/** AI semantic interpretation, kept visually separate from the keyword result. */
function SemanticSection({ feedback }) {
  // Matches saved before the AI layer existed have no semantic feedback.
  if (!feedback) return null;

  if (feedback.status !== "AVAILABLE") {
    return (
      <div className="ats-semantic unavailable" role="status">
        <h4>AI semantic feedback</h4>
        <p>{feedback.message || "AI semantic feedback temporarily unavailable."}</p>
      </div>
    );
  }

  return (
    <div className="ats-semantic">
      <div className="ats-semantic-head">
        <h4>AI semantic feedback</h4>
        <span className="resume-demo-badge">AI-assisted</span>
      </div>
      {feedback.summary && <p>{feedback.summary}</p>}
      <FeedbackList title="Strengths" items={feedback.strengths} tone="good" />
      <FeedbackList title="Gaps" items={feedback.gaps} tone="warning" />
      <FeedbackList title="Tailoring Suggestions" items={feedback.tailoringSuggestions} tone="info" />
    </div>
  );
}


function FeedbackList({ title, items, tone }) {
  if (!items || items.length === 0) return null;

  return (
    <div className={`ats-demo-list ${tone}`}>
      <h4>{title}</h4>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}


function SkillList({ skills, tone, empty }) {
  if (!skills || skills.length === 0) {
    return empty ? <p className="jm-empty-note">{empty}</p> : null;
  }

  return (
    <div className={`job-skill-list ${tone}`}>
      {skills.map((skill) => (
        <span key={skill}>{skill}</span>
      ))}
    </div>
  );
}

export default JobMatch;
