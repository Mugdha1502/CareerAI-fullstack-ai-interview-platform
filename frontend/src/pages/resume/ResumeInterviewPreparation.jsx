import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  FiFileText,
  FiBriefcase,
  FiMic,
  FiArrowRight,
  FiArrowLeft,
  FiCheck,
  FiLoader,
  FiAlertCircle
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import {
  MIN_JOB_DESCRIPTION_LENGTH,
  prepareInterview
} from "../../services/resumeService";
import {
  ResumeGuestNotice,
  ResumeLoadState,
  ResumePicker
} from "../../components/resume/ResumePicker";
import {
  pickDefaultResume,
  useResumeList
} from "../../components/resume/useResumeList";

// Must match the roles offered on the AI Interview setup page.
const roles = [
  "Java Full Stack Developer",
  "Frontend Developer",
  "Backend Developer",
  "Software Engineer",
  "Python Developer",
  "Data Analyst",
  "Data Scientist",
  "DevOps Engineer",
  "Cybersecurity Analyst"
];

const steps = [
  {
    number: 1,
    icon: <FiFileText />,
    title: "Select Resume",
    text: "Choose the saved resume you want CareerAI to use."
  },
  {
    number: 2,
    icon: <FiBriefcase />,
    title: "Add Job Description",
    text: "Optional: add a job description for job-specific topics."
  },
  {
    number: 3,
    icon: <FiMic />,
    title: "Start Interview Preparation",
    text: "Review focus topics and start an AI interview."
  }
];

const PRIORITY_LABELS = { CORE: "Core", GAP: "Gap", GENERAL: "General" };

function ResumeInterviewPreparation() {
  const navigate = useNavigate();
  const { state } = useLocation();

  // Data passed from Job Match prefills the flow.
  const { resumes, status: listStatus, error: listError, reload, upsert } = useResumeList();
  const [chosenId, setChosenId] = useState(state?.resumeId || null);
  const [jobDescription, setJobDescription] = useState(state?.jobDescription || "");
  const [targetRole, setTargetRole] = useState(
    roles.includes(state?.targetRole) ? state.targetRole : "Backend Developer"
  );
  const [step, setStep] = useState(state?.resumeId ? 2 : 1);
  const [status, setStatus] = useState("idle");
  const [error, setError] = useState("");
  const [context, setContext] = useState(null);

  const selectedId = resumes.some((resume) => resume.id === chosenId)
    ? chosenId
    : pickDefaultResume(resumes)?.id ?? null;
  const selectedResume = resumes.find((resume) => resume.id === selectedId);

  const jobLength = jobDescription.trim().length;
  const jobProvided = jobLength > 0;
  const isJobValid = !jobProvided || jobLength >= MIN_JOB_DESCRIPTION_LENGTH;

  const isStepDone = (number) =>
    (number === 1 && Boolean(selectedId) && step > 1) ||
    (number === 2 && step > 2 && Boolean(context));

  const canOpenStep = (number) =>
    number === 1 || (number === 2 && Boolean(selectedId)) || (number === 3 && Boolean(context));

  const handleGenerate = async () => {
    setStep(3);
    setStatus("loading");
    setError("");
    setContext(null);

    try {
      const result = await prepareInterview(selectedId, {
        targetRole,
        jobDescription: jobProvided ? jobDescription.trim() : null
      });
      setContext(result);
      setStatus("done");
    } catch (err) {
      setError(toApiError(err).message);
      setStatus("error");
    }
  };

  const handleStartInterview = () => {
    const isJob = context.interviewType === "job";

    navigate(`/ai-interview/setup?type=${context.interviewType}`, {
      state: {
        source: isJob ? "Job-Based" : "Resume-Based",
        targetRole,
        resumeId: context.resumeId,
        resume: context.resumeTitle,
        jobDescription: isJob ? jobDescription.trim() : "",
        focusTopics: context.focusTopics.map((topic) => topic.name)
      }
    });
  };

  return (
    <div className="tool-page">

      <div className="section-container">

        <div className="tool-page-header">

          <span className="section-tag">
            INTERVIEW PREPARATION
          </span>

          <h1>Prepare from your resume and target job</h1>

          <p>
            Use your saved resume and, optionally, a job description to find
            the topics to prepare before an interview.
          </p>

        </div>


        {listStatus === "guest" && <ResumeGuestNotice />}


        {listStatus !== "guest" && (
          <>
            {/* STEP INDICATOR */}

            <div className="resume-interview-grid">

              {steps.map((item) => {
                const isActive = step === item.number;
                const isDone = isStepDone(item.number);

                return (
                  <button
                    type="button"
                    key={item.number}
                    className={`resume-interview-step prep-step${isActive ? " active" : ""}${isDone ? " done" : ""}`}
                    onClick={() => canOpenStep(item.number) && setStep(item.number)}
                    disabled={!canOpenStep(item.number)}
                    aria-current={isActive ? "step" : undefined}
                  >

                    {isDone ? <FiCheck /> : item.icon}

                    <span>STEP {item.number}</span>

                    <h3>{item.title}</h3>

                    <p>{item.text}</p>

                  </button>
                );
              })}

            </div>


            {/* STEP CONTENT */}

            <section className="prep-panel">

              {step === 1 && (
                <>

                  <h2>Choose a resume</h2>

                  <ResumeLoadState status={listStatus} error={listError} onRetry={reload} />

                  {listStatus === "ready" && (
                    <ResumePicker
                      resumes={resumes}
                      selectedId={selectedId}
                      onSelect={(id) => {
                        setChosenId(id);
                        setContext(null);
                      }}
                      onUploaded={(resume) => {
                        upsert(resume);
                        setChosenId(resume.id);
                        setContext(null);
                      }}
                      name="prep-resume"
                    />
                  )}

                  <div className="prep-actions">
                    <span></span>
                    <button
                      type="button"
                      className="primary-button"
                      onClick={() => setStep(2)}
                      disabled={!selectedId}
                    >
                      Next: Add Job Description
                      <FiArrowRight />
                    </button>
                  </div>

                </>
              )}


              {step === 2 && (
                <>

                  <h2>Add the job description (optional)</h2>

                  <label className="prep-field">
                    <span>Target role</span>
                    <select
                      value={targetRole}
                      onChange={(event) => setTargetRole(event.target.value)}
                    >
                      {roles.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="prep-field">
                    <span>Job description</span>
                    <textarea
                      rows={8}
                      maxLength={20000}
                      placeholder="Paste the job description, including responsibilities and required skills. Leave empty for resume-based preparation."
                      value={jobDescription}
                      onChange={(event) => {
                        setJobDescription(event.target.value);
                        setContext(null);
                      }}
                    />
                  </label>

                  <span className={`jm-hint${jobProvided && isJobValid ? " ok" : ""}`}>
                    {!jobProvided && `No job description: preparation will use ${selectedResume?.title || "your resume"} only.`}
                    {jobProvided && !isJobValid && `Add at least ${MIN_JOB_DESCRIPTION_LENGTH} characters (${jobLength}/${MIN_JOB_DESCRIPTION_LENGTH}), or clear it.`}
                    {jobProvided && isJobValid && `Using ${selectedResume?.title || "your resume"} and this job description.`}
                  </span>

                  <div className="prep-actions">
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => setStep(1)}
                    >
                      <FiArrowLeft />
                      Back
                    </button>
                    <button
                      type="button"
                      className="primary-button"
                      onClick={handleGenerate}
                      disabled={!selectedId || !isJobValid}
                    >
                      Generate Preparation
                      <FiArrowRight />
                    </button>
                  </div>

                </>
              )}


              {step === 3 && status === "loading" && (
                <div className="prep-loading">
                  <FiLoader className="resume-spin" />
                  <p>Reading your resume{jobProvided ? " and the job description" : ""} and preparing questions. This can take up to a minute...</p>
                </div>
              )}


              {step === 3 && status === "error" && (
                <>
                  <p className="resume-inline-error" role="alert">
                    <FiAlertCircle />
                    {error}
                  </p>
                  <div className="prep-actions">
                    <button type="button" className="secondary-button" onClick={() => setStep(2)}>
                      <FiArrowLeft />
                      Back
                    </button>
                    <button type="button" className="primary-button" onClick={handleGenerate}>
                      Try Again
                    </button>
                  </div>
                </>
              )}


              {step === 3 && status === "done" && context && (
                <>

                  <div className="prep-result-header">

                    <div>
                      <span className="resume-demo-badge">
                        {context.aiStatus === "AVAILABLE"
                          ? "AI-generated preparation context"
                          : "Rule-based preparation context · AI unavailable"}
                      </span>
                      <h2>Preparation plan for {targetRole}</h2>
                      <p>
                        Based on {context.resumeTitle}
                        {context.interviewType === "job" ? " and your job description." : "."}
                      </p>
                    </div>

                  </div>

                  <div className="prep-context">
                    <ContextList title="Skills on your resume" items={context.resumeSkills} tone="good" empty="No recognised technical skills were found on this resume." />
                    {context.interviewType === "job" && (
                      <ContextList title="Missing from your resume" items={context.missingSkills} tone="warning" empty="Every recognised job skill appears on your resume." />
                    )}
                  </div>

                  {context.aiStatus !== "AVAILABLE" && context.aiMessage && (
                    <p className="resume-load-state" role="status">{context.aiMessage}</p>
                  )}

                  <h3 className="jm-layer-title">Focus Topics</h3>

                  <div className="prep-topic-grid">

                    {context.focusTopics.map((topic) => (
                      <article
                        key={topic.name}
                        className={`prep-topic ${topic.priority.toLowerCase()}`}
                      >
                        <div className="prep-topic-head">
                          <h3>{topic.name}</h3>
                          <span>{PRIORITY_LABELS[topic.priority] || topic.priority}</span>
                        </div>
                        <p>{topic.reason}</p>
                        {topic.practiceRoute && (
                          <Link to={topic.practiceRoute} className="prep-topic-link">
                            Practise {topic.name}
                            <FiArrowRight />
                          </Link>
                        )}
                      </article>
                    ))}

                  </div>

                  {context.aiStatus === "AVAILABLE" && (
                    <div className="prep-question-grid">
                      <QuestionList title="Likely Resume Questions" items={context.likelyResumeQuestions} />
                      <QuestionList title="Project Questions" items={context.projectQuestions} />
                      <QuestionList title="Experience Questions" items={context.experienceQuestions} />
                      <QuestionList title="Skill Questions" items={context.skillQuestions} />
                      <QuestionList title="Potential Weak Areas" items={context.potentialWeakAreas} tone="warning" />
                    </div>
                  )}

                  <p className="ats-disclaimer">{context.note}</p>

                  <div className="prep-actions">
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => setStep(2)}
                    >
                      <FiArrowLeft />
                      Edit Job Description
                    </button>
                    <button
                      type="button"
                      className="primary-button"
                      onClick={handleStartInterview}
                    >
                      <FiMic />
                      Start {context.interviewType === "job" ? "Job-Based" : "Resume-Based"} AI Interview
                    </button>
                  </div>

                </>
              )}

            </section>
          </>
        )}

      </div>

    </div>
  );
}


function QuestionList({ title, items, tone = "info" }) {
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


function ContextList({ title, items, tone, empty }) {
  return (
    <div className="jm-panel">
      <h3>{title}</h3>
      {items.length > 0 ? (
        <div className={`job-skill-list ${tone}`}>
          {items.map((item) => <span key={item}>{item}</span>)}
        </div>
      ) : (
        <p className="jm-empty-note">{empty}</p>
      )}
    </div>
  );
}

export default ResumeInterviewPreparation;
