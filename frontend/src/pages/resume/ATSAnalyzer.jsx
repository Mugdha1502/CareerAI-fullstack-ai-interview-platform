import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  FiFileText,
  FiCheckCircle,
  FiAlertCircle,
  FiSearch,
  FiLoader,
  FiArrowRight
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import { analyzeATS, getLatestATSAnalysis } from "../../services/resumeService";
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

function toneFor(score) {
  if (score >= 75) return "good";
  if (score >= 50) return "medium";
  return "warning";
}

function ATSAnalyzer() {
  const { resumes, status: listStatus, error: listError, reload, upsert } = useResumeList();
  const [chosenId, setChosenId] = useState(null);
  // Results are keyed by resume so switching resumes never shows another resume's analysis.
  const [results, setResults] = useState({});
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState("");

  const selectedId = chosenId ?? pickDefaultResume(resumes)?.id ?? null;
  const selected = resumes.find((resume) => resume.id === selectedId);
  const entry = selectedId ? results[selectedId] : undefined;

  // Load the latest stored analysis for the selected resume.
  useEffect(() => {
    if (!selectedId || results[selectedId]) return undefined;

    let cancelled = false;

    getLatestATSAnalysis(selectedId)
      .then((analysis) => {
        if (!cancelled) setResults((current) => ({ ...current, [selectedId]: { analysis, error: "" } }));
      })
      .catch((err) => {
        if (!cancelled) setResults((current) => ({ ...current, [selectedId]: { analysis: null, error: toApiError(err).message } }));
      });

    return () => {
      cancelled = true;
    };
  }, [selectedId, results]);

  const handleSelect = (id) => {
    setChosenId(id);
    setRunError("");
  };

  const handleUploaded = (resume) => {
    upsert(resume);
    setChosenId(resume.id);
    setRunError("");
  };

  const handleAnalyze = async () => {
    if (!selectedId) return;
    setRunning(true);
    setRunError("");

    try {
      const analysis = await analyzeATS(selectedId);
      setResults((current) => ({ ...current, [selectedId]: { analysis, error: "" } }));
    } catch (err) {
      setRunError(toApiError(err).message);
    } finally {
      setRunning(false);
    }
  };

  const analysis = entry?.analysis;

  return (
    <div className="tool-page">

      <div className="section-container">

        <div className="tool-page-header">

          <span className="section-tag">
            ATS ANALYZER
          </span>

          <h1>Check your resume readiness</h1>

          <p>
            Choose a saved resume or upload one, then review structure,
            keywords, content and experience checks.
          </p>

        </div>


        {listStatus === "guest" && <ResumeGuestNotice />}

        {listStatus !== "guest" && (
          <div className="ats-tool-grid">

            <div className="resume-upload-box ats-picker-box">

              <FiFileText />

              <h3>Choose a resume</h3>

              <p>
                Select a saved resume or upload a PDF, DOC or DOCX file (max 5 MB).
                Text is read on the CareerAI server; scanned images are not supported.
              </p>

              <ResumeLoadState status={listStatus} error={listError} onRetry={reload} />

              {listStatus === "ready" && (
                <ResumePicker
                  resumes={resumes}
                  selectedId={selectedId}
                  onSelect={handleSelect}
                  onUploaded={handleUploaded}
                  name="ats-resume"
                />
              )}

              <button
                type="button"
                className="ats-demo-analyze"
                onClick={handleAnalyze}
                disabled={!selectedId || running}
              >
                {running ? <FiLoader className="resume-spin" /> : <FiSearch />}
                {running ? "Analyzing..." : "Analyze Resume"}
              </button>

              {runError && (
                <p className="resume-inline-error" role="alert">
                  <FiAlertCircle />
                  {runError}
                </p>
              )}

            </div>


            <div className="ats-result-preview">

              {(!analysis || running) && (
                <div className="ats-demo-empty">

                  {running || (selectedId && !entry) ? (
                    <>
                      <FiLoader className="resume-spin" />
                      <h3>{running ? `Analyzing ${selected?.title}...` : "Loading analysis..."}</h3>
                      <p>{running ? "Running rule checks and AI feedback. This can take up to a minute." : "Checking structure, keywords and content."}</p>
                    </>
                  ) : (
                    <>
                      <FiSearch />
                      <h3>No analysis yet</h3>
                      <p>
                        {entry?.error
                          ? entry.error
                          : selected
                            ? `Click Analyze Resume to check "${selected.title}".`
                            : "Choose or upload a resume to get started."}
                      </p>
                    </>
                  )}

                </div>
              )}


              {analysis && !running && (
                <>

                  <span className="resume-demo-badge">
                    {analysis.semanticFeedback?.status === "AVAILABLE"
                      ? "Rule-based checks + AI-assisted feedback"
                      : `${analysis.label} · rule-based`}
                  </span>

                  <div className="ats-result-header">

                    <FiSearch />

                    <div>
                      <span>RESUME READINESS</span>
                      <strong>{analysis.overallScore}%</strong>
                    </div>

                  </div>

                  <p className="ats-result-meta">
                    {analysis.resumeTitle} · {analysis.wordCount} words · analysed {formatDateTime(analysis.analyzedAt)}
                  </p>

                  {analysis.scoringMethod && (
                    <p className="ats-result-meta">
                      {analysis.scoringMethod}
                      {analysis.semanticFeedback?.status === "AVAILABLE" &&
                        ` Rule-based score: ${analysis.ruleBasedScore}%. AI writing quality: ${analysis.semanticFeedback.writingQualityScore}%.`}
                    </p>
                  )}

                  <ATSResult text="Formatting & structure" score={analysis.formattingScore} />
                  <ATSResult text="Keywords & skills" score={analysis.keywordScore} />
                  <ATSResult text="Content quality" score={analysis.contentScore} />
                  <ATSResult text="Experience & projects" score={analysis.experienceScore} />

                  {analysis.detectedSkills.length > 0 && (
                    <div className="ats-demo-list info">
                      <h4>Skills recognised</h4>
                      <div className="job-skill-list good">
                        {analysis.detectedSkills.map((skill) => (
                          <span key={skill}>{skill}</span>
                        ))}
                      </div>
                    </div>
                  )}

                  <ResultList title="Strengths" items={analysis.strengths} tone="good" />
                  <ResultList title="Issues found" items={analysis.issues} tone="warning" />
                  <ResultList title="Recommended improvements" items={analysis.recommendations} tone="info" />

                  <SemanticFeedback feedback={analysis.semanticFeedback} />

                  <p className="ats-disclaimer">{analysis.disclaimer}</p>

                  <Link
                    to="/resume/ai-improvement"
                    className="ats-demo-next"
                  >
                    Tidy wording with Resume Improvement
                    <FiArrowRight />
                  </Link>

                </>
              )}

            </div>

          </div>
        )}

      </div>

    </div>
  );
}


/** AI layer: shown separately from the objective rule-based checks. */
function SemanticFeedback({ feedback }) {
  // Analyses saved before the AI layer existed have no semantic feedback.
  if (!feedback) return null;

  if (feedback.status !== "AVAILABLE") {
    return (
      <div className="ats-semantic unavailable" role="status">
        <h4>AI feedback</h4>
        <p>{feedback.message || "AI feedback temporarily unavailable."} The rule-based analysis above is complete.</p>
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
      <ResultList title="AI Strengths" items={feedback.strengths} tone="good" />
      <ResultList title="AI Issues" items={feedback.issues} tone="warning" />
      <ResultList title="AI Recommendations" items={feedback.recommendations} tone="info" />
    </div>
  );
}


function ATSResult({ text, score }) {
  const tone = toneFor(score);

  return (
    <div className={`ats-result-row ats-demo-${tone}`}>
      {tone === "good" ? <FiCheckCircle /> : <FiAlertCircle />}
      <span>{text}</span>
      <strong>{score}%</strong>
    </div>
  );
}


function ResultList({ title, items, tone }) {
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

export default ATSAnalyzer;
