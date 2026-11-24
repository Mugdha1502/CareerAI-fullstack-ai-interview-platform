import { useRef, useState } from "react";
import {
  FiZap,
  FiClock,
  FiBookOpen,
  FiGrid,
  FiDatabase,
  FiCode,
  FiArrowRight,
  FiCheckCircle,
  FiTrendingUp
} from "react-icons/fi";
import { useAuth } from "../../context/auth";
import {
  addXP,
  getStreak,
  recordDailyActivity,
  XP_REWARDS
} from "../../services/xpService";
import QuizRunner from "./QuizRunner";
import PracticeResult from "./PracticeResult";
import PracticeSaveStatus from "./PracticeSaveStatus";
import {
  canSavePractice,
  startPracticeSession,
  savePracticeResult
} from "../../services/practiceService";
import { getDailyChallenge } from "./practiceProvider";
import { summarizeAreas } from "./practiceUtils";

const categoryIcons = {
  Technical: <FiBookOpen />,
  Aptitude: <FiGrid />,
  SQL: <FiDatabase />,
  Coding: <FiCode />
};

// xpService stores streak dates as UTC ISO dates; match that convention.
const isoDay = (offsetDays = 0) =>
  new Date(Date.now() + offsetDays * 86400000).toISOString().slice(0, 10);

function readCompletion(key) {
  if (!key) return null;

  try {
    return JSON.parse(localStorage.getItem(key));
  } catch {
    return null;
  }
}

function DailyChallengeWorkspace() {
  const { user } = useAuth();

  // TODO backend: GET /api/practice/daily-challenge (same content for everyone today).
  const [challenge] = useState(() => getDailyChallenge());
  const completionKey = user ? `careerai_daily_challenge_${user.id}_${challenge.dateKey}` : null;

  const [completion, setCompletion] = useState(() => readCompletion(completionKey));
  const [streak, setStreak] = useState(() => (user ? getStreak(user.id) : null));
  const [stage, setStage] = useState("intro");
  const [runId, setRunId] = useState(0);
  const [outcome, setOutcome] = useState(null);

  const dayNumber = (() => {
    if (!streak) return null;
    if (streak.lastActivityDate === isoDay()) return streak.current || 1;
    if (streak.lastActivityDate === isoDay(-1)) return streak.current + 1;
    return 1;
  })();

  // Backend persistence (signed-in users): session on start, result on finish.
  const [saveStatus, setSaveStatus] = useState(null);
  const remoteSession = useRef(null);

  const beginQuiz = () => {
    remoteSession.current = canSavePractice()
      ? startPracticeSession({
          module: "daily-challenge",
          topic: `Daily Challenge ${challenge.dateKey}`,
          difficulty: challenge.difficulty,
          questionType: "mixed"
        }).catch(() => null)
      : null;

    setSaveStatus(null);
    setStage("quiz");
  };

  const persistResult = (correct, total) => {
    const request = remoteSession.current;
    remoteSession.current = null;

    if (!request) {
      setSaveStatus(canSavePractice() ? null : "guest");
      return;
    }

    setSaveStatus("saving");

    request
      .then((session) => {
        if (!session) throw new Error("Session was not created");
        return savePracticeResult({ sessionId: session.id, score: correct, totalQuestions: total });
      })
      .then(() => setSaveStatus("saved"))
      .catch(() => setSaveStatus("failed"));
  };

  const handleFinish = ({ correct, total, history }) => {
    persistResult(correct, total);

    let xpStatus = "guest";
    let currentStreak = streak;

    if (user && !completion) {
      addXP(user.id, XP_REWARDS.DAILY_CHALLENGE, "DAILY_CHALLENGE", "Completed today's CareerAI daily challenge");
      currentStreak = recordDailyActivity(user.id);

      const saved = { score: correct, total };

      try {
        localStorage.setItem(completionKey, JSON.stringify(saved));
      } catch {
        // Storage unavailable: XP is still added, completion is not remembered.
      }

      setCompletion(saved);
      setStreak(currentStreak);
      xpStatus = "saved";
    } else if (user) {
      xpStatus = "already-earned";
    }

    setOutcome({
      result: {
        kicker: "TODAY'S CAREERAI CHALLENGE",
        title: "Daily Challenge Complete",
        score: correct,
        total,
        ...summarizeAreas(history)
      },
      xp: { amount: XP_REWARDS.DAILY_CHALLENGE, status: xpStatus },
      streak: currentStreak
    });
    setStage("done");
  };

  const restart = () => {
    setRunId((value) => value + 1);
    setStage("intro");
    setOutcome(null);
  };

  if (stage === "done" && outcome) {
    return (
      <PracticeResult
        result={outcome.result}
        xp={outcome.xp}
        retryLabel="Back to Challenge"
        onRetry={restart}
        extra={
          <>
            <div className="daily-streak-banner">
              <FiZap />
              {outcome.streak ? (
                <div>
                  <span>Current Streak</span>
                  <strong>
                    {outcome.streak.current} day{outcome.streak.current === 1 ? "" : "s"}
                  </strong>
                  <small>Longest streak: {outcome.streak.longest} days</small>
                </div>
              ) : (
                <div>
                  <span>Current Streak</span>
                  <strong>Sign in to track your streak</strong>
                </div>
              )}
            </div>
            <PracticeSaveStatus status={saveStatus} />
          </>
        }
      />
    );
  }

  if (stage === "quiz") {
    return (
      <div className="daily-quiz-shell">
        <QuizRunner
          key={runId}
          questions={challenge.questions}
          heading={`Today's Challenge • ${challenge.difficulty}`}
          showStats
          finishLabel="Complete Challenge"
          onFinish={handleFinish}
        />
      </div>
    );
  }

  return (
    <section className="daily-intro">

      <div className="daily-hero-card">

        <div className="daily-flame">
          <FiZap />
        </div>

        <span className="section-tag">
          TODAY'S CAREERAI CHALLENGE
        </span>

        <h2>
          {dayNumber ? `Day ${dayNumber}` : new Date().toLocaleDateString(undefined, { weekday: "long", day: "numeric", month: "long" })}
        </h2>

        <div className="daily-chips">
          <span>{challenge.difficulty}</span>
          <span className="xp">
            <FiZap />
            +{XP_REWARDS.DAILY_CHALLENGE} XP
          </span>
          <span>
            <FiClock />
            Estimated time: {challenge.estimatedMinutes} minutes
          </span>
        </div>

        {streak && (
          <p className="daily-streak-line">
            <FiTrendingUp />
            Current streak: {streak.current} day{streak.current === 1 ? "" : "s"} · Longest: {streak.longest}
          </p>
        )}

        {!user && (
          <p className="daily-streak-line">
            Sign in to earn XP and keep your daily streak.
          </p>
        )}

      </div>


      <div className="daily-mix">

        <h3>Today's mix</h3>

        <ul>
          {challenge.questions.map((question) => (
            <li key={question.id}>
              <span className="daily-mix-icon">
                {categoryIcons[question.category]}
              </span>
              <div>
                <strong>1 {question.category} question</strong>
                <small>{question.tag}</small>
              </div>
            </li>
          ))}
        </ul>

        {completion ? (
          <div className="daily-done">
            <p>
              <FiCheckCircle />
              Completed today · Score {completion.score} / {completion.total}. Come back tomorrow for a new challenge.
            </p>
            <button
              type="button"
              className="secondary-button"
              onClick={beginQuiz}
            >
              Practise again (no extra XP)
            </button>
          </div>
        ) : (
          <button
            type="button"
            className="primary-button daily-start"
            onClick={beginQuiz}
          >
            Start Today's Challenge
            <FiArrowRight />
          </button>
        )}

      </div>

    </section>
  );
}

export default DailyChallengeWorkspace;
