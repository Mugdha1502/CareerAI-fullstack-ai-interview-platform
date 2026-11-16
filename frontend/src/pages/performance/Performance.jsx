import { useEffect, useState } from "react";
import { Link } from "react-router-dom";

import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip
} from "recharts";

import {
  FiTarget,
  FiBookOpen,
  FiGrid,
  FiCode,
  FiDatabase,
  FiMic,
  FiLayers,
  FiInfo,
  FiArrowRight,
  FiCheckCircle,
  FiAlertCircle,
  FiActivity,
  FiZap,
  FiLoader,
  FiRefreshCw,
  FiBarChart2
} from "react-icons/fi";

import { toApiError, getToken } from "../../services/api";
import {
  getPerformanceSummary,
  getPerformanceHistory,
  getSkillPerformance,
  getRecentActivity
} from "../../services/performanceService";
import { getInterviewStats } from "../../services/interviewService";

/* =====================================================
   PERFORMANCE — all numbers come from GET /api/performance/*,
   calculated by the backend from saved practice results.
===================================================== */

const HISTORY_DAYS = 30;

const CATEGORIES = [
  { key: "technical", field: "technical", label: "Technical", icon: <FiBookOpen />, to: "/practice/technical" },
  { key: "aptitude", field: "aptitude", label: "Aptitude", icon: <FiGrid />, to: "/practice/aptitude" },
  { key: "coding", field: "coding", label: "Coding", icon: <FiCode />, to: "/practice/coding" },
  { key: "sql", field: "sql", label: "SQL", icon: <FiDatabase />, to: "/practice/sql" },
  { key: "system-design", field: "systemDesign", label: "System Design", icon: <FiLayers />, to: "/practice/system-design" },
  { key: "voice", field: "voice", label: "Voice", icon: <FiMic />, to: "/practice/voice" }
];

const MODULE_LABELS = {
  technical: "Technical",
  aptitude: "Aptitude",
  coding: "Coding",
  sql: "SQL",
  "system-design": "System Design",
  voice: "Voice",
  "daily-challenge": "Daily Challenge",
  "mock-assessment": "Mock Assessment"
};

// Matches the backend thresholds (SkillStatus): 80 / 65 / 50.
const scoreTone = (value) => (value >= 80 ? "strong" : value >= 65 ? "good" : value >= 50 ? "improve" : "priority");
const barColor = (value) => (value >= 80 ? "#12b76a" : value >= 65 ? "#6c63ff" : value >= 50 ? "#f79009" : "#f04438");

function practiceRoute(module, topic) {
  if (module === "technical" && topic) return `/practice/technical?topic=${encodeURIComponent(topic)}`;
  return `/practice/${module}`;
}

function relativeDay(value) {
  const date = new Date(value);
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);

  const days = Math.round((startOfToday - new Date(date.getFullYear(), date.getMonth(), date.getDate())) / 86400000);

  if (days <= 0) return "Today";
  if (days === 1) return "Yesterday";
  if (days < 7) return `${days} days ago`;
  return date.toLocaleDateString(undefined, { day: "numeric", month: "short" });
}

const shortDate = (isoDate) =>
  new Date(`${isoDate}T00:00:00`).toLocaleDateString(undefined, { day: "numeric", month: "short" });

/** Last 7 calendar days with session counts (days without practice are 0). */
function lastSevenDays(history) {
  const byDate = Object.fromEntries(history.map((point) => [point.date, point.sessions]));

  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date();
    day.setDate(day.getDate() - (6 - index));
    const key = [day.getFullYear(), String(day.getMonth() + 1).padStart(2, "0"), String(day.getDate()).padStart(2, "0")].join("-");

    return {
      key,
      label: day.toLocaleDateString(undefined, { weekday: "short" }),
      sessions: byDate[key] || 0
    };
  });
}


function Performance() {
  const signedIn = Boolean(getToken());

  const [state, setState] = useState({ loading: signedIn, error: "", data: null });
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!signedIn) return undefined;

    let cancelled = false;

    Promise.all([
      getPerformanceSummary(),
      getPerformanceHistory(HISTORY_DAYS),
      getSkillPerformance(),
      getRecentActivity(),
      // Interview results are a separate data source; the page still works if it fails.
      getInterviewStats().catch(() => null)
    ])
      .then(([summary, history, skills, recent, interviews]) => {
        if (!cancelled) setState({ loading: false, error: "", data: { summary, history, skills, recent, interviews } });
      })
      .catch((error) => {
        if (cancelled) return;
        const { status, message } = toApiError(error);
        setState({ loading: false, error: status === 0 ? "Unable to connect to CareerAI server." : message, data: null });
      });

    return () => {
      cancelled = true;
    };
  }, [signedIn, reloadKey]);

  const retry = () => {
    setState({ loading: true, error: "", data: null });
    setReloadKey((key) => key + 1);
  };

  return (
    <div className="ua-page">

      <header className="ua-page-header">

        <div>
          <span className="ua-eyebrow">PERFORMANCE</span>
          <h1>Performance dashboard</h1>
          <p>Readiness, category scores and recent activity from your saved practice.</p>
        </div>

        <div className="ua-header-actions">
          <Link to="/practice" className="ua-button primary">
            Practice Weak Skills
            <FiArrowRight aria-hidden="true" />
          </Link>
          <Link to="/career/skill-gap" className="ua-button ghost">
            View Skill Gap
          </Link>
          <Link to="/ai-interview" className="ua-button ghost">
            <FiMic aria-hidden="true" />
            Start AI Interview
          </Link>
        </div>

      </header>

      {!signedIn ? (
        <StatusCard
          icon={<FiInfo />}
          message="Sign in to build your performance profile. Practice you complete while signed in is saved and analysed here."
          action={<Link to="/login" className="ua-button primary">Log in</Link>}
        />
      ) : state.loading ? (
        <StatusCard icon={<FiLoader className="auth-spinner" />} message="Loading your performance…" role="status" />
      ) : state.error ? (
        <StatusCard
          icon={<FiAlertCircle />}
          message={state.error}
          role="alert"
          action={(
            <button type="button" className="ua-button primary" onClick={retry}>
              <FiRefreshCw aria-hidden="true" />
              Try again
            </button>
          )}
        />
      ) : !state.data.summary.hasData ? (
        <StatusCard
          icon={<FiBarChart2 />}
          message="Complete some practice sessions to start building your performance profile."
          action={<Link to="/practice" className="ua-button primary">Start practising</Link>}
        />
      ) : (
        <PerformanceContent data={state.data} />
      )}

    </div>
  );
}


function StatusCard({ icon, message, action, role }) {
  return (
    <section className="ua-card">
      <div className="ua-empty" role={role}>
        <span aria-hidden="true">{icon}</span>
        <p>{message}</p>
        {action}
      </div>
    </section>
  );
}


function PerformanceContent({ data }) {
  const { summary, history, skills, recent, interviews } = data;

  const categories = CATEGORIES.map((category) => ({ ...category, value: summary[category.field] }));
  const attempted = categories.filter((category) => category.value != null);
  const notAttempted = categories.filter((category) => category.value == null);

  const week = lastSevenDays(history);
  const weekSessions = week.reduce((sum, day) => sum + day.sessions, 0);
  const maxSessions = Math.max(...week.map((day) => day.sessions), 1);

  const trend = history.map((point) => ({ ...point, label: shortDate(point.date) }));
  const trendChange = trend.length > 1 ? trend[trend.length - 1].averageScore - trend[0].averageScore : null;

  const strong = skills.filter((skill) => skill.status === "STRONG" || skill.status === "GOOD").slice(0, 4);
  const weak = skills
    .filter((skill) => skill.status === "IMPROVE" || skill.status === "PRIORITY")
    .sort((a, b) => a.score - b.score)
    .slice(0, 4);

  const nextActions = [
    weak[0] && {
      title: `Practise ${weak[0].skill}`,
      text: `Your lowest-scoring topic at ${weak[0].score}%.`,
      to: practiceRoute(weak[0].module, weak[0].skill)
    },
    notAttempted[0] && {
      title: `Try ${notAttempted[0].label}`,
      text: "Not attempted yet — one session gives you a first score.",
      to: notAttempted[0].to
    },
    {
      title: "Take a mock assessment",
      text: "A timed test checks readiness under pressure.",
      to: "/practice/mock-assessment"
    }
  ].filter(Boolean);

  return (
    <>

      {/* SCORES */}

      <section className="perf-score-grid">

        <div
          className="perf-overall"
          style={{ "--score": `${(summary.overallReadiness ?? 0) * 3.6}deg` }}
        >
          <div className="perf-ring">
            <strong>{summary.overallReadiness != null ? `${summary.overallReadiness}%` : "—"}</strong>
            <span>Overall Readiness</span>
          </div>
          <p>
            <FiTarget aria-hidden="true" />
            Based on {summary.categoriesAttempted} of {CATEGORIES.length} categories
          </p>
        </div>

        <div className="perf-category-grid">
          {categories.map((item) => (
            <Link
              key={item.key}
              to={item.to}
              className={`perf-category ${item.value != null ? scoreTone(item.value) : ""}`}
            >
              <span className="perf-category-icon" aria-hidden="true">{item.icon}</span>
              <span className="perf-category-label">{item.label}</span>
              {item.value != null ? (
                <>
                  <strong>{item.value}%</strong>
                  <span className="ua-progress thin">
                    <span style={{ width: `${item.value}%` }}></span>
                  </span>
                </>
              ) : (
                <>
                  <strong className="perf-not-attempted">Not attempted</strong>
                  <small>Start practising →</small>
                </>
              )}
            </Link>
          ))}
        </div>

      </section>


      {/* TOTALS */}

      <section className="ua-stat-grid">
        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiActivity /></span>
          <span className="ua-stat-label">Sessions completed</span>
          <strong className="ua-stat-value">{summary.totalSessions}</strong>
        </div>
        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiBookOpen /></span>
          <span className="ua-stat-label">Questions answered</span>
          <strong className="ua-stat-value">{summary.totalQuestions}</strong>
        </div>
        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiTarget /></span>
          <span className="ua-stat-label">Average accuracy</span>
          <strong className="ua-stat-value">{summary.averageAccuracy != null ? `${summary.averageAccuracy}%` : "—"}</strong>
        </div>
        <div className="ua-stat">
          <span className="ua-stat-icon" aria-hidden="true"><FiZap /></span>
          <span className="ua-stat-label">Practice streak</span>
          <strong className="ua-stat-value">{summary.currentStreak} {summary.currentStreak === 1 ? "day" : "days"}</strong>
        </div>
        {interviews && (
          <Link to="/ai-interview/setup" className="ua-stat">
            <span className="ua-stat-icon" aria-hidden="true"><FiMic /></span>
            <span className="ua-stat-label">Mock interviews · average score</span>
            <strong className="ua-stat-value">
              {interviews.completedCount}
              {interviews.averageScore != null ? ` · ${interviews.averageScore}%` : ""}
            </strong>
          </Link>
        )}
      </section>


      {/* CHARTS */}

      <div className="perf-chart-grid">

        <section className="ua-card">
          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">READINESS TREND</span>
              <h2>Last {HISTORY_DAYS} days</h2>
            </div>
            {trendChange != null && (
              <span className={`ua-chip${trendChange >= 0 ? " success" : ""}`}>
                {trendChange >= 0 ? "+" : ""}{trendChange} pts
              </span>
            )}
          </div>

          {trend.length ? (
            <div
              className="perf-chart"
              role="img"
              aria-label={`Average daily score over the last ${HISTORY_DAYS} days: ${trend.map((point) => `${point.label} ${point.averageScore}%`).join(", ")}.`}
            >
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={trend} margin={{ top: 10, right: 12, bottom: 0, left: -18 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#ececf4" vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: "#70758c" }} />
                  <YAxis domain={[0, 100]} tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: "#70758c" }} />
                  <Tooltip formatter={(value, name, entry) => [`${value}% (${entry.payload.sessions} sessions)`, "Average score"]} />
                  <Line
                    type="monotone"
                    dataKey="averageScore"
                    stroke="#6c63ff"
                    strokeWidth={3}
                    dot={{ r: 4, fill: "#6c63ff" }}
                    activeDot={{ r: 6 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <p className="ua-muted">No scored practice in the last {HISTORY_DAYS} days.</p>
          )}
        </section>

        <section className="ua-card">
          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">CATEGORY PERFORMANCE</span>
              <h2>Attempted categories</h2>
            </div>
          </div>

          {attempted.length ? (
            <div
              className="perf-chart"
              role="img"
              aria-label={`Category scores: ${attempted.map((item) => `${item.label} ${item.value}%`).join(", ")}.`}
            >
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={attempted} margin={{ top: 10, right: 8, bottom: 0, left: -18 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#ececf4" vertical={false} />
                  <XAxis dataKey="label" tickLine={false} axisLine={false} interval={0} tick={{ fontSize: 11, fill: "#70758c" }} />
                  <YAxis domain={[0, 100]} tickLine={false} axisLine={false} tick={{ fontSize: 12, fill: "#70758c" }} />
                  <Tooltip formatter={(value) => [`${value}%`, "Score"]} cursor={{ fill: "rgba(108, 99, 255, 0.06)" }} />
                  <Bar dataKey="value" radius={[8, 8, 0, 0]} maxBarSize={44}>
                    {attempted.map((item) => (
                      <Cell key={item.key} fill={barColor(item.value)} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <p className="ua-muted">Category scores appear after practice in Technical, Aptitude, Coding, SQL, System Design or Voice.</p>
          )}
        </section>

      </div>


      {/* WEEKLY ACTIVITY */}

      <section className="ua-card">

        <div className="ua-card-header">
          <div>
            <span className="ua-card-kicker">WEEKLY ACTIVITY</span>
            <h2>Last 7 days</h2>
          </div>
          <span className="ua-chip">{weekSessions} scored {weekSessions === 1 ? "session" : "sessions"}</span>
        </div>

        <ol className="perf-week">
          {week.map((day) => (
            <li key={day.key}>
              <span className="perf-week-bar" aria-hidden="true">
                <span style={{ height: `${Math.max((day.sessions / maxSessions) * 100, 4)}%` }}></span>
              </span>
              <strong>{day.label}</strong>
              <small>{day.sessions ? `${day.sessions} ${day.sessions === 1 ? "session" : "sessions"}` : "Rest"}</small>
            </li>
          ))}
        </ol>

      </section>


      <div className="perf-two-col">

        {/* RECENT PRACTICE */}

        <section className="ua-card">

          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">RECENT PRACTICE</span>
              <h2>Last sessions</h2>
            </div>
            <Link to="/practice" className="ua-card-link">All practice</Link>
          </div>

          <ul className="perf-list">
            {recent.slice(0, 6).map((item) => {
              const title = item.topic || MODULE_LABELS[item.module] || item.module;

              return (
                <li key={item.resultId}>
                  <span className={`perf-score-pill ${scoreTone(item.accuracy)}`}>{item.accuracy}%</span>
                  <div>
                    <strong>{title}</strong>
                    <small>
                      {MODULE_LABELS[item.module] || item.module}
                      {item.questionType === "notes" ? " · notes" : ""}
                      {" · "}{item.correctAnswers} / {item.totalQuestions}
                      {" · "}{relativeDay(item.completedAt || item.startedAt)}
                    </small>
                  </div>
                  <Link
                    to={practiceRoute(item.module, item.topic)}
                    className="ua-button small ghost"
                    aria-label={`Practise ${title} again`}
                  >
                    Again
                  </Link>
                </li>
              );
            })}
          </ul>

        </section>


        {/* RECENT INTERVIEWS — no interview results are stored yet */}

        <section className="ua-card">

          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">RECENT INTERVIEWS</span>
              <h2>Mock interviews</h2>
            </div>
            <Link to="/ai-interview/setup?type=role" className="ua-card-link">New interview</Link>
          </div>

          <div className="ua-empty">
            <FiMic aria-hidden="true" />
            <p>Interview scores will appear here once AI interview results are saved to your account.</p>
          </div>

        </section>

      </div>


      <div className="perf-three-col">

        <section className="ua-card">
          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">STRONG SKILLS</span>
              <h2>Keep these sharp</h2>
            </div>
          </div>
          {strong.length ? (
            <ul className="perf-skill-list">
              {strong.map((skill) => (
                <li key={`${skill.module}-${skill.skill}`} className="strong">
                  <FiCheckCircle aria-hidden="true" />
                  <span>{skill.skill}</span>
                  <strong>{skill.score}%</strong>
                </li>
              ))}
            </ul>
          ) : (
            <p className="ua-muted">No topic has reached 65% yet.</p>
          )}
        </section>

        <section className="ua-card">
          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">NEEDS IMPROVEMENT</span>
              <h2>Focus areas</h2>
            </div>
          </div>
          {weak.length ? (
            <ul className="perf-skill-list">
              {weak.map((skill) => (
                <li key={`${skill.module}-${skill.skill}`} className="weak">
                  <FiAlertCircle aria-hidden="true" />
                  <span>{skill.skill}</span>
                  <strong>{skill.score}%</strong>
                </li>
              ))}
            </ul>
          ) : (
            <p className="ua-muted">No practised topic is below 65%.</p>
          )}
        </section>

        <section className="ua-card">
          <div className="ua-card-header">
            <div>
              <span className="ua-card-kicker">RECOMMENDED NEXT ACTIONS</span>
              <h2>What to do next</h2>
            </div>
          </div>
          <ul className="perf-actions">
            {nextActions.map((action) => (
              <li key={action.title}>
                <Link to={action.to}>
                  <FiActivity aria-hidden="true" />
                  <span>
                    <strong>{action.title}</strong>
                    <small>{action.text}</small>
                  </span>
                  <FiArrowRight aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
        </section>

      </div>

    </>
  );
}

export default Performance;
