import { useRef, useState } from "react";
import {
  FiZap,
  FiAlertCircle,
  FiCopy,
  FiTrash2,
  FiLoader,
  FiInfo,
  FiCheck
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import {
  createVersion,
  getResume,
  improveResume,
  updateResume
} from "../../services/resumeService";
import { useAuth } from "../../context/AuthContext";
import { ResumeGuestNotice } from "../../components/resume/ResumePicker";
import { useResumeList } from "../../components/resume/useResumeList";

const SECTIONS = [
  { id: "SUMMARY", label: "Summary" },
  { id: "EXPERIENCE", label: "Experience" },
  { id: "PROJECT", label: "Project" },
  { id: "SKILLS", label: "Skills" },
  { id: "FULL_RESUME", label: "Full resume" }
];

const CHANGE_LABELS = {
  CLARITY: "Clarity",
  GRAMMAR: "Grammar",
  WORDING: "Wording",
  ACTION_VERB: "Action verb",
  CONCISENESS: "Conciseness",
  STRUCTURE: "Structure"
};

// Builder sections that can be written back safely (single text fields).
const APPLICABLE_SECTIONS = ["SUMMARY", "SKILLS"];

/** Text of one section of a stored resume, used to prefill the editor. */
function sectionText(resume, section) {
  if (!resume) return "";
  if (section === "FULL_RESUME") return resume.extractedText || "";

  const content = resume.content;
  if (!content) return "";

  switch (section) {
    case "SUMMARY":
      return content.summary || "";
    case "SKILLS":
      return (content.skills || []).join(", ");
    case "EXPERIENCE":
      return (content.experience || [])
        .map((entry) => [[entry.title, entry.company].filter(Boolean).join(" - "), entry.description].filter(Boolean).join("\n"))
        .join("\n\n");
    case "PROJECT":
      return (content.projects || [])
        .map((entry) => [entry.name, entry.description].filter(Boolean).join("\n"))
        .join("\n\n");
    default:
      return "";
  }
}

function maxLengthFor(section) {
  return section === "FULL_RESUME" ? 15000 : 5000;
}

function AIResumeImprovement() {
  const { isAuthenticated } = useAuth();
  const { resumes } = useResumeList();

  const [resumeId, setResumeId] = useState("");
  const [resumeDetail, setResumeDetail] = useState(null);
  const [section, setSection] = useState("SUMMARY");
  const [original, setOriginal] = useState("");
  const [result, setResult] = useState(null);
  const [isImproving, setIsImproving] = useState(false);
  const [isApplying, setIsApplying] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const noticeTimer = useRef(null);

  const improved = result?.improvedText || "";
  const isAi = result?.aiConnected === true;
  const canApply = Boolean(result) && resumeDetail?.sourceType === "BUILDER" && APPLICABLE_SECTIONS.includes(result.section);

  const showNotice = (message) => {
    setNotice(message);
    clearTimeout(noticeTimer.current);
    noticeTimer.current = setTimeout(() => setNotice(""), 3200);
  };

  const loadResume = async (id, nextSection) => {
    setResumeId(id);
    setResult(null);
    setError("");

    if (!id) {
      setResumeDetail(null);
      return;
    }

    try {
      const detail = await getResume(Number(id));
      setResumeDetail(detail);
      setOriginal(sectionText(detail, nextSection));
    } catch (err) {
      setResumeDetail(null);
      setError(toApiError(err).message);
    }
  };

  const changeSection = (next) => {
    setSection(next);
    setResult(null);
    if (resumeDetail) setOriginal(sectionText(resumeDetail, next));
  };

  const handleImprove = async () => {
    setIsImproving(true);
    setResult(null);
    setError("");

    try {
      setResult(await improveResume({
        resumeId: resumeId ? Number(resumeId) : null,
        text: original,
        section
      }));
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setIsImproving(false);
    }
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(improved);
      showNotice("Improved text copied to clipboard.");
    } catch {
      showNotice("Copy failed — please select the text and copy it manually.");
    }
  };

  /** Saves a "before" version, then writes the improved text into the builder resume. */
  const handleApply = async () => {
    if (!canApply || !window.confirm("Apply the improved text to this resume? A version of the current resume is saved first.")) {
      return;
    }

    setIsApplying(true);
    setError("");

    try {
      await createVersion(resumeDetail.id, { versionName: `Before ${result.section.toLowerCase()} improvement` });

      const content = { ...resumeDetail.content };
      if (result.section === "SUMMARY") {
        content.summary = improved;
      } else {
        content.skills = improved.split(/[,\n]/).map((item) => item.replace(/^[-•]\s*/, "").trim()).filter(Boolean);
      }

      const updated = await updateResume(resumeDetail.id, { content });
      setResumeDetail(updated);
      setOriginal(sectionText(updated, section));
      showNotice("Applied to your resume. The previous text is saved as a version.");
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setIsApplying(false);
    }
  };

  const handleClear = () => {
    setOriginal("");
    setResult(null);
    setError("");
  };

  return (
    <div className="tool-page">

      <div className="section-container">

        <div className="tool-page-header">

          <span className="section-tag">
            AI RESUME IMPROVEMENT
          </span>

          <h1>Improve your resume wording</h1>

          <p>
            CareerAI suggests clearer wording while keeping all
            qualifications and achievements based on your real
            information.
          </p>

        </div>


        {!isAuthenticated && <ResumeGuestNotice />}

        {isAuthenticated && (
          <>
            <div className="improve-controls">

              <label className="prep-field improve-section-field">
                <span>Resume (optional)</span>
                <select
                  value={resumeId}
                  onChange={(event) => loadResume(event.target.value, section)}
                >
                  <option value="">Paste text instead</option>
                  {resumes.map((item) => (
                    <option key={item.id} value={item.id}>{item.title}</option>
                  ))}
                </select>
              </label>

              <label className="prep-field improve-section-field">
                <span>Resume section</span>
                <select
                  value={section}
                  onChange={(event) => changeSection(event.target.value)}
                >
                  {SECTIONS.map((item) => (
                    <option key={item.id} value={item.id}>{item.label}</option>
                  ))}
                </select>
              </label>

            </div>


            <div className="ai-improvement-card">

              <div className="improvement-before">

                <span>ORIGINAL</span>

                <textarea
                  aria-label="Original resume text"
                  placeholder="Paste a resume section or bullet point, e.g. 'i worked on rest apis using spring boot'"
                  maxLength={maxLengthFor(section)}
                  value={original}
                  onChange={(event) => {
                    setOriginal(event.target.value);
                    setResult(null);
                  }}
                ></textarea>

              </div>


              <div className="improvement-arrow">
                <FiZap />
              </div>


              <div className="improvement-after">

                <span>IMPROVED</span>

                <div
                  className={`improved-text${improved ? " improve-has-result" : ""}`}
                  aria-live="polite"
                >
                  {isImproving && (
                    <span className="improve-loading">
                      <FiLoader className="resume-spin" />
                      Improving wording (AI can take up to a minute)...
                    </span>
                  )}

                  {!isImproving && (improved || "Your improved text will appear here.")}
                </div>

              </div>

            </div>


            <div className="improve-actions">

              <button
                type="button"
                className="primary-button"
                onClick={handleImprove}
                disabled={!original.trim() || isImproving}
              >
                <FiZap />
                Improve
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={handleCopy}
                disabled={!improved}
              >
                <FiCopy />
                Copy
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={handleApply}
                disabled={!canApply || isApplying}
                title={canApply ? "" : "Available for the Summary and Skills sections of Resume Builder resumes"}
              >
                {isApplying ? <FiLoader className="resume-spin" /> : <FiCheck />}
                Apply to Resume
              </button>

              <button
                type="button"
                className="secondary-button"
                onClick={handleClear}
                disabled={!original && !result}
              >
                <FiTrash2 />
                Clear
              </button>

              {result && (
                <span className="resume-demo-badge">
                  {isAi ? "AI-assisted improvement" : "Basic wording assistance"}
                </span>
              )}

            </div>


            {error && (
              <p className="resume-inline-error" role="alert">
                <FiAlertCircle />
                {error}
              </p>
            )}


            {result?.message && (
              <div className={`resume-ai-status${isAi ? "" : " fallback"}`} role="note">
                <FiInfo />
                <p>{result.message}</p>
              </div>
            )}


            {result && (
              <div className="improve-feedback">

                <div className="ats-demo-list good">
                  <h4>Changes</h4>
                  <ul>
                    {result.changes.map((change) => (
                      <li key={`${change.type}-${change.description}`}>
                        <strong>{CHANGE_LABELS[change.type] || change.type}:</strong> {change.description}
                      </li>
                    ))}
                  </ul>
                </div>

                {(result.warnings.length > 0 || result.suggestions.length > 0) && (
                  <div className="ats-demo-list warning">
                    <h4>Warnings</h4>
                    <ul>
                      {[...result.warnings, ...result.suggestions].map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  </div>
                )}

              </div>
            )}
          </>
        )}


        <div className="resume-ai-warning">

          <FiAlertCircle />

          <p>
            CareerAI improves wording only. It must not invent
            experience, qualifications, achievements or metrics — new
            numbers or technologies in an AI rewrite are rejected automatically.
            Always review changes before applying them.
          </p>

        </div>

      </div>


      {notice && (
        <div
          className="resume-toast"
          role="status"
        >
          {notice}
        </div>
      )}

    </div>
  );
}

export default AIResumeImprovement;
