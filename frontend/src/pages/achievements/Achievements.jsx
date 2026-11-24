import { Link } from "react-router-dom";

import {
  FiAward,
  FiZap,
  FiTrendingUp,
  FiBookOpen,
  FiMic,
  FiFileText,
  FiDatabase,
  FiCode,
  FiCalendar,
  FiTarget,
  FiCompass,
  FiLock,
  FiCheckCircle,
  FiInfo,
  FiClock
} from "react-icons/fi";

import { useAuth } from "../../context/auth";
import {
  getXP,
  getXPHistory,
  getLevelInfo,
  getStreak,
  XP_REWARDS
} from "../../services/xpService";
import { getUserTaskHistory } from "../plan/planData";
import { getSelectedTrack, getTrackByTitle } from "../career/careerTrackData";

const INTERVIEW_READY_XP = 1400;

const PRACTICE_TASKS = ["core-topic", "priority-skill", "sql", "aptitude", "coding", "daily-challenge"];
const PRACTICE_ACTIVITIES = ["TECHNICAL_SET", "APTITUDE_SET", "CODING_PROBLEM", "SQL_PROBLEM", "MOCK_ASSESSMENT", "DAILY_CHALLENGE"];

function hasDailyChallengeCompletion(userId) {
  if (!userId) return false;

  try {
    const prefix = `careerai_daily_challenge_${userId}_`;

    for (let index = 0; index < localStorage.length; index++) {
      if (localStorage.key(index)?.startsWith(prefix)) return true;
    }
  } catch {
    // Storage unavailable.
  }

  return false;
}

/**
 * Achievements are evaluated from data already stored on this device.
 * TODO backend: GET /api/achievements will return unlocked achievements
 * and credit their XP server-side.
 */
function evaluateAchievements(user) {
  const userId = user?.id;
  const xp = getXP(userId);
  const streak = getStreak(userId);
  const history = getXPHistory(userId);
  const activities = new Set(history.map((item) => item.activity));

  const completedTasks = new Set(
    Object.values(getUserTaskHistory(userId)).flatMap((day) =>
      Object.entries(day)
        .filter(([, status]) => status === "completed")
        .map(([taskId]) => taskId)
    )
  );

  const dailyDone = hasDailyChallengeCompletion(userId) || activities.has("DAILY_CHALLENGE");
  const anyActivity = (list) => list.some((item) => activities.has(item));

  return [
    {
      id: "first-practice",
      icon: <FiBookOpen />,
      title: "First Practice",
      description: "Complete your first practice task or session.",
      rule: "Complete any practice task in My Plan or the Daily Challenge.",
      reward: XP_REWARDS.TECHNICAL_SET,
      unlocked: PRACTICE_TASKS.some((id) => completedTasks.has(id)) || anyActivity(PRACTICE_ACTIVITIES) || dailyDone,
      to: "/my-plan"
    },
    {
      id: "first-interview",
      icon: <FiMic />,
      title: "First Interview",
      description: "Finish your first AI mock interview.",
      rule: "Mark the AI Interview task complete in My Plan.",
      reward: XP_REWARDS.AI_INTERVIEW,
      unlocked: completedTasks.has("interview") || activities.has("AI_INTERVIEW"),
      to: "/ai-interview"
    },
    {
      id: "resume-ready",
      icon: <FiFileText />,
      title: "Resume Ready",
      description: "Improve your resume for your target role.",
      rule: "Mark the Resume Improvement task complete in My Plan.",
      reward: XP_REWARDS.RESUME_COMPLETE,
      unlocked: completedTasks.has("resume") || activities.has("RESUME_COMPLETE"),
      to: "/resume-tools"
    },
    {
      id: "seven-day-streak",
      icon: <FiZap />,
      title: "7 Day Streak",
      description: "Prepare on seven consecutive days.",
      rule: "Reach a 7-day streak.",
      reward: 50,
      unlocked: streak.longest >= 7,
      progress: { value: Math.min(streak.current, 7), max: 7, label: `${Math.min(streak.current, 7)} / 7 days` },
      to: "/my-plan"
    },
    {
      id: "sql-starter",
      icon: <FiDatabase />,
      title: "SQL Starter",
      description: "Complete your first SQL practice.",
      rule: "Mark the SQL Practice task complete in My Plan.",
      reward: XP_REWARDS.SQL_PROBLEM,
      unlocked: completedTasks.has("sql") || activities.has("SQL_PROBLEM"),
      to: "/practice/sql"
    },
    {
      id: "coding-starter",
      icon: <FiCode />,
      title: "Coding Starter",
      description: "Complete your first coding problem.",
      rule: "Mark the Coding Practice task complete in My Plan.",
      reward: XP_REWARDS.CODING_PROBLEM,
      unlocked: completedTasks.has("coding") || activities.has("CODING_PROBLEM"),
      to: "/practice/coding"
    },
    {
      id: "daily-challenger",
      icon: <FiCalendar />,
      title: "Daily Challenger",
      description: "Complete a Daily Challenge.",
      rule: "Finish today's Daily Challenge.",
      reward: XP_REWARDS.DAILY_CHALLENGE,
      unlocked: dailyDone,
      to: "/practice/daily-challenge"
    },
    {
      id: "interview-ready",
      icon: <FiTarget />,
      title: "Interview Ready",
      description: "Reach the Interview Ready level.",
      rule: `Earn ${INTERVIEW_READY_XP} XP.`,
      reward: 100,
      unlocked: xp >= INTERVIEW_READY_XP,
      progress: { value: Math.min(xp, INTERVIEW_READY_XP), max: INTERVIEW_READY_XP, label: `${Math.min(xp, INTERVIEW_READY_XP)} / ${INTERVIEW_READY_XP} XP` },
      to: "/practice/daily-challenge"
    },
    {
      id: "career-explorer",
      icon: <FiCompass />,
      title: "Career Explorer",
      description: "Choose a target career track.",
      rule: "Select a track on Career Tracks or set a target role in your profile.",
      reward: 30,
      unlocked: Boolean(getSelectedTrack() || getTrackByTitle(user?.profile?.targetRole)),
      to: "/career-tracks"
    }
  ];
}

const activityLabels = {
  DAILY_CHALLENGE: "Daily Challenge",
  PROFILE_COMPLETE: "Profile completed",
  DAILY_LOGIN: "Daily login"
};


function Achievements() {
  const { user } = useAuth();

  const xp = getXP(user?.id);
  const level = getLevelInfo(xp);
  const streak = getStreak(user?.id);
  const history = getXPHistory(user?.id).slice(0, 6);
  const achievements = evaluateAchievements(user);
  const unlockedCount = achievements.filter((item) => item.unlocked).length;

  return (
    <div className="ua-page">

      <header className="ua-page-header">
        <div>
          <span className="ua-eyebrow">ACHIEVEMENTS</span>
          <h1>Level, XP and achievements</h1>
          <p>Track your CareerAI level and unlock achievements as you prepare.</p>
        </div>
        <span className="ua-chip large">
          <FiAward aria-hidden="true" />
          {unlockedCount} / {achievements.length} unlocked
        </span>
      </header>


      {/* LEVEL */}

      <section className="ach-level">

        <div className="ach-level-badge">
          <span>LEVEL</span>
          <strong>{level.level}</strong>
        </div>

        <div className="ach-level-main">
          <span className="ua-card-kicker light">CURRENT LEVEL</span>
          <h2>{level.name}</h2>
          <div
            className="ach-level-bar"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={level.progress}
            aria-label="Progress to next level"
          >
            <span style={{ width: `${level.progress}%` }}></span>
          </div>
          <p>
            {level.nextLevel
              ? `${level.remainingXP} XP to Level ${level.nextLevel} · ${level.nextName}`
              : "Maximum level reached"}
          </p>
        </div>

      </section>


      <section className="ua-stat-grid five">

        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiAward /></span>
          <span className="ua-stat-label">Current Level</span>
          <strong className="ua-stat-value">{level.level}</strong>
          <small>{level.name}</small>
        </div>

        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiTrendingUp /></span>
          <span className="ua-stat-label">Current XP</span>
          <strong className="ua-stat-value">{xp}</strong>
          <small>Saved on this device</small>
        </div>

        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiTarget /></span>
          <span className="ua-stat-label">XP to Next Level</span>
          <strong className="ua-stat-value">{level.nextLevel ? level.remainingXP : "—"}</strong>
          <small>{level.nextLevel ? `Level ${level.nextLevel} at ${level.nextXP} XP` : "Top level"}</small>
        </div>

        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiZap /></span>
          <span className="ua-stat-label">Current Streak</span>
          <strong className="ua-stat-value">{streak.current}</strong>
          <small>{streak.current === 1 ? "day" : "days"}</small>
        </div>

        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiCalendar /></span>
          <span className="ua-stat-label">Longest Streak</span>
          <strong className="ua-stat-value">{streak.longest}</strong>
          <small>{streak.longest === 1 ? "day" : "days"}</small>
        </div>

      </section>


      {/* ACHIEVEMENTS */}

      <section className="ua-card">

        <div className="ua-card-header">
          <div>
            <span className="ua-card-kicker">ACHIEVEMENTS</span>
            <h2>Badges</h2>
          </div>
        </div>

        <ul className="ach-grid">
          {achievements.map((item) => (
            <li
              key={item.id}
              className={`ach-card ${item.unlocked ? "unlocked" : "locked"}`}
            >

              <div className="ach-card-top">
                <span className="ach-icon" aria-hidden="true">{item.icon}</span>
                <span className={`ach-state ${item.unlocked ? "unlocked" : "locked"}`}>
                  {item.unlocked ? <FiCheckCircle aria-hidden="true" /> : <FiLock aria-hidden="true" />}
                  {item.unlocked ? "Unlocked" : "Locked"}
                </span>
              </div>

              <h3>{item.title}</h3>
              <p>{item.description}</p>

              {!item.unlocked && item.progress && (
                <div className="ach-progress">
                  <span className="ua-progress thin">
                    <span style={{ width: `${(item.progress.value / item.progress.max) * 100}%` }}></span>
                  </span>
                  <small>{item.progress.label}</small>
                </div>
              )}

              <div className="ach-card-footer">
                <span className="ach-reward">+{item.reward} XP</span>
                {!item.unlocked && (
                  <Link to={item.to} className="ua-card-link">
                    How to unlock
                  </Link>
                )}
              </div>

              {!item.unlocked && <small className="ach-rule">{item.rule}</small>}

            </li>
          ))}
        </ul>

        <p className="ua-footnote">
          <FiInfo aria-hidden="true" />
          Achievements are checked from progress saved on this device. Achievement XP
          rewards will be credited once account progress is connected to the backend.
        </p>

      </section>


      {/* XP HISTORY */}

      <section className="ua-card">

        <div className="ua-card-header">
          <div>
            <span className="ua-card-kicker">XP HISTORY</span>
            <h2>Recent XP</h2>
          </div>
        </div>

        {history.length ? (
          <ul className="ach-history">
            {history.map((item) => (
              <li key={item.id}>
                <span className="ach-history-amount">+{item.amount}</span>
                <div>
                  <strong>{item.description || activityLabels[item.activity] || item.activity}</strong>
                  <small>
                    <FiClock aria-hidden="true" />
                    {new Date(item.createdAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}
                  </small>
                </div>
              </li>
            ))}
          </ul>
        ) : (
          <div className="ua-empty">
            <p>No XP earned yet. The Daily Challenge awards +{XP_REWARDS.DAILY_CHALLENGE} XP once per day.</p>
            <Link to="/practice/daily-challenge" className="ua-button primary">
              Start Daily Challenge
            </Link>
          </div>
        )}

      </section>

    </div>
  );
}

export default Achievements;
