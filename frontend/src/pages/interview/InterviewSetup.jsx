import { useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  FiTarget,
  FiFileText,
  FiBriefcase,
  FiCode,
  FiUsers,
  FiMessageCircle,
  FiLayers,
  FiClock,
  FiBarChart2,
  FiCheckCircle,
  FiArrowLeft,
  FiArrowRight,
  FiType,
  FiMic
} from "react-icons/fi";
import { isVoiceAvailable } from "../../services/vapiService";
import {
  ResumeEmptyState,
  ResumeLoadState,
  ResumeUploadButton
} from "../../components/resume/ResumePicker";
import { pickDefaultResume, useResumeList } from "../../components/resume/useResumeList";

const sources = [
  {
    id: "role",
    label: "Role-Based",
    icon: <FiTarget />,
    description: "Questions built around the skills your target role requires."
  },
  {
    id: "resume",
    label: "Resume-Based",
    icon: <FiFileText />,
    description: "Questions about your own projects, skills and experience."
  },
  {
    id: "job",
    label: "Job-Based",
    icon: <FiBriefcase />,
    description: "Questions matched to the requirements of a specific job."
  }
];

const categories = [
  { id: "technical", label: "Technical", icon: <FiCode /> },
  { id: "hr", label: "HR", icon: <FiUsers /> },
  { id: "behavioural", label: "Behavioural", icon: <FiMessageCircle /> },
  { id: "mixed", label: "Mixed", icon: <FiLayers /> }
];

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

const difficulties = ["Beginner", "Intermediate", "Advanced"];

const durations = [10, 20, 30];

// Text interview length: number of questions for each duration.
const QUESTIONS_FOR_DURATION = { 10: 4, 20: 6, 30: 8 };

// Spoken answers take longer, so voice interviews ask fewer questions for the same time.
const VOICE_QUESTIONS_FOR_DURATION = { 10: 3, 20: 4, 30: 5 };

// Must match JobMatchService.MIN_JOB_DESCRIPTION_LENGTH on the backend.
const MIN_JOB_DESCRIPTION = 80;

const CATEGORY_TYPES = { technical: "TECHNICAL", hr: "HR", behavioural: "BEHAVIORAL", mixed: "MIXED" };

/** Backend interview type from the chosen source and category. */
function interviewType(source, category) {
  if (source === "resume") return "RESUME";
  if (source === "job") return "JOB_SPECIFIC";
  return CATEGORY_TYPES[category] || "TECHNICAL";
}


// ?type= takes priority, then a config passed back from a later step.
function getInitialSource(searchParams, previous) {
  const type = searchParams.get("type");

  if (sources.some((item) => item.id === type)) {
    return type;
  }

  const match = sources.find((item) => item.label === previous?.source);

  return match ? match.id : "role";
}

function InterviewSetup() {
  const navigate = useNavigate();
  const { state: previous } = useLocation();
  const [searchParams] = useSearchParams();

  const [source, setSource] = useState(() => getInitialSource(searchParams, previous));
  const [category, setCategory] = useState(
    () => categories.find((item) => item.label === previous?.category)?.id || "technical"
  );
  const [role, setRole] = useState(
    () => (roles.includes(previous?.targetRole) ? previous.targetRole : roles[0])
  );
  const [difficulty, setDifficulty] = useState(
    () => (difficulties.includes(previous?.difficulty) ? previous.difficulty : "Intermediate")
  );
  const voiceAvailable = isVoiceAvailable();
  const [mode, setMode] = useState(() => (previous?.mode === "VOICE" && voiceAvailable ? "VOICE" : "TEXT"));
  const [duration, setDuration] = useState(
    () => (durations.includes(parseInt(previous?.duration, 10)) ? parseInt(previous.duration, 10) : 20)
  );
  // Saved resumes of the signed-in user (Resume-Based interviews use one of them).
  const { resumes, status: resumeStatus, error: resumeError, reload: reloadResumes, upsert: addResume } = useResumeList();
  const [chosenResumeId, setChosenResumeId] = useState(previous?.resumeId || null);
  const [jobResumeId, setJobResumeId] = useState(previous?.resumeId ? String(previous.resumeId) : "");
  const [changingResume, setChangingResume] = useState(false);
  const [jobDescription, setJobDescription] = useState(previous?.jobDescription || "");
  const focusTopics = Array.isArray(previous?.focusTopics) ? previous.focusTopics : [];

  const selectedSource = sources.find((item) => item.id === source);
  const selectedCategory = categories.find((item) => item.id === category);

  const isJobDescriptionMissing =
    source === "job" && jobDescription.trim().length < MIN_JOB_DESCRIPTION;

  const selectedResume = resumes.find((item) => item.id === chosenResumeId) || pickDefaultResume(resumes);

  const isResumeMissing = source === "resume" && !selectedResume;

  // A resume handed over from Interview Preparation is used without asking again.
  const suppliedResume = previous?.resumeId && selectedResume?.id === previous.resumeId && !changingResume
    ? selectedResume
    : null;

  const handleContinue = (event) => {
    event.preventDefault();

    if (isJobDescriptionMissing || isResumeMissing) {
      return;
    }

    const resumeId = source === "resume"
      ? selectedResume.id
      : source === "job" && jobResumeId ? Number(jobResumeId) : null;

    const interviewConfig = {
      source: selectedSource.label,
      category: selectedCategory.label,
      type: interviewType(source, category),
      targetRole: role,
      difficulty,
      duration: `${duration} minutes`,
      mode,
      questionCount: (mode === "VOICE" ? VOICE_QUESTIONS_FOR_DURATION[duration] : QUESTIONS_FOR_DURATION[duration]) || 6,
      resume: resumeId ? resumes.find((item) => item.id === resumeId)?.title || "" : "",
      resumeId,
      // Focus topics handed over from Resume Interview Preparation.
      focusTopics,
      jobDescription: source === "job" ? jobDescription.trim() : ""
    };

    // Text interviews start directly; voice interviews check the microphone first.
    navigate(mode === "VOICE" ? "/ai-interview/device-check" : "/ai-interview/session", {
      state: interviewConfig
    });
  };

  return (
    <div className="ai-flow-page">

      <section className="ai-flow-header">

        <div className="section-container">

          <ol className="ai-flow-stepper" aria-label="Interview progress">
            <li className="active">Setup</li>
            <li>Device Check</li>
            <li>Interview</li>
            <li>Report</li>
          </ol>

          <span className="section-tag">
            AI INTERVIEW SETUP
          </span>

          <h1>Configure your mock interview</h1>

          <p>
            Choose what your interview should be based on, the type of
            questions you want and how long you would like to practise.
          </p>

        </div>

      </section>


      <section className="ai-flow-body">

        <form
          className="section-container ai-flow-setup-layout"
          onSubmit={handleContinue}
        >

          <div className="ai-flow-setup-main">

            {/* MODE */}

            <fieldset className="ai-flow-panel">

              <legend>
                <span>00</span>
                Interview mode
              </legend>

              <div className="ai-flow-option-grid three" role="radiogroup" aria-label="Interview mode">

                <button
                  type="button"
                  className={`ai-flow-option-card${mode === "TEXT" ? " selected" : ""}`}
                  aria-pressed={mode === "TEXT"}
                  onClick={() => setMode("TEXT")}
                >
                  <div className="ai-flow-option-icon"><FiType /></div>
                  <strong>Text Interview</strong>
                  <p>Read each question and type your answer. Works everywhere.</p>
                  {mode === "TEXT" && <FiCheckCircle className="ai-flow-option-check" />}
                </button>

                <button
                  type="button"
                  className={`ai-flow-option-card${mode === "VOICE" ? " selected" : ""}`}
                  aria-pressed={mode === "VOICE"}
                  onClick={() => voiceAvailable && setMode("VOICE")}
                  disabled={!voiceAvailable}
                >
                  <div className="ai-flow-option-icon"><FiMic /></div>
                  <strong>Voice Interview</strong>
                  <p>
                    {voiceAvailable
                      ? "Speak with a realtime AI interviewer. Needs a microphone."
                      : "Voice interview unavailable: the voice service is not configured."}
                  </p>
                  {mode === "VOICE" && <FiCheckCircle className="ai-flow-option-check" />}
                </button>

              </div>

            </fieldset>


            {/* SOURCE */}

            <fieldset className="ai-flow-panel">

              <legend>
                <span>01</span>
                Interview source
              </legend>

              <div className="ai-flow-option-grid three">

                {sources.map((item) => (
                  <button
                    type="button"
                    key={item.id}
                    className={`ai-flow-option-card${source === item.id ? " selected" : ""}`}
                    aria-pressed={source === item.id}
                    onClick={() => setSource(item.id)}
                  >

                    <div className="ai-flow-option-icon">
                      {item.icon}
                    </div>

                    <strong>{item.label}</strong>

                    <p>{item.description}</p>

                    {source === item.id && (
                      <FiCheckCircle className="ai-flow-option-check" />
                    )}

                  </button>
                ))}

              </div>


              {source === "resume" && (
                <div className="ai-flow-conditional">

                  <h3>Choose a resume</h3>

                  {resumeStatus === "guest" && (
                    <p className="ai-flow-hint warning">
                      Sign in with a CareerAI account to use your saved resumes.
                    </p>
                  )}

                  <ResumeLoadState status={resumeStatus} error={resumeError} onRetry={reloadResumes} />

                  {resumeStatus === "ready" && resumes.length === 0 && (
                    <ResumeEmptyState compact onUploaded={(item) => { addResume(item); setChosenResumeId(item.id); }} />
                  )}

                  {resumeStatus === "ready" && suppliedResume && (
                    <p className="ai-flow-hint">
                      Using <strong>{suppliedResume.title}</strong> from Interview Preparation
                      {focusTopics.length > 0 && ` with focus topics: ${focusTopics.join(", ")}`}.{" "}
                      <button type="button" className="ai-flow-link-button" onClick={() => setChangingResume(true)}>
                        Change resume
                      </button>
                    </p>
                  )}

                  {resumeStatus === "ready" && resumes.length > 0 && !suppliedResume && (
                    <>
                      <div className="ai-flow-resume-list">

                        {resumes.map((item) => (
                          <label
                            key={item.id}
                            className={`ai-flow-resume-item${selectedResume?.id === item.id ? " selected" : ""}`}
                          >
                            <input
                              type="radio"
                              name="resume"
                              value={item.id}
                              checked={selectedResume?.id === item.id}
                              onChange={() => setChosenResumeId(item.id)}
                            />
                            <FiFileText />
                            <span>{item.title}</span>
                          </label>
                        ))}

                      </div>

                      <ResumeUploadButton onUploaded={(item) => { addResume(item); setChosenResumeId(item.id); }} />
                    </>
                  )}

                </div>
              )}


              {source === "job" && (
                <div className="ai-flow-conditional">

                  <label
                    className="ai-flow-field-label"
                    htmlFor="ai-flow-job-description"
                  >
                    Job description
                  </label>

                  <textarea
                    id="ai-flow-job-description"
                    className="ai-flow-textarea"
                    rows={7}
                    placeholder="Paste the job description, including responsibilities and required skills..."
                    value={jobDescription}
                    onChange={(event) => setJobDescription(event.target.value)}
                  />

                  <span
                    className={`ai-flow-hint${isJobDescriptionMissing ? " warning" : ""}`}
                  >
                    {isJobDescriptionMissing
                      ? `Add at least ${MIN_JOB_DESCRIPTION} characters so CareerAI can match the job requirements (${jobDescription.trim().length}/${MIN_JOB_DESCRIPTION}).`
                      : "Job description added."}
                  </span>

                  {resumeStatus === "ready" && resumes.length > 0 && (
                    <>
                      <label className="ai-flow-field-label" htmlFor="ai-flow-job-resume">
                        Resume (optional)
                      </label>
                      <select
                        id="ai-flow-job-resume"
                        className="ai-flow-select"
                        value={jobResumeId}
                        onChange={(event) => setJobResumeId(event.target.value)}
                      >
                        <option value="">No resume — questions from the job description only</option>
                        {resumes.map((item) => (
                          <option key={item.id} value={item.id}>{item.title}</option>
                        ))}
                      </select>
                    </>
                  )}

                </div>
              )}

            </fieldset>


            {/* CATEGORY */}

            <fieldset className="ai-flow-panel">

              <legend>
                <span>02</span>
                Interview category
              </legend>

              <div className="ai-flow-option-grid four">

                {categories.map((item) => (
                  <button
                    type="button"
                    key={item.id}
                    className={`ai-flow-chip-card${category === item.id ? " selected" : ""}`}
                    aria-pressed={category === item.id}
                    onClick={() => setCategory(item.id)}
                  >
                    {item.icon}
                    <span>{item.label}</span>
                  </button>
                ))}

              </div>

            </fieldset>


            {/* ROLE, DIFFICULTY, DURATION */}

            <fieldset className="ai-flow-panel">

              <legend>
                <span>03</span>
                Role and format
              </legend>

              <label
                className="ai-flow-field-label"
                htmlFor="ai-flow-role"
              >
                Target role
              </label>

              <select
                id="ai-flow-role"
                className="ai-flow-select"
                value={role}
                onChange={(event) => setRole(event.target.value)}
              >
                {roles.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>


              <span className="ai-flow-field-label">
                Difficulty
              </span>

              <div
                className="ai-flow-segmented"
                role="group"
                aria-label="Difficulty"
              >
                {difficulties.map((item) => (
                  <button
                    type="button"
                    key={item}
                    className={difficulty === item ? "selected" : ""}
                    aria-pressed={difficulty === item}
                    onClick={() => setDifficulty(item)}
                  >
                    {item}
                  </button>
                ))}
              </div>


              <span className="ai-flow-field-label">
                Duration
              </span>

              <div
                className="ai-flow-segmented"
                role="group"
                aria-label="Duration"
              >
                {durations.map((item) => (
                  <button
                    type="button"
                    key={item}
                    className={duration === item ? "selected" : ""}
                    aria-pressed={duration === item}
                    onClick={() => setDuration(item)}
                  >
                    {item} minutes
                  </button>
                ))}
              </div>

            </fieldset>

          </div>


          {/* SUMMARY */}

          <aside className="ai-flow-summary-card">

            <span className="ai-dark-tag">
              INTERVIEW SUMMARY
            </span>

            <h2>{role}</h2>

            <ul className="ai-flow-summary-list">

              <li>
                {selectedSource.icon}
                <span>Source</span>
                <strong>{selectedSource.label}</strong>
              </li>

              <li>
                {selectedCategory.icon}
                <span>Category</span>
                <strong>{selectedCategory.label}</strong>
              </li>

              <li>
                <FiBarChart2 />
                <span>Difficulty</span>
                <strong>{difficulty}</strong>
              </li>

              <li>
                {mode === "VOICE" ? <FiMic /> : <FiType />}
                <span>Mode</span>
                <strong>{mode === "VOICE" ? "Voice interview" : "Text interview"}</strong>
              </li>

              <li>
                <FiClock />
                <span>Length</span>
                <strong>
                  {(mode === "VOICE" ? VOICE_QUESTIONS_FOR_DURATION[duration] : QUESTIONS_FOR_DURATION[duration]) || 6} questions · about {duration} minutes
                </strong>
              </li>

              {source === "resume" && (
                <li>
                  <FiFileText />
                  <span>Resume</span>
                  <strong>{selectedResume?.title || "Not selected"}</strong>
                </li>
              )}

            </ul>

            <div className="ai-flow-actions">

              <button
                type="submit"
                className="ai-primary-button"
                disabled={isJobDescriptionMissing || isResumeMissing}
              >
                {mode === "VOICE" ? "Continue to Device Check" : "Start Text Interview"}
                <FiArrowRight />
              </button>

              <Link
                to="/ai-interview"
                className="ai-flow-back-link"
              >
                <FiArrowLeft />
                Back to AI Interview
              </Link>

            </div>

          </aside>

        </form>

      </section>

    </div>
  );
}

export default InterviewSetup;
