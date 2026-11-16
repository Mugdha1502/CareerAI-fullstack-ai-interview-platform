import { useState } from "react";
import { Link } from "react-router-dom";
import {
  FiFileText,
  FiUploadCloud,
  FiLoader,
  FiAlertCircle,
  FiEdit3,
  FiLogIn,
  FiRefreshCw
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import {
  ACCEPTED_RESUME_TYPES,
  uploadResume,
  validateResumeFile
} from "../../services/resumeService";
import { describeResume } from "./useResumeList";

/* =====================================================
   Shared pieces for the resume tool pages: account gate,
   loading/error/empty states and the stored-resume picker.
===================================================== */

/** Resume tools save to the account, so local guest sessions see a sign-in prompt. */
export function ResumeGuestNotice() {
  return (
    <div className="rv-empty resume-gate">
      <FiLogIn />
      <h3>Sign in to use resume tools</h3>
      <p>
        Resumes, versions and analyses are saved to your CareerAI account.
        Guest sessions are stored only in this browser, so please sign in or create an account.
      </p>
      <div className="resume-empty-actions">
        <Link to="/login" className="primary-button">Log In</Link>
        <Link to="/register" className="secondary-button">Create Account</Link>
      </div>
    </div>
  );
}

export function ResumeLoadState({ status, error, onRetry, label = "Loading your resumes..." }) {
  if (status === "loading") {
    return (
      <div className="resume-load-state" role="status">
        <FiLoader className="resume-spin" />
        {label}
      </div>
    );
  }

  if (status === "error") {
    return (
      <div className="resume-load-state error" role="alert">
        <FiAlertCircle />
        <span>{error}</span>
        {onRetry && (
          <button type="button" onClick={onRetry}>
            <FiRefreshCw />
            Retry
          </button>
        )}
      </div>
    );
  }

  return null;
}

/** Brand-new users: no resumes yet, with Build and Upload actions. */
export function ResumeEmptyState({ onUploaded, compact = false }) {
  return (
    <div className={`rv-empty resume-empty${compact ? " compact" : ""}`}>
      <FiFileText />
      <h3>No resumes yet</h3>
      <p>Build a resume with the Resume Builder or upload a PDF, DOC or DOCX file.</p>
      <div className="resume-empty-actions">
        <Link to="/resume/builder" className="primary-button">
          <FiEdit3 />
          Build Resume
        </Link>
        <ResumeUploadButton onUploaded={onUploaded} label="Upload Resume" className="secondary-button" />
      </div>
    </div>
  );
}

/**
 * Upload control. Validates on the client first, then uploads; the backend
 * re-validates type, size and content and extracts the text.
 */
export function ResumeUploadButton({ onUploaded, label = "Upload a different resume", className = "resume-upload-inline" }) {
  const [state, setState] = useState({ uploading: false, error: "" });

  const handleChange = async (event) => {
    const file = event.target.files[0];
    // Allow choosing the same file again later.
    event.target.value = "";
    if (!file) return;

    const problem = validateResumeFile(file);
    if (problem) {
      setState({ uploading: false, error: problem });
      return;
    }

    setState({ uploading: true, error: "" });

    try {
      const resume = await uploadResume(file);
      setState({ uploading: false, error: "" });
      onUploaded?.(resume);
    } catch (err) {
      setState({ uploading: false, error: toApiError(err).message });
    }
  };

  return (
    <>
      <label className={`${className}${state.uploading ? " is-busy" : ""}`} aria-busy={state.uploading}>
        {state.uploading ? <FiLoader className="resume-spin" /> : <FiUploadCloud />}
        {state.uploading ? "Uploading and reading text..." : label}
        <input
          type="file"
          accept={ACCEPTED_RESUME_TYPES}
          hidden
          disabled={state.uploading}
          onChange={handleChange}
        />
      </label>
      {state.error && (
        <p className="resume-inline-error" role="alert">
          <FiAlertCircle />
          {state.error}
        </p>
      )}
    </>
  );
}

/** Radio list of the user's stored resumes plus an inline upload. */
export function ResumePicker({ resumes, selectedId, onSelect, onUploaded, name = "resume" }) {
  if (resumes.length === 0) {
    return <ResumeEmptyState onUploaded={onUploaded} compact />;
  }

  return (
    <>
      <div className="resume-choice-list" role="radiogroup" aria-label="Choose a resume">
        {resumes.map((resume) => (
          <label
            key={resume.id}
            className={`resume-choice${selectedId === resume.id ? " selected" : ""}`}
          >
            <input
              type="radio"
              name={name}
              checked={selectedId === resume.id}
              onChange={() => onSelect(resume.id)}
            />
            <FiFileText />
            <span className="resume-choice-text">
              <span>{resume.title}</span>
              <small>{describeResume(resume)}</small>
            </span>
          </label>
        ))}
      </div>

      <ResumeUploadButton onUploaded={onUploaded} />
    </>
  );
}
