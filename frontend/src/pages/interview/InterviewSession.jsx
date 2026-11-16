import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  FiClock,
  FiUser,
  FiSend,
  FiCheck,
  FiLogOut,
  FiArrowRight,
  FiAlertCircle,
  FiLoader
} from "react-icons/fi";
import { toApiError } from "../../services/api";
import {
  buildStartRequest,
  completeInterview,
  getInterview,
  startInterview,
  submitAnswer
} from "../../services/interviewService";
import { useAuth } from "../../context/AuthContext";

const TYPE_LABELS = {
  TECHNICAL: "Technical",
  HR: "HR",
  BEHAVIORAL: "Behavioral",
  RESUME: "Resume-Based",
  JOB_SPECIFIC: "Job-Specific",
  MIXED: "Mixed"
};

const MAX_ANSWER = 5000;

function formatTime(totalSeconds) {
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, "0");
  const seconds = String(totalSeconds % 60).padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function title(text) {
  return text ? text.charAt(0) + text.slice(1).toLowerCase() : "";
}

function InterviewSession() {
  const navigate = useNavigate();
  const { state: config } = useLocation();
  const [searchParams] = useSearchParams();
  const { isAuthenticated } = useAuth();
  const sessionIdParam = Number(searchParams.get("id")) || null;

  const [session, setSession] = useState(null);
  const [phase, setPhase] = useState(isAuthenticated ? "loading" : "guest");
  const [answer, setAnswer] = useState("");
  const [lastResult, setLastResult] = useState(null);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [seconds, setSeconds] = useState(0);

  const startRef = useRef(null);
  const transcriptEndRef = useRef(null);

  // Start a new interview (from setup) or resume one by id. The request promise is kept
  // in a ref so React StrictMode's double effect never creates two sessions.
  useEffect(() => {
    if (!isAuthenticated) return undefined;
    if (!sessionIdParam && !config?.type) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setPhase("no-config");
      return undefined;
    }

    let active = true;
    if (!startRef.current) {
      startRef.current = sessionIdParam ? getInterview(sessionIdParam) : startInterview(buildStartRequest(config, "TEXT"));
    }

    startRef.current
      .then((data) => {
        if (!active) return;
        if (data.mode === "VOICE") {
          navigate(`/ai-interview/voice/${data.id}`, { replace: true });
          return;
        }
        if (data.status === "COMPLETED") {
          navigate(`/ai-interview/report?id=${data.id}`, { replace: true });
          return;
        }
        setSession(data);
        setNotice(data.notice || "");
        setPhase(data.currentQuestion ? "answering" : "ready-to-finish");
        if (!sessionIdParam) {
          // Keep the session in the URL so a refresh resumes it instead of starting a new one.
          navigate(`/ai-interview/session?id=${data.id}`, { replace: true, state: config });
        }
      })
      .catch((err) => {
        if (!active) return;
        setError(toApiError(err).message);
        setPhase("error");
      });

    return () => {
      active = false;
    };
    // Runs once per mount; the URL update above must not restart the interview.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  useEffect(() => {
    const timer = setInterval(() => setSeconds((value) => value + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    transcriptEndRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [session?.questions?.length, lastResult]);

  const questions = session?.questions || [];
  const current = session?.currentQuestion || null;
  const answeredCount = questions.filter((item) => item.evaluation).length;
  const total = session?.questionCount || 0;
  const questionNumber = current ? current.sequenceNumber : answeredCount;
  const progress = total ? Math.round((answeredCount / total) * 100) : 0;

  const handleSubmit = async (event) => {
    event.preventDefault();
    const text = answer.trim();
    if (!text || !current) return;

    setPhase("submitting");
    setError("");

    try {
      const result = await submitAnswer(session.id, current.id, text);
      const evaluation = {
        answer: text,
        score: result.score,
        strengths: result.strengths,
        missingPoints: result.missingPoints,
        feedback: result.feedback,
        idealAnswer: result.idealAnswer,
        followUpRequired: result.followUpRequired,
        source: result.source
      };

      setSession((value) => ({
        ...value,
        questions: [
          ...value.questions.map((item) => (item.id === current.id ? { ...item, evaluation } : item)),
          ...(result.nextQuestion ? [result.nextQuestion] : [])
        ],
        currentQuestion: result.nextQuestion
      }));
      setLastResult({ ...result, question: current.question });
      setNotice(result.notice || "");
      setAnswer("");
      setPhase("feedback");
    } catch (err) {
      setError(toApiError(err).message);
      setPhase("answering");
    }
  };

  const handleFinish = async () => {
    if (answeredCount === 0) {
      if (window.confirm("Leave this interview? No questions have been answered yet.")) {
        navigate("/ai-interview/setup", { state: config });
      }
      return;
    }

    if (current && phase !== "feedback" && !window.confirm("Finish now and see the report? Unanswered questions are skipped.")) {
      return;
    }

    setPhase("completing");
    setError("");

    try {
      await completeInterview(session.id);
      navigate(`/ai-interview/report?id=${session.id}`);
    } catch (err) {
      setError(toApiError(err).message);
      setPhase(current ? "answering" : "ready-to-finish");
    }
  };

  if (phase === "guest" || phase === "no-config" || (phase === "error" && !session)) {
    return (
      <div className="ai-flow-page">
        <section className="ai-flow-body">
          <div className="section-container ai-flow-session-message">
            <FiAlertCircle />
            <h1>
              {phase === "guest" ? "Sign in to start an AI interview"
                : phase === "no-config" ? "Set up your interview first" : "The interview could not start"}
            </h1>
            <p>
              {phase === "guest" ? "Interviews and reports are saved to your CareerAI account."
                : phase === "no-config" ? "Choose the interview type, role and difficulty on the setup page."
                  : error}
            </p>
            <Link to={phase === "guest" ? "/login" : "/ai-interview/setup"} className="ai-primary-button" state={config}>
              {phase === "guest" ? "Log In" : "Go to Interview Setup"}
              <FiArrowRight />
            </Link>
          </div>
        </section>
      </div>
    );
  }

  if (phase === "loading" || !session) {
    return (
      <div className="ai-flow-page">
        <section className="ai-flow-body">
          <div className="section-container ai-flow-session-message" role="status">
            <FiLoader className="resume-spin" />
            <h1>Preparing your interview…</h1>
            <p>The interviewer is writing your first question. This can take a few seconds.</p>
          </div>
        </section>
      </div>
    );
  }

  const busy = phase === "submitting" || phase === "completing";
  const interviewerState = phase === "submitting" ? "speaking" : "listening";

  return (
    <div className="ai-flow-session">

      {/* HEADER */}

      <header className="ai-flow-session-topbar">

        <div className="ai-flow-session-title">

          <span className="ai-live-dot"></span>

          <div>
            <h1>{TYPE_LABELS[session.type] || session.type} Interview</h1>
            <strong>{session.targetRole}</strong>
            <span>
              Text interview • {title(session.difficulty)}
              {session.resumeTitle ? ` • ${session.resumeTitle}` : ""}
            </span>
          </div>

        </div>

        <div className="ai-flow-session-meta">

          <span className="ai-flow-question-count">
            Question {questionNumber} of {total}
          </span>

          <span className="ai-flow-timer">
            <FiClock />
            {formatTime(seconds)}
          </span>

          <button
            type="button"
            className="ai-flow-end-button"
            onClick={handleFinish}
            disabled={busy}
          >
            <FiLogOut />
            {answeredCount > 0 ? "Finish Interview" : "Exit"}
          </button>

        </div>

      </header>


      {/* PROGRESS */}

      <div className="ai-flow-session-progress">

        <span className="ai-flow-progress-label">
          {answeredCount} of {total} answered
        </span>

        <div className="ai-flow-progress-row">

          <div
            className="ai-flow-progress-track"
            role="progressbar"
            aria-label="Interview progress"
            aria-valuenow={progress}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div style={{ width: `${progress}%` }}></div>
          </div>

          <strong className="ai-flow-progress-value">
            {progress}%
          </strong>

        </div>

      </div>


      {notice && (
        <p className="ai-flow-session-notice" role="status">
          <FiAlertCircle />
          {notice}
        </p>
      )}


      <div className="ai-flow-session-grid">

        {/* STAGE */}

        <section className="ai-flow-stage">

          <div className="ai-flow-stage-tiles">

            <div className={`ai-flow-interviewer-tile ${interviewerState}`}>

              <div className="ai-avatar-wrapper">
                <div className="ai-avatar-ring"></div>
                <div className="ai-avatar">AI</div>
              </div>

              <h2>CareerAI Interviewer</h2>

              <span className={`ai-flow-ai-state ${interviewerState}`} aria-live="polite">
                <span></span>
                {phase === "submitting" ? "Evaluating your answer…"
                  : phase === "completing" ? "Writing your report…"
                    : phase === "feedback" ? "Feedback ready"
                      : "Waiting for your answer"}
              </span>

            </div>


            <div className="ai-flow-self-tile">

              <div className="ai-flow-self-avatar">
                <FiUser />
              </div>

              <strong>You</strong>

              <span className="ai-flow-self-status">Typed answers</span>

            </div>

          </div>


          {/* FEEDBACK FOR THE LAST ANSWER */}

          {phase === "feedback" && lastResult && (
            <div className="ai-flow-feedback-card" aria-live="polite">

              <div className="ai-flow-feedback-head">
                <span>
                  {lastResult.source === "AI" ? "AI evaluation" : "Key-point scoring (AI unavailable)"}
                </span>
                <strong>{lastResult.score} / 100</strong>
              </div>

              <p>{lastResult.feedback}</p>

              <div className="ai-flow-feedback-columns">
                {lastResult.strengths.length > 0 && (
                  <div>
                    <h4>Strengths</h4>
                    <ul>{lastResult.strengths.map((item) => <li key={item}>{item}</li>)}</ul>
                  </div>
                )}
                {lastResult.missingPoints.length > 0 && (
                  <div>
                    <h4>Missing points</h4>
                    <ul>{lastResult.missingPoints.map((item) => <li key={item}>{item}</li>)}</ul>
                  </div>
                )}
              </div>

              <details>
                <summary>Show a model answer</summary>
                <p>{lastResult.idealAnswer}</p>
              </details>

              <div className="ai-flow-controls">
                <span></span>
                {current ? (
                  <button type="button" className="ai-primary-button" onClick={() => setPhase("answering")}>
                    Next Question
                    <FiArrowRight />
                  </button>
                ) : (
                  <button type="button" className="ai-primary-button" onClick={handleFinish} disabled={busy}>
                    {phase === "completing" ? "Writing report…" : "Finish Interview"}
                    <FiCheck />
                  </button>
                )}
              </div>

            </div>
          )}


          {/* CURRENT QUESTION */}

          {phase !== "feedback" && current && (
            <>
              <div className="ai-flow-question-card">
                <span>
                  QUESTION {String(current.sequenceNumber).padStart(2, "0")}
                  {current.topic ? ` · ${current.topic}` : ""}
                  {current.source === "FALLBACK" ? " · question bank" : ""}
                </span>
                <p>{current.question}</p>
              </div>

              <form className="ai-flow-answer-form" onSubmit={handleSubmit}>

                <label htmlFor="ai-flow-answer" className="ai-flow-answer-label">
                  Your answer
                </label>

                <textarea
                  id="ai-flow-answer"
                  rows={7}
                  maxLength={MAX_ANSWER}
                  placeholder="Type your answer as you would explain it in an interview…"
                  value={answer}
                  onChange={(event) => setAnswer(event.target.value)}
                  disabled={busy}
                />

                <p className="ai-flow-answer-helper">
                  Text interview: aim for 80-200 words with a concrete example. Voice answers will come later.
                </p>

                <div className="ai-flow-controls">
                  <span className="ai-flow-answer-helper">{answer.trim() ? answer.trim().split(/\s+/).length : 0} words</span>
                  <button type="submit" className="ai-primary-button" disabled={!answer.trim() || busy}>
                    {phase === "submitting" ? "Evaluating…" : "Submit Answer"}
                    {phase === "submitting" ? <FiLoader className="resume-spin" /> : <FiSend />}
                  </button>
                </div>

              </form>
            </>
          )}


          {phase !== "feedback" && !current && (
            <div className="ai-flow-question-card">
              <span>ALL QUESTIONS ANSWERED</span>
              <p>Finish the interview to see your report.</p>
              <button type="button" className="ai-primary-button" onClick={handleFinish} disabled={busy}>
                {phase === "completing" ? "Writing report…" : "Finish Interview"}
                <FiCheck />
              </button>
            </div>
          )}

          {error && (
            <p className="ai-flow-session-notice error" role="alert">
              <FiAlertCircle />
              {error}
            </p>
          )}

        </section>


        {/* TRANSCRIPT */}

        <aside className="ai-flow-transcript">

          <h2>Transcript</h2>

          <div className="ai-flow-transcript-list">

            {questions.map((item) => (
              <div key={item.id}>
                <div className="ai-flow-transcript-item ai">
                  <strong>AI Interviewer · Q{item.sequenceNumber}</strong>
                  <p>{item.question}</p>
                </div>
                {item.evaluation && (
                  <div className="ai-flow-transcript-item candidate">
                    <strong>You · {item.evaluation.score}/100</strong>
                    <p>{item.evaluation.answer}</p>
                  </div>
                )}
              </div>
            ))}

            <div ref={transcriptEndRef}></div>

          </div>

        </aside>

      </div>

    </div>
  );
}

export default InterviewSession;
